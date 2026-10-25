package com.systar.ops.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.*;

/**
 * B1: t_monitor_stats gains nullable open_val/close_val. Fresh databases get
 * them from the CREATE; existing databases get them from a guarded, idempotent
 * ALTER run at bean init. The guard must be safe to run twice and must not
 * depend on INFORMATION_SCHEMA identifier casing (H2 uppercases, MySQL does
 * not), so it probes the column with a zero-row SELECT instead.
 */
class StatsSchemaGuardTest {

    private JdbcTemplate freshDb() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:guard_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(new HikariDataSource(cfg));
    }

    private static final String LEGACY_CREATE =
            "CREATE TABLE t_monitor_stats ("
          + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
          + "monitor INT NOT NULL, bucket_start TIMESTAMP NOT NULL,"
          + "bucket_end TIMESTAMP NOT NULL, granularity TINYINT NOT NULL,"
          + "avg_val DOUBLE, max_val DOUBLE, min_val DOUBLE,"
          + "sample_count INT DEFAULT 0, updated_at TIMESTAMP NOT NULL,"
          + "CONSTRAINT uk_monitor_gran_start UNIQUE (monitor, granularity, bucket_start))";

    @Test
    void addsMissingColumnsAndIsIdempotent() {
        JdbcTemplate jdbc = freshDb();
        jdbc.execute(LEGACY_CREATE);

        StatisticsDataConfig.ensureOpenCloseColumns(jdbc);
        StatisticsDataConfig.ensureOpenCloseColumns(jdbc); // second run must not throw

        jdbc.update("INSERT INTO t_monitor_stats"
                        + " (monitor, bucket_start, bucket_end, granularity, open_val, close_val, updated_at)"
                        + " VALUES (1, ?, ?, 1, 1.5, 2.5, NOW())",
                Timestamp.valueOf("2026-10-25 00:00:00"), Timestamp.valueOf("2026-10-25 01:00:00"));
        var row = jdbc.queryForMap("SELECT open_val, close_val FROM t_monitor_stats WHERE monitor = 1");
        assertThat(((Number) row.get("open_val")).doubleValue()).isEqualTo(1.5);
        assertThat(((Number) row.get("close_val")).doubleValue()).isEqualTo(2.5);
    }

    @Test
    void toleratesAlreadyPresentColumns() {
        JdbcTemplate jdbc = freshDb();
        jdbc.execute(LEGACY_CREATE);
        jdbc.execute("ALTER TABLE t_monitor_stats ADD COLUMN open_val FLOAT NULL");
        jdbc.execute("ALTER TABLE t_monitor_stats ADD COLUMN close_val FLOAT NULL");

        StatisticsDataConfig.ensureOpenCloseColumns(jdbc); // must be a silent no-op
    }
}
