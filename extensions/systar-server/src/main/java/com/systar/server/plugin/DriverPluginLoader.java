package com.systar.server.plugin;

import com.systar.monitor.asset.AssetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Discovers driver plugins under the configured drivers search path
 * ({@code systar.drivers.path}, resolved against the process working directory).
 * <p>
 * A plugin is one subdirectory whose name is its identity. It may contain
 * {@code driver.jar} (classes + type XMLs under
 * {@value #TYPE_XML_PREFIX}), {@code lib/*.jar}, loose type XMLs at the plugin
 * directory root or under its own package tree, and {@code seed/*.csv}. Directories are
 * processed in name order; the first plugin owning a class wins (logged).
 * <p>
 * Each plugin gets a parent-first {@link URLClassLoader} whose parent is the
 * classloader of the server itself (the LaunchedClassLoader in a fat jar), so
 * framework and shared libraries resolve from the server, plugin classes from
 * the plugin. XML attribution is restricted to the plugin's own URLs — a
 * plugin never "discovers" another plugin's or the server's descriptors.
 * <p>
 * Error semantics (spec §7): a directory with nothing loadable is WARNed and
 * skipped; a corrupt {@code driver.jar} aborts startup naming the plugin; a
 * missing drivers root logs INFO (absolute path) plus a WARN; {@code *.xml}
 * files outside the accepted locations (directory root, package tree, jar) are
 * WARNed as ignored.
 */
@Component
public class DriverPluginLoader implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DriverPluginLoader.class);

    /** Package-tree prefix for type-definition XMLs inside jars and on disk. */
    static final String TYPE_XML_PREFIX = "com/systar/monitor/drivers/";

    private final String driversPath;

    private List<DriverPlugin> plugins = List.of();
    private Map<String, DriverPlugin> classIndex = Map.of();

    public DriverPluginLoader(@Value("${systar.drivers.path:./drivers}") String driversPath) {
        this.driversPath = driversPath;
    }

    @Override
    public void afterPropertiesSet() {
        discover();
    }

    /**
     * Closes every plugin classloader, releasing the jar file handles it
     * opened (on Windows an open jar stays locked until the loader closes).
     */
    @Override
    public void destroy() {
        for (DriverPlugin plugin : plugins) {
            try {
                plugin.classLoader().close();
            } catch (IOException e) {
                log.warn("Plugin '{}': error closing classloader ({}).", plugin.name(), e.getMessage());
            }
        }
    }

    public List<DriverPlugin> plugins() {
        return plugins;
    }

    public List<PluginTypeResource> typeResources() {
        return plugins.stream().flatMap(p -> p.typeResources().stream()).toList();
    }

    /** The plugin whose jars provide {@code fqcn}, or empty (server classpath). */
    public Optional<DriverPlugin> pluginOwning(String fqcn) {
        return Optional.ofNullable(classIndex.get(fqcn));
    }

    void discover() {
        Path root = Path.of(driversPath).toAbsolutePath();
        if (!Files.isDirectory(root)) {
            log.info("Driver plugin directory resolved to '{}'.", root);
            log.warn("Driver plugin directory '{}' does not exist; no driver plugins loaded.", root);
            plugins = List.of();
            classIndex = Map.of();
            return;
        }
        List<Path> dirs;
        try (Stream<Path> s = Files.list(root)) {
            dirs = s.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list driver directory " + root, e);
        }

        List<DriverPlugin> found = new ArrayList<>();
        Map<String, DriverPlugin> index = new LinkedHashMap<>();
        for (Path dir : dirs) {
            DriverPlugin plugin = loadPluginDir(dir);
            if (plugin == null) continue;
            found.add(plugin);
            for (String fqcn : plugin.ownedClasses()) {
                DriverPlugin prev = index.putIfAbsent(fqcn, plugin);
                if (prev != null) {
                    log.info("Class '{}' is provided by plugins '{}' and '{}'; "
                                    + "directory order makes '{}' win.",
                            fqcn, prev.name(), plugin.name(), prev.name());
                }
            }
        }
        plugins = List.copyOf(found);
        classIndex = Map.copyOf(index);
        log.info("Driver plugins: {} plugin(s) loaded from '{}'.", found.size(), root);
    }

    /** Returns null when the directory holds nothing loadable (WARN, skipped). */
    private DriverPlugin loadPluginDir(Path dir) {
        String name = dir.getFileName().toString();
        List<URL> urls = new ArrayList<>();
        Path driverJar = dir.resolve("driver.jar");
        if (Files.isRegularFile(driverJar)) addJarUrl(urls, driverJar, name);
        Path libDir = dir.resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (Stream<Path> s = Files.list(libDir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .sorted().forEach(p -> addJarUrl(urls, p, name));
            } catch (IOException e) {
                log.warn("Plugin '{}': cannot list lib/ ({}); skipping the lib dir.", name, e.getMessage());
            }
        }

        List<PluginTypeResource> xmls = new ArrayList<>();
        Set<String> classes = new LinkedHashSet<>();
        for (URL url : urls) collectJar(url, name, xmls, classes);
        collectLooseXmls(dir, name, xmls);
        warnStrayXmls(dir, name);

        List<Path> seeds = listSeedFiles(dir, name);

        if (urls.isEmpty() && xmls.isEmpty() && seeds.isEmpty()) {
            log.warn("Plugin '{}': directory has no driver.jar, lib/*.jar, type XML or seed; skipped.", name);
            return null;
        }

        URLClassLoader loader = new URLClassLoader(
                urls.toArray(URL[]::new), getClass().getClassLoader());
        log.info("Plugin '{}': {} type XML(s), {} class(es), {} seed file(s).",
                name, xmls.size(), classes.size(), seeds.size());
        return new DriverPlugin(name, dir, loader, List.copyOf(xmls), seeds, Set.copyOf(classes));
    }

    private void addJarUrl(List<URL> urls, Path jar, String pluginName) {
        try {
            urls.add(jar.toUri().toURL());
        } catch (Exception e) {
            throw new AssetException(e, "Plugin '%s': cannot build URL for jar %s: %s",
                    pluginName, jar, e.getMessage());
        }
    }

    private void collectJar(URL url, String pluginName,
                            List<PluginTypeResource> xmls, Set<String> classes) {
        Path jarPath;
        try {
            jarPath = Paths.get(url.toURI());
        } catch (Exception e) {
            throw new AssetException(e, "Plugin '%s': unreadable jar %s: %s",
                    pluginName, url, e.getMessage());
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory()) continue;
                if (entryName.startsWith(TYPE_XML_PREFIX) && entryName.endsWith(".xml")) {
                    String location = "jar:" + jarPath.toUri() + "!/" + entryName;
                    xmls.add(new PluginTypeResource(pluginName,
                            new UrlResource("jar:" + jarPath.toUri() + "!/" + entryName), location));
                } else if (entryName.endsWith(".class") && !entryName.startsWith("META-INF/")) {
                    classes.add(entryName.substring(0, entryName.length() - 6).replace('/', '.'));
                }
            }
        } catch (IOException e) {
            // spec §7: a corrupt driver.jar is a deploy error — fail startup naming the plugin dir
            throw new AssetException(e, "Plugin '%s': driver jar %s is corrupt or unreadable (%s); "
                    + "startup failed.", pluginName, jarPath, e.getMessage());
        }
    }

    /**
     * spec §7 finalized placement: loose type XMLs are collected from the plugin directory
     * root (the XML-only plugin convention) and from the package tree mirroring the jar
     * layout ({@code com/systar/monitor/drivers/**}).
     */
    private void collectLooseXmls(Path dir, String pluginName, List<PluginTypeResource> xmls) {
        Path pkgRoot = dir.resolve("com/systar/monitor/drivers");
        try {
            if (Files.isDirectory(pkgRoot)) {
                try (Stream<Path> s = Files.walk(pkgRoot)) {
                    s.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".xml"))
                            .sorted()
                            .forEach(p -> addLooseXml(pluginName, p, xmls));
                }
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".xml"))
                        .sorted()
                        .forEach(p -> addLooseXml(pluginName, p, xmls));
            }
        } catch (IOException e) {
            log.warn("Plugin '{}': cannot walk loose XML tree ({}).", pluginName, e.getMessage());
        }
    }

    private void addLooseXml(String pluginName, Path p, List<PluginTypeResource> xmls) {
        try {
            String loc = p.toUri().toURL().toExternalForm();
            xmls.add(new PluginTypeResource(pluginName, new UrlResource(loc), loc));
        } catch (Exception e) {
            log.warn("Plugin '{}': unreadable loose XML {}: {}", pluginName, p, e.getMessage());
        }
    }

    /** spec §7: *.xml in other plugin subdirs (e.g. lib/) is a deploy mistake — WARN, ignored. */
    private void warnStrayXmls(Path dir, String pluginName) {
        Path pkgRoot = dir.resolve("com/systar/monitor/drivers");
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .filter(p -> !p.startsWith(pkgRoot) && !dir.equals(p.getParent()))
                    .forEach(p -> log.warn("Plugin '{}': XML file '{}' is not in an accepted type XML "
                                    + "location and is ignored (type XMLs must live at the plugin "
                                    + "directory root, under com/systar/monitor/drivers/**, "
                                    + "or inside driver.jar).",
                            pluginName, p));
        } catch (IOException e) {
            log.warn("Plugin '{}': cannot scan for stray XML files ({}).", pluginName, e.getMessage());
        }
    }

    private List<Path> listSeedFiles(Path dir, String pluginName) {
        Path seedDir = dir.resolve("seed");
        if (!Files.isDirectory(seedDir)) return List.of();
        try (Stream<Path> s = Files.list(seedDir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Plugin '{}': cannot list seed/ ({}).", pluginName, e.getMessage());
            return List.of();
        }
    }
}
