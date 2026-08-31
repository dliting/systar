package com.systar.data.service.impl;

import com.systar.common.config.SystemConfigManager;
import com.systar.data.service.DataRetentionService;
import com.systar.data.service.retention.RetentionResult;
import com.systar.data.service.retention.RetentionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DataRetentionServiceImpl implements DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionServiceImpl.class);

    private static final String CONFIG_SAMPLE_DAYS      = "data_retention.sample_days";
    private static final String CONFIG_ALARM_LOG_DAYS   = "data_retention.alarm_log_days";
    private static final String CONFIG_LINKAGE_LOG_DAYS = "data_retention.linkage_log_days";

    public static final int DEFAULT_SAMPLE_DAYS      = 90;
    public static final int DEFAULT_ALARM_LOG_DAYS   = 180;
    public static final int DEFAULT_LINKAGE_LOG_DAYS = 180;

    /** Minimum allowed retention days to prevent accidental mass deletion. */
    public static final int MIN_RETENTION_DAYS = 1;
    /** Maximum allowed retention days. */
    public static final int MAX_RETENTION_DAYS = 3650;

    /** Allowlist of table names accepted by the cleanup methods. */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "t_sample_float", "t_sample_int", "t_sample_boolean", "t_sample_exception",
            "t_alarm_message", "t_error_message_log", "t_linkage_log"
    );

    /** Allowlist of column names accepted by the cleanup methods. */
    private static final Set<String> ALLOWED_COLUMNS = Set.of("moment", "alarm_time", "time");

    /** Guard against concurrent invocations. Visible for testing. */
    final AtomicBoolean running = new AtomicBoolean(false);

    private final JdbcTemplate         jdbc;
    private final SystemConfigManager  configManager;

    public DataRetentionServiceImpl(JdbcTemplate jdbc, SystemConfigManager configManager) {
        this.jdbc           = jdbc;
        this.configManager  = configManager;
    }

    @Override
    public RetentionSummary executeAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Data retention already in progress; skipping this invocation");
            return emptySummary();
        }

        try {
            int sampleDays      = clampDays(configManager.getIntValue(CONFIG_SAMPLE_DAYS, DEFAULT_SAMPLE_DAYS));
            int alarmLogDays    = clampDays(configManager.getIntValue(CONFIG_ALARM_LOG_DAYS, DEFAULT_ALARM_LOG_DAYS));
            int linkageLogDays  = clampDays(configManager.getIntValue(CONFIG_LINKAGE_LOG_DAYS, DEFAULT_LINKAGE_LOG_DAYS));

            LocalDateTime sampleBefore   = LocalDateTime.now().minusDays(sampleDays);
            LocalDateTime alarmBefore    = LocalDateTime.now().minusDays(alarmLogDays);
            LocalDateTime linkageBefore  = LocalDateTime.now().minusDays(linkageLogDays);

            log.info("Data retention: sampleBefore={}, alarmBefore={}, linkageBefore={}",
                    sampleBefore, alarmBefore, linkageBefore);

            RetentionResult floatResult     = cleanByColumn("t_sample_float",         "moment",      sampleBefore);
            RetentionResult intResult       = cleanByColumn("t_sample_int",           "moment",      sampleBefore);
            RetentionResult boolResult      = cleanByColumn("t_sample_boolean",       "moment",      sampleBefore);
            RetentionResult exceptionResult = cleanByColumn("t_sample_exception",     "moment",      sampleBefore);

            // Delete alarm messages first (child table with log_id reference), then error logs
            RetentionResult alarmMsgResult  = cleanByColumn("t_alarm_message",        "alarm_time",  alarmBefore);
            RetentionResult alarmLogResult  = cleanByColumn("t_error_message_log",    "time",        alarmBefore);

            RetentionResult linkageResult   = cleanByColumn("t_linkage_log",          "time",        linkageBefore);

            return new RetentionSummary(
                    floatResult, intResult, boolResult, exceptionResult,
                    alarmMsgResult, alarmLogResult, linkageResult
            );
        } finally {
            running.set(false);
        }
    }

    private RetentionSummary emptySummary() {
        RetentionResult zero = new RetentionResult("none", 0);
        return new RetentionSummary(zero, zero, zero, zero, zero, zero, zero);
    }

    /**
     * Clamps retention days to valid range [MIN_RETENTION_DAYS, MAX_RETENTION_DAYS].
     * Prevents accidental mass deletion from zero or negative values.
     */
    static int clampDays(int days) {
        if (days < MIN_RETENTION_DAYS) {
            log.warn("Retention days {} is below minimum {}; clamping to {}", days, MIN_RETENTION_DAYS, MIN_RETENTION_DAYS);
            return MIN_RETENTION_DAYS;
        }
        if (days > MAX_RETENTION_DAYS) {
            log.warn("Retention days {} exceeds maximum {}; clamping to {}", days, MAX_RETENTION_DAYS, MAX_RETENTION_DAYS);
            return MAX_RETENTION_DAYS;
        }
        return days;
    }

    /**
     * Deletes rows from the given table where the specified time column
     * is before the cutoff, in monthly batches.
     * <p>
     * Only allowlisted table and column names are accepted to prevent
     * SQL injection via string concatenation.
     */
    RetentionResult cleanByColumn(String table, String timeCol, LocalDateTime before) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Table not allowed for retention cleanup: " + table);
        }
        if (!ALLOWED_COLUMNS.contains(timeCol)) {
            throw new IllegalArgumentException("Column not allowed for retention cleanup: " + timeCol);
        }

        LocalDateTime minTime = queryMinTime(table, timeCol);
        if (minTime == null || !minTime.isBefore(before)) {
            log.debug("No data to clean from {} before {}", table, before);
            return new RetentionResult(table, 0);
        }

        int totalDeleted = 0;
        YearMonth currentMonth = YearMonth.from(minTime);
        YearMonth beforeMonth  = YearMonth.from(before);

        while (!currentMonth.isAfter(beforeMonth)) {
            LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
            LocalDateTime monthEnd;
            if (currentMonth.equals(beforeMonth)) {
                monthEnd = before;
            } else {
                monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
            }

            if (!monthStart.isBefore(monthEnd)) {
                currentMonth = currentMonth.plusMonths(1);
                continue;
            }

            String sql = "DELETE FROM " + table + " WHERE " + timeCol + " >= ? AND " + timeCol + " < ?";
            int deleted = jdbc.update(sql, monthStart, monthEnd);
            if (deleted > 0) {
                log.info("Cleaned {} rows from {} for [{} → {})", deleted, table, monthStart, monthEnd);
            }
            totalDeleted += deleted;
            currentMonth = currentMonth.plusMonths(1);
        }

        log.info("Total cleaned from {}: {} rows", table, totalDeleted);
        return new RetentionResult(table, totalDeleted);
    }

    private LocalDateTime queryMinTime(String table, String timeCol) {
        String sql = "SELECT MIN(" + timeCol + ") FROM " + table;
        Object result = jdbc.queryForObject(sql, Object.class);
        return toLocalDateTime(result);
    }

    private static LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof String s) {
            try { return LocalDateTime.parse(s.replace(' ', 'T')); }
            catch (Exception e) {
                log.warn("Cannot parse timestamp value '{}': {}", s, e.getMessage());
                return null;
            }
        }
        log.warn("Unexpected timestamp type: {} ({})", val, val.getClass().getName());
        return null;
    }
}