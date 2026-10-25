package com.systar.ops.statistics.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * B3: aggregateOHLC must expose open_val (first sample value in the bucket)
 * and close_val (last). The same statement serves epoch-aligned buckets via
 * TrendBucketExpr.getEpochBucketExpr (B4), so both bucket expressions are
 * exercised here.
 */
class TrendSqlProviderTest {

    private JdbcTemplate h2() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:provider_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(new HikariDataSource(cfg));
        jdbc.execute("CREATE TABLE t_sample_float (monitor INT, `value` DOUBLE, moment TIMESTAMP)");
        jdbc.update("INSERT INTO t_sample_float VALUES (1, 10, ?)", Timestamp.valueOf("2026-10-25 00:05:00"));
        jdbc.update("INSERT INTO t_sample_float VALUES (1, 20, ?)", Timestamp.valueOf("2026-10-25 00:35:00"));
        jdbc.update("INSERT INTO t_sample_float VALUES (1, 30, ?)", Timestamp.valueOf("2026-10-25 00:55:00"));
        jdbc.update("INSERT INTO t_sample_float VALUES (1, 44, ?)", Timestamp.valueOf("2026-10-25 01:20:00"));
        return jdbc;
    }

    private Map<String, Object> params(String bucketExpr) {
        Map<String, Object> p = new HashMap<>();
        p.put("tableName", "t_sample_float");
        p.put("monitorId", 1);
        p.put("start", LocalDateTime.of(2026, 10, 25, 0, 0));
        p.put("end", LocalDateTime.of(2026, 10, 25, 2, 0));
        p.put("bucketExpr", bucketExpr);
        return p;
    }

    /**
     * The provider returns a MyBatis template; render it the way MyBatis
     * would before handing raw JDBC the statement: ${} is literal text
     * substitution, #{} becomes a JDBC '?' (bound positionally below).
     */
    private String render(String sql, Map<String, Object> params) {
        return sql.replace("${tableName}", (String) params.get("tableName"))
                .replace("${bucketExpr}", (String) params.get("bucketExpr"))
                .replace("#{monitorId}", "?")
                .replace("#{start}", "?")
                .replace("#{end}", "?");
    }

    private List<Map<String, Object>> run(String sql, Map<String, Object> params) {
        return h2().queryForList(render(sql, params), 1,
                LocalDateTime.of(2026, 10, 25, 0, 0),
                LocalDateTime.of(2026, 10, 25, 2, 0));
    }

    @Test
    void aggregateOhlcReturnsFirstAndLastValuePerCalendarBucket() {
        Map<String, Object> p = params("HOUR(moment)");
        String sql = new TrendSqlProvider().aggregateOHLC(p);
        List<Map<String, Object>> rows = run(sql, p);
        assertThat(rows).hasSize(2);
        Map<String, Object> hour0 = rows.get(0);
        assertThat(((Number) hour0.get("open_val")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) hour0.get("close_val")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) hour0.get("avg_val")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) hour0.get("sample_count")).intValue()).isEqualTo(3);
    }

    @Test
    void epochBucketExprGroupsByFixedWidthBuckets() {
        String expr = TrendBucketExpr.getEpochBucketExpr(1800, "h2"); // 30-min buckets
        Map<String, Object> p = params(expr);
        String sql = new TrendSqlProvider().aggregateOHLC(p);
        List<Map<String, Object>> rows = run(sql, p);
        // buckets: 00:00-00:30 -> [10]; 00:30-01:00 -> [20,30]; 01:00-01:30 -> [44]
        assertThat(rows).hasSize(3);
        assertThat(((Number) rows.get(1).get("open_val")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) rows.get(1).get("close_val")).doubleValue()).isEqualTo(30.0);
    }
}
