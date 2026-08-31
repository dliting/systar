package com.systar.ops.statistics.aggregator;

import com.systar.common.database.DatabaseDialect;
import com.systar.ops.statistics.mapper.TrendBucketExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically aggregates raw sample data into t_monitor_stats
 * using batch SQL (GROUP BY monitor, bucket).
 * <p>
 * Cascade: HOUR → DAY → WEEK → MONTH, each level triggered
 * asynchronously on a single-thread executor to avoid blocking
 * the scheduler.
 * <p>
 * Catches up missed aggregation periods on startup.
 */
@Component
public class MonitorStatsAggregator {

    private static final Logger log = LoggerFactory.getLogger(MonitorStatsAggregator.class);

    private static final int HOUR  = 1;
    private static final int DAY   = 2;
    private static final int WEEK  = 3;
    private static final int MONTH = 4;

    private final JdbcTemplate        mainJdbc;
    private final JdbcTemplate        statsJdbc;
    private final DatabaseDialect     dialect;

    private final AtomicBoolean       catchUpInProgress = new AtomicBoolean(false);
    private final AtomicBoolean       cascadeInProgress  = new AtomicBoolean(false);

    @Value("${systar.statistics.max-catch-up-hours:168}")
    private int maxCatchUpHours;

    public MonitorStatsAggregator(@Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbc,
                                  @Autowired(required = false)
                                  @Qualifier("statsJdbcTemplate") JdbcTemplate statsJdbc,
                                  DatabaseDialect dialect) {
        this.mainJdbc  = mainJdbc;
        this.statsJdbc = statsJdbc != null ? statsJdbc : mainJdbc;
        this.dialect   = dialect;
    }

    // ======================== scheduled ========================

    @Scheduled(fixedRateString = "${systar.statistics.aggregation-interval-ms:300000}")
    public void aggregateHourly() {
        if (catchUpInProgress.get()) {
            log.debug("Catch-up in progress; skipping scheduled aggregation");
            return;
        }
        try {
            LocalDateTime now   = now();
            LocalDateTime start = getLastAggregatedHour();
            if (start == null) start = now.minusHours(1).truncatedTo(ChronoUnit.HOURS);

            long missingHours = ChronoUnit.HOURS.between(start, now);
            if (missingHours < 1) return;

            LocalDateTime end = now.truncatedTo(ChronoUnit.HOURS);
            log.info("Aggregating HOUR data: [{} → {}) ({} hour(s))", start, end, missingHours);

            int rowCount = batchAggregate(start, end);
            log.info("Hourly aggregation complete: {} rows inserted", rowCount);

            // Trigger async cascade for the affected time window
            cascadeAsync(start, end);
        } catch (Exception e) {
            log.error("Hourly aggregation failed: {}", e.getMessage(), e);
        }
    }

    // ======================== catch-up ========================

    @PostConstruct
    void catchUpOnStartup() {
        // Delay to let DB initialization complete
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

        if (!catchUpInProgress.compareAndSet(false, true)) {
            log.debug("Catch-up already running; skipping");
            return;
        }
        try {
            LocalDateTime now   = now();
            LocalDateTime start = getLastAggregatedHour();
            if (start == null) {
                log.info("No prior aggregation data; skipping catch-up");
                return;
            }

            long missingHours = ChronoUnit.HOURS.between(start, now);
            if (missingHours <= 1) {
                log.info("Catch-up: {} hour(s) behind — covered by next scheduled run", missingHours);
                return;
            }

            long cap = Math.min(missingHours, maxCatchUpHours);
            LocalDateTime catchUpStart = now.minusHours(cap);
            log.info("Catch-up: {} hours behind (capped at {}). Aggregating [{} → {})...",
                    missingHours, maxCatchUpHours, catchUpStart, now);

            int rowCount = batchAggregate(catchUpStart, now);
            log.info("Catch-up complete: {} HOUR rows aggregated", rowCount);

            cascadeSync(catchUpStart, now);
        } catch (Exception e) {
            log.error("Catch-up failed: {}", e.getMessage(), e);
        } finally {
            catchUpInProgress.set(false);
        }
    }

    // ======================== batch aggregation ========================

    /**
     * Aggregates all monitors for the given time range in three batch SQLs
     * (one per sample table), inserting into t_monitor_stats granularity=1.
     *
     * @return total number of rows inserted
     */
    int batchAggregate(LocalDateTime start, LocalDateTime end) {
        int total = 0;
        total += aggregateSampleTableSafe("t_sample_float",   start, end);
        total += aggregateSampleTableSafe("t_sample_int",     start, end);
        total += aggregateSampleTableSafe("t_sample_boolean", start, end);
        return total;
    }

