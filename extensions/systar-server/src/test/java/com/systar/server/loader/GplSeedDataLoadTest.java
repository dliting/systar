package com.systar.server.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies the GPL driver seed rows reach the dev-profile database.
 * <p>
 * The dev profile seeds through {@code spring.sql.init}; the GPL seed script
 * ships inside the optional {@code systar-drivers-gpl} jar and is referenced
 * as an {@code optional:classpath:} location — present only when the
 * {@code gpl-drivers} Maven profile is active. Without the module the whole
 * test class is skipped (pure-MIT form has no GPL rows by design).
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIf("gplDriversPresent")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("GPL driver seed data in dev profile")
class GplSeedDataLoadTest {

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbc;

    static boolean gplDriversPresent() {
        try {
            Class.forName("com.systar.monitor.drivers.bacnet.BacnetService");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    @DisplayName("BACnet driver-type dict row 803 is seeded")
    void dictRowSeeded() {
        String name = jdbc.queryForObject(
                "SELECT name FROM t_code_dict WHERE id = 803", String.class);
        assertThat(name).isEqualTo("BACNET");
    }

    @Test
    @DisplayName("GPL service rows 103/104 are seeded with GPL driver classes")
    void serviceRowsSeeded() {
        assertThat(jdbc.queryForObject(
                "SELECT driver_class FROM t_service WHERE id = 103", String.class))
                .isEqualTo("com.systar.monitor.drivers.iec104.Iec104Service");
        assertThat(jdbc.queryForObject(
                "SELECT driver_class FROM t_service WHERE id = 104", String.class))
                .isEqualTo("com.systar.monitor.drivers.bacnet.BacnetService");
    }

    @Test
    @DisplayName("GPL demo service assets 44/45 are seeded")
    void assetRowsSeeded() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_asset WHERE id IN (44, 45)", Integer.class))
                .isEqualTo(2);
    }
}
