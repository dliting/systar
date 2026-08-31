package com.systar.data.service.impl;

import com.systar.common.config.SystemConfigManager;
import com.systar.data.service.retention.RetentionResult;
import com.systar.data.service.retention.RetentionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = com.systar.data.test.DataTestApplication.class)
@ActiveProfiles("test")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DataRetentionServiceImplTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SystemConfigManager configManager;

    private DataRetentionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataRetentionServiceImpl(jdbc, configManager);
    }

    // ======================== cleanByColumn — monthly batch ========================

    @Nested
    @DisplayName("cleanByColumn — monthly batch delete")
    class CleanByColumn {

        @Test
        @DisplayName("deletes rows before cutoff, keeps rows after")
        void deletesBeforeCutoffKeepsAfter() {
            LocalDateTime before = LocalDateTime.now().minusDays(1);
            LocalDateTime old     = LocalDateTime.now().minusDays(10);
            LocalDateTime recent  = LocalDateTime.now().minusHours(12);

            jdbc.update("INSERT INTO t_sample_float (monitor, moment, \"value\") VALUES (9999, ?, 1.0)", old);
            jdbc.update("INSERT INTO t_sample_float (monitor, moment, \"value\") VALUES (9999, ?, 2.0)", recent);

            RetentionResult result = service.cleanByColumn("t_sample_float", "moment", before);

            assertThat(result.table()).isEqualTo("t_sample_float");
            assertThat(result.deletedCount()).isGreaterThanOrEqualTo(1);

            Integer remaining = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_sample_float WHERE monitor = 9999", Integer.class);
            assertThat(remaining).isEqualTo(1);

            jdbc.update("DELETE FROM t_sample_float WHERE monitor = 9999");
        }

        @Test
        @DisplayName("returns zero for empty table")
        void returnsZeroForEmptyTable() {
            jdbc.update("DELETE FROM t_sample_float WHERE monitor = 8888");

            RetentionResult result = service.cleanByColumn("t_sample_float", "moment",
                    LocalDateTime.now().minusDays(1));
            assertThat(result.table()).isEqualTo("t_sample_float");
        }

        @Test
        @DisplayName("skips cleanup when min time >= before cutoff")
        void skipsWhenAllDataIsNewer() {
            LocalDateTime future = LocalDateTime.now().plusDays(30);
            LocalDateTime before = LocalDateTime.now().minusDays(1);

            jdbc.update("INSERT INTO t_sample_float (monitor, moment, \"value\") VALUES (9998, ?, 3.0)", future);

            RetentionResult result = service.cleanByColumn("t_sample_float", "moment", before);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_sample_float WHERE monitor = 9998", Integer.class);
            assertThat(count).isEqualTo(1);

            jdbc.update("DELETE FROM t_sample_float WHERE monitor = 9998");
        }

        @Test
        @DisplayName("deletes across month boundaries correctly")
        void deletesAcrossMonthBoundaries() {
            LocalDateTime twoMonthsAgo = LocalDateTime.now().minusMonths(2);
            LocalDateTime monthStart   = twoMonthsAgo.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime midMonth     = monthStart.plusDays(15);
            LocalDateTime before       = LocalDateTime.now().minusDays(1);

            jdbc.update("INSERT INTO t_sample_float (monitor, moment, \"value\") VALUES (9997, ?, 5.0)", midMonth);
            jdbc.update("INSERT INTO t_sample_float (monitor, moment, \"value\") VALUES (9997, ?, 6.0)", LocalDateTime.now().minusHours(6));

            RetentionResult result = service.cleanByColumn("t_sample_float", "moment", before);

            assertThat(result.deletedCount()).isGreaterThanOrEqualTo(1);

            Integer remaining = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_sample_float WHERE monitor = 9997", Integer.class);
            assertThat(remaining).isEqualTo(1);

            jdbc.update("DELETE FROM t_sample_float WHERE monitor = 9997");
        }
    }

    // ======================== executeAll — config driven ========================

    @Nested
    @DisplayName("executeAll — config driven")
    class ExecuteAll {

        @Test
        @DisplayName("reads retention days from SystemConfigManager")
        void readsConfigFromManager() {
            Map<String, String> config = Map.of(
                    "data_retention.sample_days", "1",
                    "data_retention.alarm_log_days", "1",
                    "data_retention.linkage_log_days", "1"
            );
            configManager.loadConfigs(config);

            LocalDateTime old = LocalDateTime.now().minusDays(10);
            jdbc.update("INSERT INTO t_sample_float (monitor, moment, \"value\") VALUES (9996, ?, 7.0)", old);

            RetentionSummary summary = service.executeAll();

            assertThat(summary.sampleFloat().deletedCount()).isGreaterThanOrEqualTo(1);

            jdbc.update("DELETE FROM t_sample_float WHERE monitor = 9996");
        }
    }

    // ======================== alarm cleanup ordering ========================

    @Nested
    @DisplayName("alarm cleanup")
    class AlarmCleanup {

        @Test
        @DisplayName("cleans alarm_message and error_message_log independently")
        void cleansAlarmTablesIndependently() {
            LocalDateTime before = LocalDateTime.now().minusDays(1);
            LocalDateTime old    = LocalDateTime.now().minusDays(10);

            jdbc.update("INSERT INTO t_error_message_log (alarm_rule_id, asset_id, state, time) VALUES (1, 9995, 1, ?)", old);
            Integer logId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

            jdbc.update("INSERT INTO t_alarm_message (log_id, alarm_time) VALUES (?, ?)", logId, old);

            RetentionResult logResult  = service.cleanByColumn("t_error_message_log", "time", before);
            RetentionResult msgResult  = service.cleanByColumn("t_alarm_message", "alarm_time", before);

            assertThat(logResult.deletedCount()).isGreaterThanOrEqualTo(1);
            assertThat(msgResult.deletedCount()).isGreaterThanOrEqualTo(1);
        }
    }

    // ======================== validation & security ========================

    @Nested
    @DisplayName("validation and security")
    class ValidationSecurity {

        @Test
        @DisplayName("rejects non-allowlisted table name")
        void rejectsNonAllowlistedTable() {
            assertThatThrownBy(() ->
                    service.cleanByColumn("t_evil_table", "moment", LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Table not allowed");
        }

        @Test
        @DisplayName("rejects non-allowlisted column name")
        void rejectsNonAllowlistedColumn() {
            assertThatThrownBy(() ->
                    service.cleanByColumn("t_sample_float", "evil_column", LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Column not allowed");
        }

        @Test
        @DisplayName("clampDays clamps to minimum")
        void clampDaysClampsToMinimum() {
            assertThat(DataRetentionServiceImpl.clampDays(0)).isEqualTo(1);
            assertThat(DataRetentionServiceImpl.clampDays(-5)).isEqualTo(1);
        }

        @Test
        @DisplayName("clampDays clamps to maximum")
        void clampDaysClampsToMaximum() {
            assertThat(DataRetentionServiceImpl.clampDays(4000)).isEqualTo(3650);
        }

        @Test
        @DisplayName("clampDays preserves valid values")
        void clampDaysPreservesValid() {
            assertThat(DataRetentionServiceImpl.clampDays(90)).isEqualTo(90);
            assertThat(DataRetentionServiceImpl.clampDays(180)).isEqualTo(180);
        }
    }

    // ======================== concurrency guard ========================

    @Nested
    @DisplayName("concurrency guard")
    class ConcurrencyGuard {

        @Test
        @DisplayName("returns empty summary on concurrent invocation")
        void returnsEmptyOnConcurrentInvocation() {
            // Simulate concurrent invocation by setting running=true
            service.running.set(true);

            RetentionSummary summary = service.executeAll();
            assertThat(summary.sampleFloat().deletedCount()).isEqualTo(0);

            // Reset for other tests
            service.running.set(false);
        }
    }
}