    private int aggregateSampleTableSafe(String tableName, LocalDateTime start, LocalDateTime end) {
        try {
            return aggregateSampleTable(tableName, start, end);
        } catch (Exception e) {
            log.debug("Skipping {}: {}", tableName, e.getMessage());
            return 0;
        }
    }

    private int aggregateSampleTable(String tableName, LocalDateTime start, LocalDateTime end) {
        String dbType        = dialect.getDatabaseType();
        String bucketExpr    = TrendBucketExpr.getBucketExpr("HOUR", dbType, isWeekStartMonday());
        // value column: H2 needs quoting (reserved word), MySQL also needs backticks
        String v = "h2".equals(dbType) ? "\"value\"" : "`value`";

        String selectSql = (
            "SELECT monitor,"
            + " CAST(bucket AS DATETIME) AS bucket_start,"
            + " " + HOUR + " AS granularity,"
            + " AVG(" + v + ") AS avg_val,"
            + " MAX(" + v + ") AS max_val,"
            + " MIN(" + v + ") AS min_val,"
            + " COUNT(*) AS sample_count"
            + " FROM ("
            + " SELECT monitor, " + v + ", " + bucketExpr + " AS bucket"
            + " FROM " + tableName
            + " WHERE moment >= ? AND moment < ?"
            + ") raw"
            + " GROUP BY monitor, bucket"
        );

        List<Map<String, Object>> rows = mainJdbc.queryForList(selectSql, start, end);

        // INSERT into stats DB (t_monitor_stats)
        int count = 0;
        for (Map<String, Object> row : rows) {
            int    monitor   = ((Number) row.get("monitor")).intValue();
            Object bucketObj = row.get("bucket_start");
            if (bucketObj == null) continue;

            LocalDateTime bucketStart = toLocalDateTime(bucketObj);
            LocalDateTime bucketEnd   = bucketStart.plusHours(1);
            double avgVal   = ((Number) row.get("avg_val")).doubleValue();
            double maxVal   = ((Number) row.get("max_val")).doubleValue();
            double minVal   = ((Number) row.get("min_val")).doubleValue();
            long   samples  = ((Number) row.get("sample_count")).longValue();

            upsertStat(monitor, bucketStart, bucketEnd, HOUR, avgVal, maxVal, minVal, samples);
            count++;
        }
        return count;
    }

    private void upsertStat(int monitor, LocalDateTime bucketStart, LocalDateTime bucketEnd,
                             int granCode, double avgVal, double maxVal, double minVal,
                             long sampleCount) {
        if ("h2".equals(dialect.getDatabaseType())) {
            statsJdbc.update(
                "MERGE INTO t_monitor_stats (monitor, bucket_start, bucket_end, granularity, "
                + "avg_val, max_val, min_val, sample_count, updated_at) "
                + "KEY (monitor, granularity, bucket_start) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                monitor, bucketStart, bucketEnd, granCode,
                avgVal, maxVal, minVal, sampleCount);
        } else {
            statsJdbc.update(
                "INSERT INTO t_monitor_stats (monitor, bucket_start, bucket_end, granularity, "
                + "avg_val, max_val, min_val, sample_count, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE avg_val = VALUES(avg_val), "
                + "max_val = VALUES(max_val), min_val = VALUES(min_val), "
                + "sample_count = VALUES(sample_count), updated_at = NOW()",
                monitor, bucketStart, bucketEnd, granCode,
                avgVal, maxVal, minVal, sampleCount);
        }
    }

    // ======================== cascade ========================

    @Async("statsCascadeExecutor")
    void cascadeAsync(LocalDateTime cascadeStart, LocalDateTime cascadeEnd) {
        if (!cascadeInProgress.compareAndSet(false, true)) {
            log.debug("Cascade already in progress; skipping");
            return;
        }
        try {
            cascadeSync(cascadeStart, cascadeEnd);
        } finally {
            cascadeInProgress.set(false);
        }
    }

