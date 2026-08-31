package com.systar.ops.statistics.aggregator;

import com.systar.common.database.DatabaseDialect;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorStatsAggregatorTest {

    private HikariDataSource       mainDs;
    private HikariDataSource       statsDs;
    private JdbcTemplate           mainJdbc;
    private JdbcTemplate           statsJdbc;
    private MonitorStatsAggregator aggregator;

    @BeforeEach
    void setUp() {
        mainDs = ds("jdbc:h2:mem:testagg;DB_CLOSE_DELAY=-1");
        mainJdbc = new JdbcTemplate(mainDs);

        statsDs = ds("jdbc:h2:mem:testagg_stats;DB_CLOSE_DELAY=-1");
        statsJdbc = new JdbcTemplate(statsDs);

        mainJdbc.execute("CREATE TABLE IF NOT EXISTS t_sample_float"
                + " (monitor INT NOT NULL, \"value\" DOUBLE, moment TIMESTAMP NOT NULL)");
        mainJdbc.execute("CREATE TABLE IF NOT EXISTS t_sample_int"
                + " (monitor INT NOT NULL, \"value\" INT, moment TIMESTAMP NOT NULL)");
        mainJdbc.execute("CREATE TABLE IF NOT EXISTS t_sample_boolean"
                + " (monitor INT NOT NULL, \"value\" BOOLEAN, moment TIMESTAMP NOT NULL)");

        statsJdbc.execute("CREATE TABLE IF NOT EXISTS t_monitor_stats ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "monitor INT NOT NULL, bucket_start TIMESTAMP NOT NULL,"
                + "bucket_end TIMESTAMP NOT NULL, granularity TINYINT NOT NULL,"
                + "avg_val DOUBLE, max_val DOUBLE, min_val DOUBLE,"
                + "sample_count INT DEFAULT 0, updated_at TIMESTAMP NOT NULL,"
                + "CONSTRAINT uk_agt UNIQUE (monitor, granularity, bucket_start))");

        mainJdbc.execute("DELETE FROM t_sample_float");
        mainJdbc.execute("DELETE FROM t_sample_int");
        mainJdbc.execute("DELETE FROM t_sample_boolean");
        statsJdbc.execute("DELETE FROM t_monitor_stats");

        var dialect = new DatabaseDialect() {
            @Override public String getDatabaseType()       { return "h2"; }
            @Override public String getSchemaLocation()     { return ""; }
            @Override public String getDataLocation()       { return ""; }
            @Override public String getUpsertAttributeSql() { return ""; }
        };
        aggregator = new MonitorStatsAggregator(mainJdbc, statsJdbc, dialect);
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

    private void insert(int monitor, double value, LocalDateTime moment) {
        mainJdbc.update(
                "INSERT INTO t_sample_float (monitor, \"value\", moment) VALUES (?,?,?)",
                monitor, value, moment);
    }

    @Test
    void singleMonitorSingleHour() {
        var base = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        insert(1, 10.0, base.plusMinutes(1));
        insert(1, 20.0, base.plusMinutes(2));
        insert(1, 30.0, base.plusMinutes(3));

        assertThat(aggregator.batchAggregate(base, base.plusHours(1))).isEqualTo(1);

        var rows = statsJdbc.queryForList(
                "SELECT * FROM t_monitor_stats WHERE monitor=1 AND granularity=1");
        assertThat(rows).hasSize(1);
        var r = rows.get(0);
        assertThat((Double) r.get("avg_val")).isCloseTo(20.0, within(0.01));
        assertThat((Double) r.get("max_val")).isEqualTo(30.0);
        assertThat((Double) r.get("min_val")).isEqualTo(10.0);
        assertThat(((Number) r.get("sample_count")).longValue()).isEqualTo(3L);
    }

    @Test
    void emptyRangeReturnsZero() {
        var base = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        assertThat(aggregator.batchAggregate(base, base.plusHours(1))).isZero();
    }

    @Test
    void multipleMonitors() {
        var base = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        insert(1, 10.0, base.plusMinutes(1));
        insert(2, 100.0, base.plusMinutes(2));
        assertThat(aggregator.batchAggregate(base, base.plusHours(1))).isEqualTo(2);
    }

    @Test
    void cascadeHourToDay() {
        var base = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        insert(1, 10.0, base.plusMinutes(10));
        insert(1, 20.0, base.plusMinutes(20));
        insert(1, 30.0, base.plusMinutes(30));

        aggregator.batchAggregate(base, base.plusHours(1));
        aggregator.cascadeSync(base, base.plusDays(1));

        var rows = statsJdbc.queryForList(
                "SELECT * FROM t_monitor_stats WHERE monitor=1 AND granularity=2");
        assertThat(rows).hasSize(1);
        var d = rows.get(0);
        assertThat((Double) d.get("avg_val")).isCloseTo(20.0, within(0.01));
        assertThat((Double) d.get("max_val")).isEqualTo(30.0);
    }

    @Test
    void cascadeAllGranularities() {
        var base = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        insert(1, 50.0, base.plusMinutes(5));

        aggregator.batchAggregate(base, base.plusHours(1));
        aggregator.cascadeSync(base, base.plusDays(1));

        // HOUR(1) + DAY(1) guaranteed; WEEK/MONTH may not produce if window too narrow
        long total = statsJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_monitor_stats WHERE monitor=1", Long.class);
        assertThat(total).isGreaterThanOrEqualTo(2L);

        // Verify DAY row exists
        long dayCount = statsJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_monitor_stats WHERE monitor=1 AND granularity=2", Long.class);
        assertThat(dayCount).isEqualTo(1L);
    }

    @Test
    void cascadeIdempotent() {
        var base = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        insert(1, 42.0, base.plusMinutes(5));

        aggregator.batchAggregate(base, base.plusHours(1));
        aggregator.cascadeSync(base, base.plusDays(1));
        long before = statsJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_monitor_stats WHERE granularity=2", Long.class);

        aggregator.cascadeSync(base, base.plusDays(1));
        long after = statsJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_monitor_stats WHERE granularity=2", Long.class);

        assertThat(after).isEqualTo(before);
    }
}
