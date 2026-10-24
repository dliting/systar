package com.systar.server.plugin;

import com.systar.monitor.asset.AssetStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Full dev-profile context against a fixture plugin directory: plugin type XMLs
 * register, plugin seed CSVs insert, and a pre-existing DB row referencing an
 * absent driver class is WARN-skipped without breaking startup.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class PluginEndToEndIntegrationTest {

    static Path fixtureRoot;

    @DynamicPropertySource
    static void pluginDir(DynamicPropertyRegistry registry) throws IOException {
        fixtureRoot = Files.createTempDirectory("e2e-plugins");
        Path pkg = Files.createDirectories(fixtureRoot.resolve("e2edemo/com/systar/monitor/drivers/e2edemo"));
        Files.writeString(pkg.resolve("e2e-services.xml"),
                "<Services><Service Name=\"E2EDemoSvc\"/></Services>");
        Path seed = Files.createDirectories(fixtureRoot.resolve("e2edemo/seed"));
        Files.writeString(seed.resolve("02-service.csv"),
                "id,name,caption,mode,driver_class,max_connections,type_name\n"
                        + "50001,e2e_svc,E2E演示,0,,5,E2EDemoSvc\n");
        // a row whose driver class exists in no plugin → startup WARN skip
        // (logical name must be whitelisted: "service" — the "missing class" is
        //  the row's driver_class value, not part of the file name)
        Files.writeString(seed.resolve("03-service.csv"),
                "id,name,caption,mode,driver_class,max_connections,type_name\n"
                        + "50002,e2e_ghost,Ghost,0,com.example.AbsentDriver,5,\n");
        registry.add("systar.drivers.path", () -> fixtureRoot.toString());
    }

    @Autowired @Qualifier("mainJdbcTemplate") private JdbcTemplate jdbc;
    @Autowired private AssetStore store;

    @Test
    @DisplayName("Plugin type XML registers its service type")
    void pluginTypeRegistered() {
        assertThat(store.getServiceTypes().find("E2EDemoSvc")).isNotNull();
    }

    @Test
    @DisplayName("Plugin seed rows inserted; unresolvable row WARN-skipped, context green")
    void seedsInsertedAndGhostSkipped() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_service WHERE id=50001", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_service WHERE id=50002", Integer.class)).isEqualTo(1);
        assertThat(store.findAsset(50001)).isNotNull();   // seeded row instantiated via registered type
        assertThat(store.findAsset(50002)).isNull();      // class absent → WARN skip, startup survived
    }
}
