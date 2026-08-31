package com.systar.ops.statistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for trend chart data queries.
 */
@Mapper
public interface TrendMapper {

    @Select("SELECT moment, `value` FROM ${tableName} "
            + "WHERE monitor = #{monitorId} AND moment BETWEEN #{start} AND #{end} "
            + "ORDER BY moment LIMIT #{limit}")
    List<Map<String, Object>> findRawSamples(
            @Param("tableName") String tableName,
            @Param("monitorId") int monitorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit);

    @SelectProvider(type = TrendSqlProvider.class, method = "aggregateOHLC")
    List<Map<String, Object>> aggregateOHLC(
            @Param("tableName") String tableName,
            @Param("monitorId") int monitorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("bucketExpr") String bucketExpr);

    @Select("SELECT bucket_start, bucket_end, avg_val, max_val, min_val, sample_count "
            + "FROM t_monitor_stats "
            + "WHERE monitor = #{monitorId} AND granularity = #{granularity} "
            + "AND bucket_start BETWEEN #{start} AND #{end} "
            + "ORDER BY bucket_start")
    List<Map<String, Object>> findStats(
            @Param("monitorId") int monitorId,
            @Param("granularity") int granularity,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
