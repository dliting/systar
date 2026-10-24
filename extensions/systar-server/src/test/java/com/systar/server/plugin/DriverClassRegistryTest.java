package com.systar.server.plugin;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.*;
import java.util.jar.*;

import static org.assertj.core.api.Assertions.*;

class DriverClassRegistryTest {

    @TempDir Path root;
    private DriverPluginLoader loader;

    @AfterEach
    void closePluginClassLoaders() {
        // Windows: an open URLClassLoader locks driver.jar and blocks @TempDir cleanup
        if (loader != null) loader.destroy();
    }

    /** Compiles a trivial public class and packs it into a plugin jar. */
    private void pluginWithClass(String plugin, String fqcn) throws IOException {
        Path work = Files.createDirectories(root.resolve("work"));
        Path srcDir = Files.createDirectories(work.resolve("src"));
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        Path src = srcDir.resolve(simple + ".java");
        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
        Files.writeString(src, "package " + pkg + ";\n"
                + "public class " + simple + " { public String tag() { return \"" + plugin + "\"; } }");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "-d", work.resolve("out").toString(), src.toString());
        assertThat(rc).isZero();
        Path classFile = work.resolve("out").resolve(fqcn.replace('.', '/') + ".class");
        Path dir = Files.createDirectories(root.resolve(plugin));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(dir.resolve("driver.jar")))) {
            out.putNextEntry(new JarEntry(fqcn.replace('.', '/') + ".class"));
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }
    }

    private DriverClassRegistry registry() {
        loader = new DriverPluginLoader(root.toString());
        loader.afterPropertiesSet();
        return new DriverClassRegistry(loader);
    }

    @Test
    @DisplayName("Classes absent from every plugin resolve from the server classpath")
    void parentFallback() throws Exception {
        assertThat(registry().loadClass("com.systar.monitor.asset.Probe"))
                .isEqualTo(com.systar.monitor.asset.Probe.class);
    }

    @Test
    @DisplayName("Plugin-owned classes load through the plugin classloader")
    void pluginOwned() throws Exception {
        pluginWithClass("solo", "com.systar.monitor.drivers.solo.SoloService");
        Class<?> c = registry().loadClass("com.systar.monitor.drivers.solo.SoloService");
        assertThat(c.getSimpleName()).isEqualTo("SoloService");
        Object tag = c.getDeclaredConstructor().newInstance().getClass()
                .getMethod("tag").invoke(c.getDeclaredConstructor().newInstance());
        assertThat(tag).isEqualTo("solo");
    }

    @Test
    @DisplayName("Unknown class throws ClassNotFoundException (caller adds row context)")
    void unknown() throws Exception {
        assertThatThrownBy(() -> registry().loadClass("com.systar.monitor.drivers.nope.Nope"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("FQCN collision resolves to the first plugin in directory order")
    void firstMatchWins() throws Exception {
        pluginWithClass("second", "com.systar.monitor.drivers.dup.DupService");
        pluginWithClass("first", "com.systar.monitor.drivers.dup.DupService");
        Object tag = registry().loadClass("com.systar.monitor.drivers.dup.DupService")
                .getDeclaredConstructor().newInstance();
        assertThat(tag.getClass().getMethod("tag").invoke(tag)).isEqualTo("first");
    }
}
