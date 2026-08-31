package com.systar.ops.analysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AnalysisMapper {

    @Select("SELECT moment as ts, `value` as val FROM t_sample_float " +
            "WHERE monitor = #{monitorId} AND moment BETWEEN #{start} AND #{end} " +
            "ORDER BY moment")
    List<Map<String, Object>> getFloatHistory(@Param("monitorId") int monitorId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Select("SELECT moment as ts, `value` as val FROM t_sample_int " +
            "WHERE monitor = #{monitorId} AND moment BETWEEN #{start} AND #{end} " +
            "ORDER BY moment")
    List<Map<String, Object>> getIntHistory(@Param("monitorId") int monitorId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Select("SELECT d.id, d.name, d.health_index as healthIndex " +
            "FROM t_device d WHERE d.lifecycle_status = 'IN_SERVICE'")
    List<Map<String, Object>> listActiveDevices();

    @Select("SELECT COUNT(*) FROM t_alarm_message m " +
            "JOIN t_error_message_log e ON m.log_id = e.id " +
            "WHERE e.asset_id IN (SELECT a.id FROM t_asset a WHERE a.device_id = #{deviceId} AND a.kind IN (3,4)) " +
            "AND m.alarm_time BETWEEN #{start} AND #{end}")
    long countAlarmsForDevice(@Param("deviceId") int deviceId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM t_maintenance_record " +
            "WHERE device_id = #{deviceId} AND performed_at BETWEEN #{start} AND #{end}")
    long countMaintenanceForDevice(@Param("deviceId") int deviceId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Select("SELECT p.id FROM t_probe p JOIN t_service s ON p.parent = s.id " +
            "WHERE s.parent = #{deviceId}")
    List<Integer> findProbeIdsForDevice(@Param("deviceId") int deviceId);
}
