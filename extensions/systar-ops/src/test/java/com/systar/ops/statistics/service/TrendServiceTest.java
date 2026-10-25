package com.systar.ops.statistics.service;

import com.systar.common.database.DatabaseDialect;
import com.systar.monitor.asset.type.DataType;
import com.systar.ops.statistics.mapper.TrendMapper;
import com.systar.ops.statistics.model.TrendBarVO;
import com.systar.ops.statistics.model.TrendPointVO;
import com.systar.ops.statistics.model.TrendQuery;
import com.systar.ops.statistics.model.TrendResponseVO;
import com.systar.ops.statistics.model.TrendSummaryVO;
import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.h2.Driver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TrendService} package-private helper methods.
 *
 * <p>These tests verify the pure computation logic (MA, aggregation, summary,
 * granularity selection) without needing real sample data in the database.</p>
 */
@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class TrendServiceTest {

    @Autowired
    private TrendService trendService;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbc;

    @Autowired
    private TrendMapper trendMapper;

    @Autowired
    private DatabaseDialect databaseDialect;

    /**
     * Stats live in a separate datasource, so @Transactional/@Rollback does
     * not undo inserts here — seed helpers use unique monitor ids.
     */
    @Autowired
    @Qualifier("statsJdbcTemplate")
    private JdbcTemplate statsJdbc;

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 15, 0, 0, 0);

    // ==================== aggregateToCoarser ====================

    @Nested
    class AggregateToCoarser {

        @Test
        void twentyFourHourlyBars_producesOneDailyBar() {
            List<TrendBarVO> hourlyBars = new ArrayList<>();
            LocalDateTime dayStart = LocalDateTime.of(2026, 1, 15, 0, 0, 0);

            for (int h = 0; h < 24; h++) {
                double avg = 15 + h;      // 15..38
                double max = 30 + h;      // 30..53
                double min = 5 + h;       // 5..28
                hourlyBars.add(new TrendBarVO(
                        dayStart.plusHours(h), avg, max, min, 100));
            }

            List<TrendBarVO> result = trendService.aggregateToCoarser(hourlyBars, "DAY");

            assertThat(result).hasSize(1);
            TrendBarVO dailyBar = result.get(0);
            assertThat(dailyBar.time()).isEqualTo(dayStart);
            // avg = mean of 15..38 = (15+38)/2 = 26.5
            assertThat(dailyBar.avg()).isEqualTo(26.5);
            assertThat(dailyBar.max()).isEqualTo(53.0);
            assertThat(dailyBar.min()).isEqualTo(5.0);
            assertThat(dailyBar.sampleCount()).isEqualTo(2400);
        }

        @Test
        void fortyEightHourlyBars_producesTwoDailyBars() {
            List<TrendBarVO> hourlyBars = new ArrayList<>();
            LocalDateTime day1 = LocalDateTime.of(2026, 1, 15, 0, 0, 0);

            for (int h = 0; h < 48; h++) {
                double avg = 10 + h;
                double max = 30 + h;
                double min = 5 + h;
                hourlyBars.add(new TrendBarVO(
                        day1.plusHours(h), avg, max, min, 50));
            }

            List<TrendBarVO> result = trendService.aggregateToCoarser(hourlyBars, "DAY");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).time()).isEqualTo(day1);
            assertThat(result.get(0).sampleCount()).isEqualTo(1200);
            assertThat(result.get(1).time()).isEqualTo(day1.plusDays(1));
            assertThat(result.get(1).sampleCount()).isEqualTo(1200);
        }

        @Test
        void emptyList_returnsEmptyResult() {
            List<TrendBarVO> result = trendService.aggregateToCoarser(
                    Collections.emptyList(), "DAY");

            assertThat(result).isEmpty();
        }

        @Test
        void nullInput_returnsEmptyResult() {
            List<TrendBarVO> result = trendService.aggregateToCoarser(null, "DAY");

            assertThat(result).isEmpty();
        }

        @Test
        void singleHourlyBar_returnsSameValuesForWeekGranularity() {
            TrendBarVO single = new TrendBarVO(BASE_TIME, 15, 20, 5, 100);
            List<TrendBarVO> result = trendService.aggregateToCoarser(
                    List.of(single), "WEEK");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).avg()).isEqualTo(15);
            assertThat(result.get(0).max()).isEqualTo(20);
            assertThat(result.get(0).min()).isEqualTo(5);
            assertThat(result.get(0).sampleCount()).isEqualTo(100);
        }

        @Test
        void weekGranularity_groupsByMonday() {
            LocalDateTime monday = LocalDateTime.of(2026, 1, 12, 0, 0, 0);
            LocalDateTime tuesday = monday.plusDays(1);
            LocalDateTime nextMonday = monday.plusDays(7);

            List<TrendBarVO> bars = List.of(
                    new TrendBarVO(monday, 15, 20, 5, 100),
                    new TrendBarVO(tuesday, 17, 22, 7, 100),
                    new TrendBarVO(nextMonday, 35, 40, 25, 100)
            );

            List<TrendBarVO> result = trendService.aggregateToCoarser(bars, "WEEK");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).time()).isEqualTo(monday);
            assertThat(result.get(1).time()).isEqualTo(nextMonday);
        }

        @Test
        void monthGranularity_groupsByFirstOfMonth() {
            LocalDateTime jan10 = LocalDateTime.of(2026, 1, 10, 10, 0, 0);
            LocalDateTime jan20 = LocalDateTime.of(2026, 1, 20, 10, 0, 0);
            LocalDateTime feb05 = LocalDateTime.of(2026, 2, 5, 10, 0, 0);
            LocalDateTime feb10 = LocalDateTime.of(2026, 2, 10, 10, 0, 0);

            List<TrendBarVO> bars = List.of(
                    new TrendBarVO(jan10, 15, 20, 5, 100),
                    new TrendBarVO(jan20, 17, 22, 7, 100),
                    new TrendBarVO(feb05, 35, 40, 25, 100),
                    new TrendBarVO(feb10, 37, 42, 27, 100)
            );

            List<TrendBarVO> result = trendService.aggregateToCoarser(bars, "MONTH");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).time()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
            // January: avg of 15 and 17 = 16.0
            assertThat(result.get(0).avg()).isEqualTo(16.0);
            assertThat(result.get(0).max()).isEqualTo(22.0);
            assertThat(result.get(0).min()).isEqualTo(5.0);
            assertThat(result.get(0).sampleCount()).isEqualTo(200);
            assertThat(result.get(1).time()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0, 0));
            assertThat(result.get(1).avg()).isEqualTo(36.0);
            assertThat(result.get(1).max()).isEqualTo(42.0);
            assertThat(result.get(1).min()).isEqualTo(25.0);
            assertThat(result.get(1).sampleCount()).isEqualTo(200);
        }
    }

    // ==================== Degraded stats schema ====================

    @Nested
    class DegradedStatsSchema {

        /**
         * The B1 startup guard WARNs "trend open/close degrade to NULL" when
         * its ALTER fails — the aggregated query path must honor that promise
         * (points with null open/close) instead of failing every request with
         * an unknown-column error (final review Important #2).
         */
        @Test
        void statsTableWithoutOpenCloseColumns_returnsPointsWithNullOpenClose() {
            SimpleDriverDataSource ds = new SimpleDriverDataSource(
                    new Driver(),
                    "jdbc:h2:mem:degraded_stats_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1",
                    "sa", "");
            JdbcTemplate degraded = new JdbcTemplate(ds);
            degraded.execute("CREATE TABLE t_monitor_stats ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, monitor INT NOT NULL,"
                    + "bucket_start DATETIME NOT NULL, bucket_end DATETIME NOT NULL,"
                    + "granularity TINYINT NOT NULL, avg_val FLOAT, max_val FLOAT, min_val FLOAT,"
                    + "sample_count INT NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL)");
            degraded.update("INSERT INTO t_monitor_stats "
                            + "(monitor, bucket_start, bucket_end, granularity, avg_val, max_val, min_val, sample_count, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    999, BASE_TIME, BASE_TIME.plusDays(1), 2, 21.0, 24.0, 18.0, 7, BASE_TIME);

            TrendService degradedService =
                    new TrendService(trendMapper, databaseDialect, jdbc, degraded);
            // Window entirely in the past → the on-the-fly current-period path
            // is skipped and only the stats-table read runs.
            TrendResponseVO resp = degradedService.getTrendData(new TrendQuery(
                    999, "PROBE", BASE_TIME, BASE_TIME.plusDays(2), "DAY"));

            assertThat(resp.getPoints()).hasSize(1);
            TrendPointVO point = resp.getPoints().get(0);
            assertThat(point.t()).isEqualTo(BASE_TIME);
            assertThat(point.avg()).isEqualTo(21.0);
            assertThat(point.max()).isEqualTo(24.0);
            assertThat(point.min()).isEqualTo(18.0);
            assertThat(point.open()).isNull();
            assertThat(point.close()).isNull();
            assertThat(point.count()).isEqualTo(7L);
        }
    }

    // ==================== buildSummary ====================

    @Nested
    class BuildSummary {

        @Test
        void multiplePoints_returnsCorrectSummary() {
            List<TrendPointVO> points = List.of(
                    new TrendPointVO(BASE_TIME, 20.0, 50.0, 5.0, 20.0, 25.0, 100),
                    new TrendPointVO(BASE_TIME.plusHours(1), 30.0, 45.0, 8.0, 25.0, 30.0, 150),
                    new TrendPointVO(BASE_TIME.plusHours(2), 25.0, 60.0, 3.0, 30.0, 25.0, 200)
            );

            TrendSummaryVO summary = trendService.buildSummary(points);

            assertThat(summary.currentValue()).isEqualTo(25.0);
            assertThat(summary.periodMax()).isEqualTo(60.0);
            assertThat(summary.periodMin()).isEqualTo(3.0);
            assertThat(summary.totalSamples()).isEqualTo(450);
        }

        @Test
        void singlePoint_allValuesMatch() {
            TrendPointVO single = new TrendPointVO(BASE_TIME, 42.5, 42.5, 42.5, 42.5, 42.5, 10);

            TrendSummaryVO summary = trendService.buildSummary(List.of(single));

            assertThat(summary.currentValue()).isEqualTo(42.5);
            assertThat(summary.periodMax()).isEqualTo(42.5);
            assertThat(summary.periodMin()).isEqualTo(42.5);
            assertThat(summary.totalSamples()).isEqualTo(10);
        }

        @Test
        void emptyList_returnsZeroSummary() {
            TrendSummaryVO summary = trendService.buildSummary(Collections.emptyList());

            assertThat(summary.currentValue()).isEqualTo(0);
            assertThat(summary.periodMax()).isEqualTo(0);
            assertThat(summary.periodMin()).isEqualTo(0);
            assertThat(summary.totalSamples()).isEqualTo(0);
        }

        @Test
        void nullInput_returnsZeroSummary() {
            TrendSummaryVO summary = trendService.buildSummary(null);

            assertThat(summary.currentValue()).isEqualTo(0);
            assertThat(summary.periodMax()).isEqualTo(0);
            assertThat(summary.periodMin()).isEqualTo(0);
            assertThat(summary.totalSamples()).isEqualTo(0);
        }
    }

    // ==================== determineGranularity ====================

    @Nested
    class DetermineGranularity {

        @Test
        void oneDayWindow_returnsIntraday() {
            assertThat(trendService.determineGranularity(86_400L)).isEqualTo("INTRADAY");
        }

        @Test
        void oneDayPlusOneSecond_returnsHour() {
            assertThat(trendService.determineGranularity(86_401L)).isEqualTo("HOUR");
        }

        // Candlestick-friendly AUTO ladder (D6): keep bucket count readable
        // (≤ ~180 bars) — 31d fits in DAY, 180d in WEEK, 2y in MONTH.
        @Test
        void thirtyOneDays_returnsDay() {
            assertThat(trendService.determineGranularity(2_678_400L)).isEqualTo("DAY");
        }

        @Test
        void fourteenDays_returnsHour() {
            assertThat(trendService.determineGranularity(1_209_600L)).isEqualTo("HOUR");
        }

        @Test
        void fourteenDaysPlusOneSecond_returnsDay() {
            assertThat(trendService.determineGranularity(1_209_601L)).isEqualTo("DAY");
        }

        @Test
        void oneHundredEightyDays_returnsWeek() {
            assertThat(trendService.determineGranularity(15_552_000L)).isEqualTo("WEEK");
        }

        @Test
        void oneHundredTwentyDays_returnsDay() {
            assertThat(trendService.determineGranularity(10_368_000L)).isEqualTo("DAY");
        }

        @Test
        void twoYears_returnsMonth() {
            assertThat(trendService.determineGranularity(63_072_000L)).isEqualTo("MONTH");
        }

        @Test
        void threeHundredSixtyFiveDays_returnsWeek() {
            assertThat(trendService.determineGranularity(31_536_000L)).isEqualTo("WEEK");
        }

        @Test
        void verySmallWindow_returnsIntraday() {
            assertThat(trendService.determineGranularity(1L)).isEqualTo("INTRADAY");
        }

        @Test
        void zeroWindow_returnsIntraday() {
            assertThat(trendService.determineGranularity(0L)).isEqualTo("INTRADAY");
        }
    }

    // ==================== resolveDetectIntervalSeconds ====================

    @Nested
    class ResolveDetectIntervalSeconds {

        @Test
        void probeWithInterval10s_returns10() {
            int probeId = createProbe(DataType.FLOAT, "10s");

            long result = trendService.resolveDetectIntervalSeconds(probeId, "PROBE");

            assertThat(result).isEqualTo(10L);
        }

        @Test
        void probeWithHHmmssInterval_returnsCorrectSeconds() {
            int probeId = createProbe(DataType.FLOAT, "00:05:00");

            long result = trendService.resolveDetectIntervalSeconds(probeId, "PROBE");

            assertThat(result).isEqualTo(300L);
        }

        @Test
        void controlWithInterval30s_returns30() {
            int controlId = createControl("30s");

            long result = trendService.resolveDetectIntervalSeconds(controlId, "CONTROL");

            assertThat(result).isEqualTo(30L);
        }

        @Test
        void nonExistentProbe_returnsFallbackDefault() {
            long result = trendService.resolveDetectIntervalSeconds(99999, "PROBE");

            assertThat(result).isEqualTo(10L);
        }

        @Test
        void probeWithBlankInterval_returnsFallbackDefault() {
            int probeId = createProbe(DataType.FLOAT, "  ");

            long result = trendService.resolveDetectIntervalSeconds(probeId, "PROBE");

            assertThat(result).isEqualTo(10L);
        }

        @Test
        void probeWithNullInterval_returnsFallbackDefault() {
            int probeId = createProbeWithNullInterval(DataType.FLOAT);

            long result = trendService.resolveDetectIntervalSeconds(probeId, "PROBE");

            assertThat(result).isEqualTo(10L);
        }

        @Test
        void probeWithHHmmssFormat00_01_00_returns60() {
            int probeId = createProbe(DataType.FLOAT, "00:01:00");

            long result = trendService.resolveDetectIntervalSeconds(probeId, "PROBE");

            assertThat(result).isEqualTo(60L);
        }
    }

    // ==================== resolveSampleTableName ====================

    @Nested
    class ResolveSampleTableName {

        @Test
        void probeWithFloatDataType_returnsTsampleFloat() {
            int probeId = createProbe(DataType.FLOAT, "10s");

            String tableName = trendService.resolveSampleTableName("PROBE", probeId);

            assertThat(tableName).isEqualTo("t_sample_float");
        }

        @Test
        void probeWithIntDataType_returnsTsampleInt() {
            int probeId = createProbe(DataType.INT, "10s");

            String tableName = trendService.resolveSampleTableName("PROBE", probeId);

            assertThat(tableName).isEqualTo("t_sample_int");
        }

        @Test
        void probeWithBooleanDataType_returnsTsampleBoolean() {
            int probeId = createProbe(DataType.BOOLEAN, "10s");

            String tableName = trendService.resolveSampleTableName("PROBE", probeId);

            assertThat(tableName).isEqualTo("t_sample_boolean");
        }

        @Test
        void control_returnsTsampleFloat() {
            int controlId = createControl("10s");

            String tableName = trendService.resolveSampleTableName("CONTROL", controlId);

            assertThat(tableName).isEqualTo("t_sample_float");
        }

        @Test
        void probeWithNullDataType_returnsTsampleFloat() {
            int probeId = createProbeWithNullDataType("10s");

            String tableName = trendService.resolveSampleTableName("PROBE", probeId);

            assertThat(tableName).isEqualTo("t_sample_float");
        }

        @Test
        void nonExistentMonitor_returnsTsampleFloat() {
            String tableName = trendService.resolveSampleTableName("PROBE", 99999);

            assertThat(tableName).isEqualTo("t_sample_float");
        }
    }

    // ==================== getDefaultView ====================

    @Nested
    class GetDefaultView {

        @Test
        void probeWith10sInterval_selectsIntraday() {
            int probeId = createProbe(DataType.FLOAT, "10s");

            TrendResponseVO response = trendService.getDefaultView(probeId, "PROBE");

            assertThat(response.getGranularity()).isEqualTo("INTRADAY");
            assertThat(response.getPoints()).isNotNull();
        }

        @Test
        void probeWith30mInterval_selectsHour() {
            int probeId = createProbe(DataType.FLOAT, "00:30:00");

            TrendResponseVO response = trendService.getDefaultView(probeId, "PROBE");

            assertThat(response.getGranularity()).isEqualTo("HOUR");
            assertThat(response.getPoints()).isNotNull();
        }
    }

    // ==================== toDouble helper ====================

    @Nested
    class ToDouble {

        @Test
        void nullValue_returnsZero() {
            assertThat(TrendService.toDouble(null)).isEqualTo(0.0);
        }

        @Test
        void numberValue_returnsDouble() {
            assertThat(TrendService.toDouble(42)).isEqualTo(42.0);
        }

        @Test
        void booleanTrue_returnsOne() {
            assertThat(TrendService.toDouble(true)).isEqualTo(1.0);
        }

        @Test
        void booleanFalse_returnsZero() {
            assertThat(TrendService.toDouble(false)).isEqualTo(0.0);
        }

        @Test
        void stringValue_parsesDouble() {
            assertThat(TrendService.toDouble("3.14")).isEqualTo(3.14);
        }

        @Test
        void unparseableString_returnsZero() {
            assertThat(TrendService.toDouble("abc")).isEqualTo(0.0);
        }
    }

    // ==================== getTrendData with empty data ====================

    @Nested
    class GetTrendData {

        @Test
        void nonExistentProbe_returnsEmptyResponseForHour() {
            // 2h window: HOUR survives the clamp (a 1h span would clamp down
            // to INTRADAY — see explicitGranularityIsClampedWhenSpanTooSmall)
            LocalDateTime start = LocalDateTime.now().minusHours(2);
            LocalDateTime end = LocalDateTime.now();
            TrendQuery query = new TrendQuery(99998, "PROBE", start, end, "HOUR");

            TrendResponseVO response = trendService.getTrendData(query);

            assertThat(response.getGranularity()).isEqualTo("HOUR");
            assertThat(response.getPoints()).isEmpty();
        }

        @Test
        void nonExistentProbe_returnsEmptyResponseForDay() {
            LocalDateTime start = LocalDateTime.now().minusDays(2);
            LocalDateTime end = LocalDateTime.now();
            TrendQuery query = new TrendQuery(99998, "PROBE", start, end, "DAY");

            TrendResponseVO response = trendService.getTrendData(query);

            assertThat(response.getGranularity()).isEqualTo("DAY");
            assertThat(response.getPoints()).isEmpty();
        }

        @Test
        void invalidGranularity_throwsException() {
            LocalDateTime start = LocalDateTime.now().minusHours(1);
            LocalDateTime end = LocalDateTime.now();

            assertThatThrownBy(() -> new TrendQuery(1, "PROBE", start, end, "YEAR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid granularity");
        }
    }

    // ==================== unified response contract (B3/B4/D6) ====================

    @Nested
    class UnifiedContract {

        @Test
        void aggregatedResponseIsOhlcModeWithOpenCloseAndBucketSeconds() {
            int monitor = seedStatsHourRow(10.0, 30.0, 20.0, 30.0, 10.0, 3);

            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(monitor, "PROBE", BASE_TIME, BASE_TIME.plusDays(1), "HOUR"));

            assertThat(resp.getMode()).isEqualTo(TrendService.MODE_OHLC);
            assertThat(resp.getBucketSeconds()).isEqualTo(3600L);
            assertThat(resp.getPoints()).hasSize(1);
            TrendPointVO p = resp.getPoints().get(0);
            assertThat(p.open()).isEqualTo(10.0);
            assertThat(p.close()).isEqualTo(30.0);
            assertThat(p.count()).isEqualTo(3);
        }

        @Test
        void legacyStatsRowWithoutOpenCloseSerializesNulls() {
            // Review Focus 1: stats row inserted with open_val/close_val NULL
            int monitor = seedStatsHourRow(null, null, 5.0, 9.0, 1.0, 3);

            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(monitor, "PROBE", BASE_TIME, BASE_TIME.plusDays(1), "HOUR"));

            assertThat(resp.getPoints()).hasSize(1);
            TrendPointVO p = resp.getPoints().get(0);
            assertThat(p.open()).isNull();
            assertThat(p.close()).isNull();
            assertThat(p.avg()).isEqualTo(5.0);
        }

        @Test
        void intradaySmallWindowIsRawModeWithOpenEqualsClose() {
            int monitor = seedFloatSamples(
                    10.0, BASE_TIME.plusHours(10),
                    20.0, BASE_TIME.plusHours(10).plusMinutes(5));

            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(monitor, "PROBE",
                            BASE_TIME.plusHours(9), BASE_TIME.plusHours(11), "INTRADAY"));

            assertThat(resp.getMode()).isEqualTo(TrendService.MODE_RAW);
            assertThat(resp.getBucketSeconds()).isNull();
            assertThat(resp.getPoints()).hasSize(2);
            TrendPointVO p = resp.getPoints().get(0);
            assertThat(p.open()).isEqualTo(p.avg());
            assertThat(p.close()).isEqualTo(p.avg());
            assertThat(p.count()).isEqualTo(1);
        }

        @Test
        void nullGranularityResolvesBySpan() {
            // Review Focus 5 / D6: AUTO granularity
            assertThat(TrendService.resolveGranularity(null, 3_600)).isEqualTo("INTRADAY");
            assertThat(TrendService.resolveGranularity(null, 7 * 86_400)).isEqualTo("HOUR");
            assertThat(TrendService.resolveGranularity(null, 30L * 86_400)).isEqualTo("DAY");
            assertThat(TrendService.resolveGranularity(null, 200L * 86_400)).isEqualTo("WEEK");
            assertThat(TrendService.resolveGranularity(null, 400L * 86_400)).isEqualTo("MONTH");
        }

        @Test
        void explicitGranularityIsClampedWhenSpanTooSmall() {
            // span 1h asked as DAY -> clamp down to INTRADAY (bucket 86400 > span)
            assertThat(TrendService.resolveGranularity("DAY", 3_600)).isEqualTo("INTRADAY");
            assertThat(TrendService.resolveGranularity("HOUR", 600)).isEqualTo("INTRADAY");
            assertThat(TrendService.resolveGranularity("WEEK", 2 * 86_400)).isEqualTo("DAY");
            // INTRADAY is always feasible (BUCKETED downsamples)
            assertThat(TrendService.resolveGranularity("INTRADAY", 400L * 86_400)).isEqualTo("INTRADAY");
            // feasible explicit values pass through
            assertThat(TrendService.resolveGranularity("DAY", 30L * 86_400)).isEqualTo("DAY");
        }

        @Test
        void responseEchoesEffectiveGranularityAfterClamp() {
            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(99997, "PROBE",
                            BASE_TIME.plusHours(10), BASE_TIME.plusHours(11), "MONTH"));

            assertThat(resp.getGranularity()).isEqualTo("INTRADAY");
        }
    }

    // ==================== Adaptive INTRADAY downsampling (D5) ====================

    @Nested
    class AdaptiveIntradayDownsample {

        @Test
        void intradaySwitchesToBucketedWhenSampleCountExceedsRawCap() {
            // 5001 samples at 17s spacing fit inside the 24h window
            int monitor = seedFloatSamplesBatch(5001, BASE_TIME, 17);

            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(monitor, "PROBE", BASE_TIME, BASE_TIME.plusHours(24), "INTRADAY"));

            assertThat(resp.getMode()).isEqualTo(TrendService.MODE_BUCKETED);
            assertThat(resp.getBucketSeconds()).isEqualTo(72L); // ceil(86400 / 1200)
            assertThat(resp.getPoints()).isNotEmpty().hasSizeLessThanOrEqualTo(1200);
            TrendPointVO p = resp.getPoints().get(0);
            assertThat(p.open()).isNotNull();
            assertThat(p.close()).isNotNull();
            assertThat(p.count()).isGreaterThan(1);
        }

        @Test
        void intradayStaysRawAtExactlyCap() {
            int monitor = seedFloatSamplesBatch(5000, BASE_TIME, 17);

            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(monitor, "PROBE", BASE_TIME, BASE_TIME.plusHours(24), "INTRADAY"));

            assertThat(resp.getMode()).isEqualTo(TrendService.MODE_RAW);
            assertThat(resp.getPoints()).hasSize(5000);
        }

        @Test
        void stringMonitorIntradayResolvesToEmptyRaw() {
            // STRING kind has no sample table: RAW empty, no exception
            int monitor = createProbe(DataType.STRING, "00:00:10");

            TrendResponseVO resp = trendService.getTrendData(
                    new TrendQuery(monitor, "PROBE", BASE_TIME, BASE_TIME.plusHours(24), "INTRADAY"));

            assertThat(resp.getMode()).isEqualTo(TrendService.MODE_RAW);
            assertThat(resp.getPoints()).isEmpty();
        }
    }

    // ==================== getMetadata ====================

    @Nested
    class GetMetadata {

        @Test
        void probeMetadata_returnsCorrectFields() {
            int probeId = createProbe(DataType.FLOAT, "00:00:10");
            Map<String, Object> meta = trendService.getMetadata(probeId, "PROBE");

            assertThat(meta).isNotEmpty();
            assertThat(meta).containsKey("name");
            assertThat(meta).containsKey("unit");
            assertThat(meta).containsKey("dataType");
            assertThat(meta).containsKey("detectInterval");
        }

        @Test
        void nonExistentProbe_returnsEmptyMap() {
            Map<String, Object> meta = trendService.getMetadata(99999, "PROBE");

            assertThat(meta).isEmpty();
        }

        @Test
        void controlMetadata_returnsCorrectFields() {
            int controlId = createControl("00:00:10");
            Map<String, Object> meta = trendService.getMetadata(controlId, "CONTROL");

            assertThat(meta).isNotEmpty();
            assertThat(meta).containsKey("name");
            assertThat(meta).containsKey("unit");
            assertThat(meta).containsKey("detectInterval");
        }

        @Test
        void unknownMonitorKind_returnsEmptyMap() {
            Map<String, Object> meta = trendService.getMetadata(1, "UNKNOWN");

            assertThat(meta).isEmpty();
        }

        @Test
        void probeMetadata_returnsThresholds() {
            int probeId = createProbeWithThresholds(DataType.FLOAT, "00:00:10",
                    0.0f, 100.0f, "value > 80");
            Map<String, Object> meta = trendService.getMetadata(probeId, "PROBE");

            assertThat(meta).isNotEmpty();
            assertThat(meta.get("minValue")).isEqualTo(0.0);
            assertThat(meta.get("maxValue")).isEqualTo(100.0);
            assertThat(meta.get("warnCond")).isEqualTo("value > 80");
        }

        @Test
        void probeMetadata_thresholdsNullWhenColumnsNull() {
            // Existing createProbe inserts NULL for min_value/max_value/warn_cond —
            // metadata must surface them as null, not 0.
            int probeId = createProbe(DataType.FLOAT, "00:00:10");
            Map<String, Object> meta = trendService.getMetadata(probeId, "PROBE");

            assertThat(meta).isNotEmpty();
            assertThat(meta.get("minValue")).isNull();
            assertThat(meta.get("maxValue")).isNull();
            assertThat(meta.get("warnCond")).isNull();
        }

        @Test
        void controlMetadata_returnsThresholds() {
            int controlId = createControlWithThresholds("00:00:10", 10.0f, 90.0f, "value < 10");
            Map<String, Object> meta = trendService.getMetadata(controlId, "CONTROL");

            assertThat(meta).isNotEmpty();
            assertThat(meta.get("minValue")).isEqualTo(10.0);
            assertThat(meta.get("maxValue")).isEqualTo(90.0);
            assertThat(meta.get("warnCond")).isEqualTo("value < 10");
        }

        @Test
        void probeMetadata_stillResolvesDetectIntervalAfterThresholdExtension() {
            // Regression: queryProbeById is shared by resolveDetectIntervalSeconds and getMetadata.
            // Extension must not break either path.
            int probeId = createProbeWithThresholds(DataType.FLOAT, "00:00:15",
                    0.0f, 100.0f, null);
            long interval = trendService.resolveDetectIntervalSeconds(probeId, "PROBE");

            assertThat(interval).isEqualTo(15L);
        }

        @Test
        void probeMetadata_stillResolvesDataTypeAfterThresholdExtension() {
            int probeId = createProbeWithThresholds(DataType.FLOAT, "00:00:10",
                    0.0f, 100.0f, null);
            String tableName = trendService.resolveSampleTableName("PROBE", probeId);

            assertThat(tableName).isEqualTo("t_sample_float");
        }
    }

    // ==================== granularityCode ====================

    @Nested
    class GranularityCode {

        @Test
        void hour_returns1() {
            assertThat(TrendService.granularityCode("HOUR")).isEqualTo(1);
        }

        @Test
        void day_returns2() {
            assertThat(TrendService.granularityCode("DAY")).isEqualTo(2);
        }

        @Test
        void week_returns3() {
            assertThat(TrendService.granularityCode("WEEK")).isEqualTo(3);
        }

        @Test
        void month_returns4() {
            assertThat(TrendService.granularityCode("MONTH")).isEqualTo(4);
        }
    }

    // ==================== Helpers ====================

    /**
     * Seed one HOUR-granularity stats row in the stats datasource. The stats
     * H2 is a separate datasource (no transaction rollback), so each call
     * claims a fresh monitor id and clears it first.
     */
    private int seedStatsHourRow(Double open, Double close,
                                 double avg, double max, double min, int count) {
        int monitor = 95000 + (int) (System.nanoTime() % 1000);
        statsJdbc.update("DELETE FROM t_monitor_stats WHERE monitor = ?", monitor);
        statsJdbc.update("INSERT INTO t_monitor_stats "
                        + "(monitor, bucket_start, bucket_end, granularity, open_val, close_val, "
                        + "avg_val, max_val, min_val, sample_count, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                monitor, BASE_TIME, BASE_TIME.plusHours(1), 1, open, close, avg, max, min, count);
        return monitor;
    }

    /** Seed two raw float samples; monitor id is unknown to t_probe (falls back to t_sample_float). */
    private int seedFloatSamples(double v1, LocalDateTime m1, double v2, LocalDateTime m2) {
        int monitor = 96000 + (int) (System.nanoTime() % 1000);
        jdbc.update("DELETE FROM t_sample_float WHERE monitor = ?", monitor);
        jdbc.update("INSERT INTO t_sample_float (monitor, `value`, moment) VALUES (?, ?, ?)", monitor, v1, m1);
        jdbc.update("INSERT INTO t_sample_float (monitor, `value`, moment) VALUES (?, ?, ?)", monitor, v2, m2);
        return monitor;
    }

    /**
     * Seed {@code count} float samples at {@code stepSeconds} spacing (batched —
     * thousands of rows). Monitor id is unknown to t_probe (falls back to
     * t_sample_float); main datasource inserts roll back with the test.
     */
    private int seedFloatSamplesBatch(int count, LocalDateTime start, long stepSeconds) {
        int monitor = 97000 + (int) (System.nanoTime() % 1000);
        jdbc.update("DELETE FROM t_sample_float WHERE monitor = ?", monitor);
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            batch.add(new Object[]{monitor, (double) (i % 50) + 10.0, start.plusSeconds(i * stepSeconds)});
        }
        jdbc.batchUpdate("INSERT INTO t_sample_float (monitor, `value`, moment) VALUES (?, ?, ?)", batch);
        return monitor;
    }

    private static final String INSERT_PROBE =
            "INSERT INTO t_probe (id, name, caption, parent, unit, time_interval, saving_interval, monitor_kind, catalog, min_value, max_value, warn_cond) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_CONTROL =
            "INSERT INTO t_control (id, name, caption, parent, unit, time_interval, saving_interval, catalog, min_value, max_value, warn_cond) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private int createProbe(DataType dataType, String interval) {
        int id = 90000 + (int) (System.nanoTime() % 1000);
        String name = "TestProbe-" + System.nanoTime();
        int monitorKind = dataType != null ? dataType.ordinal() : 0;
        jdbc.update(INSERT_PROBE, id, name, "Test Probe", 0, "C", interval, interval, monitorKind, (short) 1, null, null, null);
        return id;
    }

    private int createProbeWithNullDataType(String interval) {
        int id = 90000 + (int) (System.nanoTime() % 1000);
        String name = "TestProbe-" + System.nanoTime();
        jdbc.update(INSERT_PROBE, id, name, "Test Probe", 0, "C", interval, interval, null, (short) 1, null, null, null);
        return id;
    }

    private int createProbeWithNullInterval(DataType dataType) {
        int id = 90000 + (int) (System.nanoTime() % 1000);
        String name = "TestProbe-" + System.nanoTime();
        int monitorKind = dataType != null ? dataType.ordinal() : 0;
        jdbc.update(INSERT_PROBE, id, name, "Test Probe", 0, "C", null, null, monitorKind, (short) 1, null, null, null);
        return id;
    }

    /**
     * Create a probe with explicit threshold values for testing markLine rendering.
     * First two params match createProbe() signature for compatibility; remaining
     * params supply min_value, max_value, warn_cond.
     */
    private int createProbeWithThresholds(DataType dataType, String interval,
                                           Float minValue, Float maxValue, String warnCond) {
        int id = 90000 + (int) (System.nanoTime() % 1000);
        String name = "TestProbeThr-" + System.nanoTime();
        int monitorKind = dataType != null ? dataType.ordinal() : 0;
        jdbc.update(INSERT_PROBE, id, name, "Test Probe Thr", 0, "C", interval, interval, monitorKind, (short) 1, minValue, maxValue, warnCond);
        return id;
    }

    private int createControl(String interval) {
        int id = 91000 + (int) (System.nanoTime() % 1000);
        String name = "TestControl-" + System.nanoTime();
        jdbc.update(INSERT_CONTROL, id, name, "Test Control", 0, "%", interval, interval, (short) 1, null, null, null);
        return id;
    }

    private int createControlWithThresholds(String interval, Float minValue, Float maxValue, String warnCond) {
        int id = 91000 + (int) (System.nanoTime() % 1000);
        String name = "TestControlThr-" + System.nanoTime();
        jdbc.update(INSERT_CONTROL, id, name, "Test Control Thr", 0, "%", interval, interval, (short) 1, minValue, maxValue, warnCond);
        return id;
    }
}
