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
        return "SELECT DISTINCT "
                + "bucket, "
                + "AVG(" + VALUE_COL + ") OVER w_agg AS avg_val, "
                + "MAX(" + VALUE_COL + ") OVER w_agg AS max_val, "
                + "MIN(" + VALUE_COL + ") OVER w_agg AS min_val, "
                + "COUNT(*) OVER w_agg AS sample_count "
                + "FROM ("
                + "  SELECT moment, " + VALUE_COL + ", ${bucketExpr} AS bucket "
                + "  FROM ${tableName} "
                + "  WHERE monitor = #{monitorId} "
                + "  AND moment BETWEEN #{start} AND #{end}"
                + ") raw "
                + "WINDOW w_agg AS (PARTITION BY bucket) "
                + "ORDER BY bucket";
    }
}
