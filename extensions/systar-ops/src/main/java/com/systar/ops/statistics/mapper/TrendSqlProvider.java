package com.systar.ops.statistics.mapper;

import org.apache.ibatis.builder.annotation.ProviderMethodResolver;

import java.util.Map;

/**
 * Dynamic SQL provider for trend aggregation via window functions.
 * Result columns: bucket, avg_val, max_val, min_val, sample_count
 */
public class TrendSqlProvider implements ProviderMethodResolver {

    private static final String VALUE_COL = "`value`";

    public String aggregateOHLC(Map<String, Object> params) {
        // Two-level nesting: the inner SELECT ranks rows per bucket (rn/rdn),
        // so the outer window can read them as plain columns and derive the
        // first/last sample value (open_val/close_val) per bucket.
        return "SELECT DISTINCT bucket,"
                + " AVG(" + VALUE_COL + ") OVER w_agg AS avg_val,"
                + " MAX(" + VALUE_COL + ") OVER w_agg AS max_val,"
                + " MIN(" + VALUE_COL + ") OVER w_agg AS min_val,"
                + " COUNT(*) OVER w_agg AS sample_count,"
                + " MAX(CASE WHEN rn = 1 THEN " + VALUE_COL + " END) OVER w_agg AS open_val,"
                + " MAX(CASE WHEN rdn = 1 THEN " + VALUE_COL + " END) OVER w_agg AS close_val"
                + " FROM (SELECT moment, " + VALUE_COL + ", ${bucketExpr} AS bucket,"
                + "   ROW_NUMBER() OVER (PARTITION BY ${bucketExpr} ORDER BY moment ASC) AS rn,"
                + "   ROW_NUMBER() OVER (PARTITION BY ${bucketExpr} ORDER BY moment DESC) AS rdn"
                + "   FROM ${tableName} WHERE monitor = #{monitorId} AND moment BETWEEN #{start} AND #{end}) raw"
                + " WINDOW w_agg AS (PARTITION BY bucket) ORDER BY bucket";
    }
}
