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

    // t_error_message_log.asset_id stores the MONITOR id (t_probe.id /
    // t_control.id) — bridge to t_asset via COALESCE(probe_id, control_id).
    // Monitor rows do NOT carry device_id; they hang off the device's asset row
    // via parent_id, so the device must be resolved through that row.
    @Select("SELECT COUNT(*) FROM t_alarm_message m " +
            "JOIN t_error_message_log e ON m.log_id = e.id " +
            "WHERE e.asset_id IN (SELECT COALESCE(a.probe_id, a.control_id) FROM t_asset a " +
            "JOIN t_asset da ON a.parent_id = da.id " +
            "WHERE da.kind = 1 AND da.device_id = #{deviceId} AND a.kind IN (3,4)) " +
            "AND m.alarm_time BETWEEN #{start} AND #{end}")
    long countAlarmsForDevice(@Param("deviceId") int deviceId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM t_maintenance_record " +
            "WHERE device_id = #{deviceId} AND performed_at BETWEEN #{start} AND #{end}")
    long countMaintenanceForDevice(@Param("deviceId") int deviceId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    // Accepts both probe→device (standard model, service via source column)
    // and probe→service→device layouts.
    @Select("SELECT p.id FROM t_probe p " +
            "LEFT JOIN t_service s ON p.parent = s.id " +
            "WHERE p.parent = #{deviceId} OR s.parent = #{deviceId}")
    List<Integer> findProbeIdsForDevice(@Param("deviceId") int deviceId);
}