    void cascadeSync(LocalDateTime cascadeStart, LocalDateTime cascadeEnd) {
        String dbType = dialect.getDatabaseType();
        boolean monday = isWeekStartMonday();

        int dayRows = cascadeFromTo(HOUR, DAY, "DAY", dbType, cascadeStart, cascadeEnd);
        log.info("Cascade HOUR→DAY: {} rows", dayRows);

        int weekRows = cascadeFromTo(DAY, WEEK, "WEEK", dbType, cascadeStart, cascadeEnd);
        log.info("Cascade DAY→WEEK: {} rows", weekRows);

        int monthRows = cascadeFromTo(DAY, MONTH, "MONTH", dbType, cascadeStart, cascadeEnd);
        log.info("Cascade DAY→MONTH: {} rows", monthRows);
    }

    private int cascadeFromTo(int fromGran, int toGran, String toGranKey,
                              String dbType, LocalDateTime windowStart, LocalDateTime windowEnd) {
        String bucketExpr = TrendBucketExpr.getColumnBucketExpr(
                toGranKey, dbType, isWeekStartMonday(), "bucket_start");

        String upsertSql = buildCascadeUpsert(toGran, bucketExpr);

        return statsJdbc.update(upsertSql, fromGran, windowStart, windowEnd);
    }

    private String buildCascadeUpsert(int toGran, String bucketExpr) {
        if ("h2".equals(dialect.getDatabaseType())) {
            return """
                MERGE INTO t_monitor_stats
                USING (
                    SELECT monitor,
                           %s AS bucket_start,
                           %s AS bucket_end,
                           %d AS granularity,
                           SUM(avg_val * sample_count) / NULLIF(SUM(sample_count), 0) AS avg_val,
                           MAX(max_val) AS max_val,
                           MIN(min_val) AS min_val,
                           SUM(sample_count) AS sample_count,
                           NOW() AS updated_at
                    FROM t_monitor_stats
                    WHERE granularity = ? AND bucket_start >= ? AND bucket_start < ?
                    GROUP BY monitor, %s
                ) src
                ON (t_monitor_stats.monitor = src.monitor
                    AND t_monitor_stats.granularity = src.granularity
                    AND t_monitor_stats.bucket_start = src.bucket_start)
                WHEN MATCHED THEN UPDATE SET
                    avg_val = src.avg_val, max_val = src.max_val,
                    min_val = src.min_val, sample_count = src.sample_count,
                    updated_at = src.updated_at
                WHEN NOT MATCHED THEN INSERT
                    (monitor, bucket_start, bucket_end, granularity,
                     avg_val, max_val, min_val, sample_count, updated_at)
                VALUES (src.monitor, src.bucket_start, src.bucket_end, src.granularity,
                        src.avg_val, src.max_val, src.min_val, src.sample_count, src.updated_at)
                """.formatted(bucketExpr, bucketExpr, toGran, bucketExpr);
        } else {
            return """
                INSERT INTO t_monitor_stats
                    (monitor, bucket_start, bucket_end, granularity,
                     avg_val, max_val, min_val, sample_count, updated_at)
                SELECT monitor,
                       %s AS bucket_start,
                       %s AS bucket_end,
                       %d AS granularity,
                       SUM(avg_val * sample_count) / NULLIF(SUM(sample_count), 0) AS avg_val,
                       MAX(max_val) AS max_val,
                       MIN(min_val) AS min_val,
                       SUM(sample_count) AS sample_count,
                       NOW() AS updated_at
                FROM t_monitor_stats
                WHERE granularity = ? AND bucket_start >= ? AND bucket_start < ?
                GROUP BY monitor, %s
                ON DUPLICATE KEY UPDATE
                    avg_val = VALUES(avg_val),
                    max_val = VALUES(max_val),
                    min_val = VALUES(min_val),
                    sample_count = VALUES(sample_count),
                    updated_at = VALUES(updated_at)
                """.formatted(bucketExpr, bucketExpr, toGran, bucketExpr);
        }
    }

    // ======================== helpers ========================

    private boolean isWeekStartMonday() {
        return true;  // overridden by systar.statistics.weekStartDay
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * Returns the latest HOUR bucket_start present in t_monitor_stats,
     * or null if the table is empty.
     */
    private LocalDateTime getLastAggregatedHour() {
        List<Object> rows = statsJdbc.query(
            "SELECT MAX(bucket_start) FROM t_monitor_stats WHERE granularity = ?",
            (rs, i) -> rs.getObject(1), HOUR);
        Object val = rows.isEmpty() ? null : rows.get(0);
        return toLocalDateTime(val);
    }

    private static LocalDateTime toLocalDateTime(Object val) {
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof String s) {
            try { return LocalDateTime.parse(s.replace(' ', 'T')); }
            catch (Exception e) { return null; }
        }
        return null;
    }
}
