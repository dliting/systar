package com.systar.ops.statistics.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class TrendBucketExprTest {

    private static final String H2    = "h2";
    private static final String MYSQL = "mysql";

    // ======================== HOUR expressions ========================

    @Nested
    @DisplayName("HOUR granularity")
    class Hour {

        @Test
        @DisplayName("H2 HOUR expression")
        void h2HourExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("HOUR", H2, true))
                    .isEqualTo("FORMATDATETIME(moment, 'yyyy-MM-dd HH:00:00')");
        }

        @Test
        @DisplayName("MySQL HOUR expression")
        void mysqlHourExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("HOUR", MYSQL, true))
                    .isEqualTo("DATE_FORMAT(moment, '%Y-%m-%d %H:00:00')");
        }

        @Test
        @DisplayName("HOUR with custom column name")
        void customColumnH2() {
            assertThat(TrendBucketExpr.getColumnBucketExpr("HOUR", H2, true, "bucket_start"))
                    .isEqualTo("FORMATDATETIME(bucket_start, 'yyyy-MM-dd HH:00:00')");
        }
    }

    // ======================== DAY expressions ========================

    @Nested
    @DisplayName("DAY granularity")
    class Day {

        @Test
        @DisplayName("H2 DAY expression")
        void h2DayExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("DAY", H2, true))
                    .isEqualTo("FORMATDATETIME(moment, 'yyyy-MM-dd 00:00:00')");
        }

        @Test
        @DisplayName("MySQL DAY expression")
        void mysqlDayExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("DAY", MYSQL, true))
                    .isEqualTo("DATE_FORMAT(moment, '%Y-%m-%d 00:00:00')");
        }

        @Test
        @DisplayName("DAY with custom column name")
        void customColumnMySQL() {
            assertThat(TrendBucketExpr.getColumnBucketExpr("DAY", MYSQL, true, "my_col"))
                    .isEqualTo("DATE_FORMAT(my_col, '%Y-%m-%d 00:00:00')");
        }
    }

    // ======================== WEEK expressions ========================

    @Nested
    @DisplayName("WEEK granularity with Monday start")
    class WeekMonday {

        @Test
        @DisplayName("H2 WEEK Monday start expression")
        void h2WeekMondayExpr() {
            String expr = TrendBucketExpr.getBucketExpr("WEEK", H2, true);
            assertThat(expr).contains("FORMATDATETIME");
            assertThat(expr).contains("DAY_OF_WEEK(moment)");
            assertThat(expr).contains("'yyyy-MM-dd 00:00:00'");
            assertThat(expr).contains("CASE WHEN");
        }

        @Test
        @DisplayName("MySQL WEEK Monday start expression")
        void mysqlWeekMondayExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("WEEK", MYSQL, true))
                    .isEqualTo("DATE_FORMAT(DATE_SUB(moment, INTERVAL WEEKDAY(moment) DAY), '%Y-%m-%d 00:00:00')");
        }

        @Test
        @DisplayName("WEEK Monday with custom column")
        void customColumn() {
            String expr = TrendBucketExpr.getColumnBucketExpr("WEEK", MYSQL, true, "ts");
            assertThat(expr)
                    .isEqualTo("DATE_FORMAT(DATE_SUB(ts, INTERVAL WEEKDAY(ts) DAY), '%Y-%m-%d 00:00:00')");
        }
    }

    @Nested
    @DisplayName("WEEK granularity with Sunday start")
    class WeekSunday {

        @Test
        @DisplayName("H2 WEEK Sunday start expression")
        void h2WeekSundayExpr() {
            String expr = TrendBucketExpr.getBucketExpr("WEEK", H2, false);
            assertThat(expr).contains("FORMATDATETIME");
            assertThat(expr).contains("DAY_OF_WEEK(moment) - 1");
            assertThat(expr).doesNotContain("CASE WHEN");
        }

        @Test
        @DisplayName("MySQL WEEK Sunday start expression")
        void mysqlWeekSundayExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("WEEK", MYSQL, false))
                    .isEqualTo("DATE_FORMAT(DATE_SUB(moment, INTERVAL DAYOFWEEK(moment) - 1 DAY), '%Y-%m-%d 00:00:00')");
        }
    }

    // ======================== MONTH expressions ========================

    @Nested
    @DisplayName("MONTH granularity")
    class Month {

        @Test
        @DisplayName("H2 MONTH expression")
        void h2MonthExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("MONTH", H2, true))
                    .isEqualTo("FORMATDATETIME(moment, 'yyyy-MM-01 00:00:00')");
        }

        @Test
        @DisplayName("MySQL MONTH expression")
        void mysqlMonthExpr() {
            assertThat(TrendBucketExpr.getBucketExpr("MONTH", MYSQL, true))
                    .isEqualTo("DATE_FORMAT(moment, '%Y-%m-01 00:00:00')");
        }

        @Test
        @DisplayName("MONTH with custom column name")
        void customColumnH2() {
            assertThat(TrendBucketExpr.getColumnBucketExpr("MONTH", H2, true, "bucket_start"))
                    .isEqualTo("FORMATDATETIME(bucket_start, 'yyyy-MM-01 00:00:00')");
        }
    }

    // ======================== Edge cases ========================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Unknown granularity throws IllegalArgumentException")
        void unknownGranularityThrows() {
            assertThatThrownBy(() -> TrendBucketExpr.getBucketExpr("YEAR", H2, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported granularity: YEAR");
        }

        @Test
        @DisplayName("getColumnBucketExpr with unknown granularity also throws")
        void unknownGranularityColumnThrows() {
            assertThatThrownBy(() ->
                    TrendBucketExpr.getColumnBucketExpr("QUARTER", MYSQL, true, "col"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported granularity: QUARTER");
        }

        @Test
        @DisplayName("Null dbType defaults to MySQL expressions")
        void nullDbTypeDefaultsToMySQL() {
            assertThat(TrendBucketExpr.getBucketExpr("DAY", null, true))
                    .isEqualTo("DATE_FORMAT(moment, '%Y-%m-%d 00:00:00')");
        }

        @Test
        @DisplayName("Week start flag is respected independently for each call")
        void weekStartFlagIndependent() {
            String monday = TrendBucketExpr.getBucketExpr("WEEK", H2, true);
            String sunday = TrendBucketExpr.getBucketExpr("WEEK", H2, false);

            assertThat(monday).isNotEqualTo(sunday);
            assertThat(monday).contains("CASE WHEN");
            assertThat(sunday).doesNotContain("CASE WHEN");
        }
    }
}
