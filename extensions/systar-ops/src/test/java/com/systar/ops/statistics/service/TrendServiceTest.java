package com.systar.ops.statistics.service;

import com.systar.monitor.asset.type.DataType;
import com.systar.ops.statistics.mapper.TrendMapper;
import com.systar.ops.statistics.model.TrendBarVO;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 15, 0, 0, 0);

    // ==================== computeMA ====================

    @Nested
    class ComputeMA {

        @Test
        void knownInput_period3_returnsCorrectAverages() {
            List<TrendBarVO> bars = List.of(
                    bar(10, 10, 10, 1),
                    bar(20, 20, 20, 1),
                    bar(30, 30, 30, 1),
                    bar(40, 40, 40, 1),
                    bar(50, 50, 50, 1)
            );

            List<Double> result = trendService.computeMA(bars, 3);

            assertThat(result).hasSize(5);
            assertThat(result.get(0)).isNull();
            assertThat(result.get(1)).isNull();
            assertThat(result.get(2)).isEqualTo(20.0);
            assertThat(result.get(3)).isEqualTo(30.0);
            assertThat(result.get(4)).isEqualTo(40.0);
        }

        @Test
        void singleElement_period5_returnsNull() {
            List<TrendBarVO> bars = List.of(bar(42, 42, 42, 1));

            List<Double> result = trendService.computeMA(bars, 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isNull();
        }

        @Test
        void emptyList_returnsEmptyResult() {
            List<TrendBarVO> bars = List.of();

            List<Double> result = trendService.computeMA(bars, 3);

            assertThat(result).isEmpty();
        }

        @Test
        void allIdenticalValues_returnsConstantMA() {
            List<TrendBarVO> bars = List.of(
                    bar(5, 5, 5, 1),
                    bar(5, 5, 5, 1),
                    bar(5, 5, 5, 1)
            );

            List<Double> result = trendService.computeMA(bars, 2);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isNull();
            assertThat(result.get(1)).isEqualTo(5.0);
            assertThat(result.get(2)).isEqualTo(5.0);
        }

        @Test
        void periodEqualToListSize_returnsSingleValue() {
            List<TrendBarVO> bars = List.of(
                    bar(10, 15, 5, 1),
                    bar(20, 25, 15, 1),
                    bar(30, 35, 25, 1)
            );

            // avg values: 10, 20, 30 → MA3 = (10+20+30)/3 = 20.0
            List<Double> result = trendService.computeMA(bars, 3);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isNull();
            assertThat(result.get(1)).isNull();
            assertThat(result.get(2)).isEqualTo(20.0);
        }

        @Test
        void largePeriod_returnsAllNulls() {
            List<TrendBarVO> bars = List.of(
                    bar(1, 1, 1, 1),
                    bar(2, 2, 2, 1)
            );

            List<Double> result = trendService.computeMA(bars, 5);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(v -> v == null);
        }
    }

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

    // ==================== buildSummary ====================

    @Nested
    class BuildSummary {

        @Test
        void multipleBars_returnsCorrectSummary() {
            List<TrendBarVO> bars = List.of(
                    new TrendBarVO(BASE_TIME, 20, 50, 5, 100),
                    new TrendBarVO(BASE_TIME.plusHours(1), 30, 45, 8, 150),
                    new TrendBarVO(BASE_TIME.plusHours(2), 25, 60, 3, 200)
            );

            TrendSummaryVO summary = trendService.buildSummary(bars);

            assertThat(summary.currentValue()).isEqualTo(25.0);
            assertThat(summary.periodMax()).isEqualTo(60.0);
            assertThat(summary.periodMin()).isEqualTo(3.0);
            assertThat(summary.totalSamples()).isEqualTo(450);
        }

        @Test
        void singleBar_allValuesMatch() {
            TrendBarVO single = new TrendBarVO(BASE_TIME, 42.5, 42.5, 42.5, 10);

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

        @Test
        void thirtyOneDays_returnsHour() {
            assertThat(trendService.determineGranularity(2_678_400L)).isEqualTo("HOUR");
        }

        @Test
        void thirtyOneDaysPlusOneSecond_returnsDay() {
            assertThat(trendService.determineGranularity(2_678_401L)).isEqualTo("DAY");
        }

        @Test
        void oneHundredEightyDays_returnsDay() {
            assertThat(trendService.determineGranularity(15_552_000L)).isEqualTo("DAY");
        }

        @Test
        void oneHundredEightyDaysPlusOneSecond_returnsWeek() {
            assertThat(trendService.determineGranularity(15_552_001L)).isEqualTo("WEEK");
        }

        @Test
        void twoYears_returnsWeek() {
            assertThat(trendService.determineGranularity(63_072_000L)).isEqualTo("WEEK");
        }

        @Test
        void twoYearsPlusOneSecond_returnsMonth() {
            assertThat(trendService.determineGranularity(63_072_001L)).isEqualTo("MONTH");
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
            assertThat(response.getIntradayPoints()).isNotNull();
        }

        @Test
        void probeWith30mInterval_selectsHour() {
            int probeId = createProbe(DataType.FLOAT, "00:30:00");

            TrendResponseVO response = trendService.getDefaultView(probeId, "PROBE");

            assertThat(response.getGranularity()).isEqualTo("HOUR");
            assertThat(response.getDataPoints()).isNotNull();
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
            LocalDateTime start = LocalDateTime.now().minusHours(1);
            LocalDateTime end = LocalDateTime.now();
            TrendQuery query = new TrendQuery(99998, "PROBE", start, end, "HOUR");

            TrendResponseVO response = trendService.getTrendData(query);

            assertThat(response.getGranularity()).isEqualTo("HOUR");
            assertThat(response.getDataPoints()).isEmpty();
            assertThat(response.getAvg5()).isEmpty();
            assertThat(response.getAvg10()).isEmpty();
            assertThat(response.getAvg20()).isEmpty();
        }

        @Test
        void nonExistentProbe_returnsEmptyResponseForDay() {
            LocalDateTime start = LocalDateTime.now().minusDays(2);
            LocalDateTime end = LocalDateTime.now();
            TrendQuery query = new TrendQuery(99998, "PROBE", start, end, "DAY");

            TrendResponseVO response = trendService.getTrendData(query);

            assertThat(response.getGranularity()).isEqualTo("DAY");
            assertThat(response.getDataPoints()).isEmpty();
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

    /** Create a TrendBarVO with identical max/min for simple test cases. */
    private static TrendBarVO bar(double avg, double max, double min, long sampleCount) {
        return new TrendBarVO(BASE_TIME.plusHours(0), avg, max, min, sampleCount);
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
