package com.systar.server.plugin;

import com.systar.monitor.asset.AssetException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.jar.*;

import static org.assertj.core.api.Assertions.*;

class DriverPluginLoaderTest {

    @TempDir Path root;

    private DriverPluginLoader loaderFor() {
        DriverPluginLoader loader = new DriverPluginLoader(root.toString());
        loader.afterPropertiesSet();
        return loader;
    }

    /** Writes a jar with the given entry names; parent dir must exist. */
    static void jar(Path file, String... entryNames) throws IOException {
        Files.createDirectories(file.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            for (String name : entryNames) {
                out.putNextEntry(new JarEntry(name));
                out.write(content(name));
                out.closeEntry();
            }
        }
    }

    static byte[] content(String entry) {
        if (entry.endsWith(".xml")) {
            return ("<Services><Service Name=\"FixtureSvc\">"
                    + "<JavaClass>com.systar.monitor.drivers.fixture.FixtureService</JavaClass>"
                    + "</Service></Services>").getBytes();
        }
        return new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}; // 占位类条目
    }

    @Test
    @DisplayName("Missing drivers root yields empty plugin list, no failure")
    void missingRoot() {
        DriverPluginLoader loader = new DriverPluginLoader(root.resolve("nope").toString());
        loader.afterPropertiesSet();
        assertThat(loader.plugins()).isEmpty();
    }

    @Test
    @DisplayName("Plugin directories load in directory-name order")
    void sortedByDirName() throws IOException {
        Files.createDirectories(root.resolve("zeta"));
        Files.createDirectories(root.resolve("alpha/lib"));
        jar(root.resolve("zeta/driver.jar"), "com/systar/monitor/drivers/zeta/zeta-services.xml");
        jar(root.resolve("alpha/lib/dep.jar"), "com/systar/monitor/drivers/alpha/alpha-services.xml");
        assertThat(loaderFor().plugins()).extracting(DriverPlugin::name)
                .containsExactly("alpha", "zeta");
    }

    @Test
    @DisplayName("Only the plugin's own jar/lib/loose XMLs are attributed to it")
    void ownUrlAttribution() throws IOException {
        Path a = Files.createDirectories(root.resolve("aaa"));
        Path b = Files.createDirectories(root.resolve("bbb"));
        jar(a.resolve("driver.jar"), "com/systar/monitor/drivers/aaa/a-services.xml");
        jar(b.resolve("lib/b.jar"), "com/systar/monitor/drivers/bbb/b-services.xml");
        // loose XML under plugin dir root package tree (XML-only style)
        Path loose = Files.createDirectories(b.resolve("com/systar/monitor/drivers/bbb"));
        Files.writeString(loose.resolve("b-loose.xml"), new String(content("x.xml")));

        DriverPlugin pluginB = loaderFor().plugins().stream()
                .filter(p -> p.name().equals("bbb")).findFirst().orElseThrow();
        assertThat(pluginB.typeResources()).extracting(PluginTypeResource::location)
                .anyMatch(loc -> loc.endsWith("b-services.xml"))
                .allMatch(loc -> loc.contains("bbb"));
        assertThat(pluginB.typeResources()).hasSize(2);
    }

    @Test
    @DisplayName("Empty directory (no jar, no XML, no seed) is skipped with WARN")
    void emptyDirSkipped() throws IOException {
        Files.createDirectories(root.resolve("ghost"));
        jar(root.resolve("real/driver.jar"), "com/systar/monitor/drivers/real/r.xml");
        assertThat(loaderFor().plugins()).extracting(DriverPlugin::name).containsExactly("real");
    }

    @Test
    @DisplayName("Seed CSVs listed sorted; class index maps FQCN to first owning plugin by dir order")
    void seedsAndClassIndex() throws IOException {
        Path p = Files.createDirectories(root.resolve("one/seed"));
        Files.writeString(p.resolve("02-service.csv"), "id\n2\n");
        Files.writeString(p.resolve("01-dictionary.csv"), "id\n1\n");
        jar(root.resolve("one/driver.jar"),
                "com/systar/monitor/drivers/one/OneService.class",
                "com/systar/monitor/drivers/one/one-services.xml");
        jar(root.resolve("two/driver.jar"), "com/systar/monitor/drivers/one/OneService.class");

        DriverPluginLoader loader = loaderFor();
        DriverPlugin one = loader.plugins().get(0);
        assertThat(one.seedFiles()).extracting(f -> f.getFileName().toString())
                .containsExactly("01-dictionary.csv", "02-service.csv");
        assertThat(one.ownedClasses())
                .contains("com.systar.monitor.drivers.one.OneService");
        assertThat(loader.pluginOwning("com.systar.monitor.drivers.one.OneService"))
                .get().extracting(DriverPlugin::name).isEqualTo("one");
    }

    @Test
    @DisplayName("Corrupt driver.jar (not a valid zip) fails startup naming the plugin dir (spec §7)")
    void corruptJarFailsStartup() throws IOException {
        Files.createDirectories(root.resolve("broken"));
        Files.writeString(root.resolve("broken/driver.jar"), "not a zip file");
        jar(root.resolve("fine/driver.jar"), "com/systar/monitor/drivers/fine/f.xml");
        DriverPluginLoader loader = new DriverPluginLoader(root.toString());
        assertThatThrownBy(loader::afterPropertiesSet)
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("broken")
                .hasMessageContaining("driver.jar");
    }

    @Test
    @DisplayName("Destroy closes plugin classloaders, releasing jar file handles")
    void destroyClosesPluginClassLoaders() throws IOException {
        Path jarFile = root.resolve("lockable/driver.jar");
        jar(jarFile, "com/systar/monitor/drivers/lockable/l.xml");
        DriverPluginLoader loader = loaderFor();
        loader.destroy();
        // On Windows an open URLClassLoader locks the jar; a closed one allows deletion
        Files.delete(jarFile);
        assertThat(Files.exists(jarFile)).isFalse();
    }

    @Test
    @DisplayName("Stray XML inside a plugin subdir (e.g. lib/) is ignored, not attributed (spec §7)")
    void strayXmlIgnored() throws IOException {
        Path lib = Files.createDirectories(root.resolve("stray/lib"));
        Files.writeString(lib.resolve("confusing.xml"), "<Services/>");
        jar(root.resolve("stray/driver.jar"), "com/systar/monitor/drivers/stray/s.xml");
        DriverPlugin stray = loaderFor().plugins().stream()
                .filter(p -> p.name().equals("stray")).findFirst().orElseThrow();
        assertThat(stray.typeResources()).hasSize(1); // only the jar entry; the stray file is WARNed away
    }

    @Test
    @DisplayName("Loose XML at the plugin directory root is collected and attributed (spec §7 finalized placement)")
    void looseXmlAtDirRootCollected() throws IOException {
        Path vis = Files.createDirectories(root.resolve("vis"));
        Files.writeString(vis.resolve("vis.xml"), new String(content("x.xml")));
        DriverPlugin plugin = loaderFor().plugins().stream()
                .filter(p -> p.name().equals("vis")).findFirst().orElseThrow();
        assertThat(plugin.typeResources()).hasSize(1);
        assertThat(plugin.typeResources().get(0).location()).endsWith("/vis/vis.xml");
    }
}
