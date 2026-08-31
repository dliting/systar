package com.systar.ops.statistics.mapper;

/**
 * Generates dialect-specific SQL expressions for time bucketing.
 */
public final class TrendBucketExpr {

    private static final String DB_MYSQL = "mysql";
    private static final String DB_H2 = "h2";

    private TrendBucketExpr() {}

    /**
     * Returns a SQL expression that truncates a DATETIME/TIMESTAMP to the
     * start of the given granularity bucket.
     *
     * @param granularity one of HOUR, DAY, WEEK, MONTH
     * @param dbType      database type from {@code DatabaseDialect#getDatabaseType()}
     * @param weekStartMonday true = Monday start, false = Sunday start
     */
    public static String getBucketExpr(String granularity, String dbType, boolean weekStartMonday) {
        return buildExpr(granularity, dbType, weekStartMonday, "moment");
    }

    /**
     * Like {@link #getBucketExpr} but uses a custom timestamp column name
     * instead of "moment". Useful for cascading where the source column is
     * already an aggregated bucket_start.
     */
    public static String getColumnBucketExpr(String granularity, String dbType,
                                              boolean weekStartMonday, String columnName) {
        return buildExpr(granularity, dbType, weekStartMonday, columnName);
    }

    private static String buildExpr(String granularity, String dbType,
                                    boolean weekStartMonday, String col) {
        return switch (granularity) {
            case "HOUR"  -> hourExpr(dbType, col);
            case "DAY"   -> dayExpr(dbType, col);
            case "WEEK"  -> weekExpr(dbType, weekStartMonday, col);
            case "MONTH" -> monthExpr(dbType, col);
            default -> throw new IllegalArgumentException("Unsupported granularity: " + granularity);
        };
    }

    private static String hourExpr(String dbType, String col) {
        if (DB_H2.equals(dbType)) {
            return "FORMATDATETIME(" + col + ", 'yyyy-MM-dd HH:00:00')";
        }
        return "DATE_FORMAT(" + col + ", '%Y-%m-%d %H:00:00')";
    }

    private static String dayExpr(String dbType, String col) {
        if (DB_H2.equals(dbType)) {
            return "FORMATDATETIME(" + col + ", 'yyyy-MM-dd 00:00:00')";
        }
        return "DATE_FORMAT(" + col + ", '%Y-%m-%d 00:00:00')";
    }

    private static String weekExpr(String dbType, boolean weekStartMonday, String col) {
        if (DB_H2.equals(dbType)) {
            if (weekStartMonday) {
                return "FORMATDATETIME(DATEADD('DAY', "
                    + "-CASE WHEN DAY_OF_WEEK(" + col + ") = 1 THEN 6 ELSE DAY_OF_WEEK(" + col + ") - 2 END, "
                    + "CAST(" + col + " AS DATE)), 'yyyy-MM-dd 00:00:00')";
            }
            return "FORMATDATETIME(DATEADD('DAY', "
                + "-(DAY_OF_WEEK(" + col + ") - 1), "
                + "CAST(" + col + " AS DATE)), 'yyyy-MM-dd 00:00:00')";
        }
        if (weekStartMonday) {
            return "DATE_FORMAT(DATE_SUB(" + col + ", INTERVAL WEEKDAY(" + col + ") DAY), '%Y-%m-%d 00:00:00')";
        }
        return "DATE_FORMAT(DATE_SUB(" + col + ", INTERVAL DAYOFWEEK(" + col + ") - 1 DAY), '%Y-%m-%d 00:00:00')";
    }

    private static String monthExpr(String dbType, String col) {
        if (DB_H2.equals(dbType)) {
            return "FORMATDATETIME(" + col + ", 'yyyy-MM-01 00:00:00')";
        }
        return "DATE_FORMAT(" + col + ", '%Y-%m-01 00:00:00')";
    }
}
