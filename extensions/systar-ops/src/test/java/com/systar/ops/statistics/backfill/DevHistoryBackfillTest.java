package com.systar.ops.statistics.backfill;

import com.systar.common.database.DatabaseDialect;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * The dev profile's persistent H2 DB must show real trend history out of the
 * box: 24h of raw samples (finest-granularity default view) plus HOUR/DAY/
 * WEEK/MONTH stats rows (aggregated views). Without this backfill the stats
 * DB is created empty and every aggregated view stays empty for weeks.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DevHistoryBackfillTest {

    private HikariDataSource   mainDs;
    private HikariDataSource   statsDs;
    private JdbcTemplate       mainJdbc;
    private JdbcTemplate       statsJdbc;
    private DevHistoryBackfill backfill;

    @BeforeEach
    void setUp() {
        mainDs = ds("jdbc:h2:mem:devhist;DB_CLOSE_DELAY=-1");
        mainJdbc = new JdbcTemplate(mainDs);
        statsDs = ds("jdbc:h2:mem:devhist_stats;DB_CLOSE_DELAY=-1");
        statsJdbc = new JdbcTemplate(statsDs);

        mainJdbc.execute("CREATE TABLE IF NOT EXISTS t_sample_float"
                + " (monitor INT NOT NULL, \"value\" DOUBLE, moment TIMESTAMP NOT NULL)");
        statsJdbc.execute("CREATE TABLE IF NOT EXISTS t_monitor_stats ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "monitor INT NOT NULL, bucket_start TIMESTAMP NOT NULL,"
                + "bucket_end TIMESTAMP NOT NULL, granularity TINYINT NOT NULL,"
                + "avg_val DOUBLE, max_val DOUBLE, min_val DOUBLE,"
                + "open_val DOUBLE, close_val DOUBLE,"
                + "sample_count INT DEFAULT 0, updated_at TIMESTAMP NOT NULL,"
                + "CONSTRAINT uk_dhb UNIQUE (monitor, granularity, bucket_start))");

        // DB_CLOSE_DELAY=-1 keeps these in-mem DBs alive across test methods —
        // wipe them so the MONTH-count guard can't leak between tests.
        mainJdbc.execute("DELETE FROM t_sample_float");
        statsJdbc.execute("DELETE FROM t_monitor_stats");

        var dialect = new DatabaseDialect() {
            @Override public String getDatabaseType()       { return "h2"; }
            @Override public String getSchemaLocation()     { return ""; }
            @Override public String getDataLocation()       { return ""; }
            @Override public String getUpsertAttributeSql() { return ""; }
        };
        backfill = new DevHistoryBackfill(mainJdbc, statsJdbc, dialect);
    }

    @AfterEach
    void tearDown() {
        if (mainDs  != null) mainDs.close();
        if (statsDs != null) statsDs.close();
    }

    private static HikariDataSource ds(String url) {
        var d = new HikariDataSource();
        d.setJdbcUrl(url);
        d.setUsername("sa");
        d.setPassword("");
        d.setMaximumPoolSize(1);
        return d;
    }

    private long count(String table, String where, Object... args) {
        String sql = "SELECT COUNT(*) FROM " + table + (where.isEmpty() ? "" : " WHERE " + where);
        return mainJdbc.queryForObject(sql, Long.class, args);
    }

    private long statsCount(int monitor, int granularity) {
        return statsJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_monitor_stats WHERE monitor = ? AND granularity = ?",
                Long.class, monitor, granularity);
    }

    @Test
    void seedsTwentyFourHoursOfRawSamplesWithinProbeRange() {
        backfill.backfill();

        long raw = count("t_sample_float",
                "monitor = 1201 AND moment >= DATEADD('HOUR', -24, NOW())");
        assertThat(raw).isGreaterThanOrEqualTo(1440L);

        // 送风温度 seed range is -10..50C; the demo sine wave stays inside the
        // simulator's operating band 16.85..29.15C.
        var mm = mainJdbc.queryForMap(
                "SELECT MIN(\"value\") AS lo, MAX(\"value\") AS hi FROM t_sample_float WHERE monitor = 1201");
        assertThat(((Number) mm.get("lo")).doubleValue()).isBetween(16.0, 29.2);
        assertThat(((Number) mm.get("hi")).doubleValue()).isBetween(16.0, 29.2);
    }

    @Test
    void seedsAllFourAggregatedGranularitiesForSimProbes() {
        backfill.backfill();

        for (int probe : new int[]{1201, 1206, 1211, 1216}) {
            assertThat(statsCount(probe, 1)).as("HOUR 7d").isGreaterThanOrEqualTo(168L);
            assertThat(statsCount(probe, 2)).as("DAY 90d").isGreaterThanOrEqualTo(90L);
            assertThat(statsCount(probe, 3)).as("WEEK 1y").isGreaterThanOrEqualTo(52L);
            assertThat(statsCount(probe, 4)).as("MONTH 5y").isGreaterThanOrEqualTo(60L);
        }
    }

    @Test
    void statsRowsCarryPlausibleOhlcAndCounts() {
        backfill.backfill();

        var row = statsJdbc.queryForMap(
                "SELECT * FROM t_monitor_stats WHERE monitor = 1201 AND granularity = 2"
                + " ORDER BY bucket_start DESC LIMIT 1");
        double avg  = ((Number) row.get("avg_val")).doubleValue();
        double max  = ((Number) row.get("max_val")).doubleValue();
        double min  = ((Number) row.get("min_val")).doubleValue();
        double open = ((Number) row.get("open_val")).doubleValue();
        double close = ((Number) row.get("close_val")).doubleValue();
        assertThat(min).isLessThanOrEqualTo(avg);
        assertThat(avg).isLessThanOrEqualTo(max);
        assertThat(open).isBetween(min, max);
        assertThat(close).isBetween(min, max);
        assertThat(((Number) row.get("sample_count")).intValue()).isPositive();
    }

    @Test
    void secondRunIsIdempotent() {
        backfill.backfill();
        long rawBefore   = count("t_sample_float", "monitor = 1201");
        long statsBefore = statsJdbc.queryForObject("SELECT COUNT(*) FROM t_monitor_stats", Long.class);

        backfill.backfill();

        assertThat(count("t_sample_float", "monitor = 1201")).isEqualTo(rawBefore);
        assertThat(statsJdbc.queryForObject("SELECT COUNT(*) FROM t_monitor_stats", Long.class))
                .isEqualTo(statsBefore);
    }

    @Test
    void skipsRawSeedForProbeWithRealSamplesInWindow() {
        // Real collected samples (simulator or live device) already cover the
        // window — seeding synthetic raw on top interleaves two series at the
        // same cadence (zigzag curve, duplicated axis ticks). Seed only probes
        // with no real history in the window.
        mainJdbc.update("INSERT INTO t_sample_float (monitor, \"value\", moment)"
                        + " VALUES (1201, 25.6, ?)",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(5)));

        backfill.backfill();

        assertThat(count("t_sample_float", "monitor = 1201")).isEqualTo(1L);
        assertThat(count("t_sample_float", "monitor = 1206")).isGreaterThanOrEqualTo(1440L);
    }

    @Test
    void statsSeedKeepsExistingRealAggregates() {
        // Backfill fills the long tail; it must never overwrite buckets the
        // aggregator already computed from real samples.
        var hourStart = java.time.LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        statsJdbc.update("INSERT INTO t_monitor_stats"
                        + " (monitor, bucket_start, bucket_end, granularity, avg_val, max_val,"
                        + "  min_val, sample_count, updated_at) VALUES (1201, ?, ?, 1, 999, 999, 999, 6, NOW())",
                java.sql.Timestamp.valueOf(hourStart),
                java.sql.Timestamp.valueOf(hourStart.plusHours(1)));

        backfill.backfill();

        assertThat(statsJdbc.queryForObject(
                "SELECT avg_val FROM t_monitor_stats"
                + " WHERE monitor = 1201 AND granularity = 1 AND bucket_start = ?",
                Double.class, java.sql.Timestamp.valueOf(hourStart))).isEqualTo(999.0);
    }

    @Test
    void skipsWhenStatsAlreadyBackfilled() {
        // Guard: an existing MONTH history means a previous backfill (or real
        // aggregation) already covers the long tail — never re-seed over it.
        for (int m = 0; m < 12; m++) {
            statsJdbc.update("INSERT INTO t_monitor_stats"
                            + " (monitor, bucket_start, bucket_end, granularity, avg_val, max_val,"
                            + "  min_val, sample_count, updated_at)"
                            + " VALUES (1201, DATEADD('MONTH', ?, NOW()), NOW(), 4, 1, 1, 1, 1, NOW())",
                    -m);
        }

        backfill.backfill();

        assertThat(count("t_sample_float", "")).isZero();
    }
}
