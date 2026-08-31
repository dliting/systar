package com.systar.ops.statistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    // ==================== Alarm Statistics ====================

    @Select("SELECT warn_id, COUNT(*) as cnt FROM t_alarm_message " +
            "WHERE alarm_time BETWEEN #{start} AND #{end} GROUP BY warn_id")
    List<Map<String, Object>> countAlarmsByLevel(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    @Select("SELECT DATE(alarm_time) as period, COUNT(*) as cnt FROM t_alarm_message " +
            "WHERE alarm_time BETWEEN #{start} AND #{end} " +
            "GROUP BY DATE(alarm_time) ORDER BY period")
    List<Map<String, Object>> countAlarmsByDay(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) as total, " +
            "COALESCE(SUM(CASE WHEN state = 2 THEN 1 ELSE 0 END), 0) as handled " +
            "FROM t_alarm_message WHERE alarm_time BETWEEN #{start} AND #{end}")
    Map<String, Object> alarmHandlingRate(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    @Select("SELECT " +
            "  COALESCE(SUM(CASE WHEN alarm_time BETWEEN #{curStart} AND #{curEnd} THEN 1 ELSE 0 END), 0) as curCount, " +
            "  COALESCE(SUM(CASE WHEN alarm_time BETWEEN #{prevStart} AND #{prevEnd} THEN 1 ELSE 0 END), 0) as prevCount " +
            "FROM t_alarm_message " +
            "WHERE alarm_time BETWEEN #{prevStart} AND #{curEnd}")
    Map<String, Object> alarmPeriodComparison(@Param("curStart") LocalDateTime curStart,
                                               @Param("curEnd") LocalDateTime curEnd,
                                               @Param("prevStart") LocalDateTime prevStart,
                                               @Param("prevEnd") LocalDateTime prevEnd);

    @Select("SELECT COUNT(*) FROM t_alarm_message " +
            "WHERE alarm_time BETWEEN #{start} AND #{end} AND state = 1")
    long countPendingAlarms(@Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    @Select("SELECT e.asset_id as assetId, COUNT(*) as cnt " +
            "FROM t_alarm_message m JOIN t_error_message_log e ON m.log_id = e.id " +
            "WHERE m.alarm_time BETWEEN #{start} AND #{end} " +
            "GROUP BY e.asset_id ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> topAlarmAssets(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("limit") int limit);

    // ==================== Work Order Statistics ====================

    @Select("SELECT status, COUNT(*) as cnt FROM t_work_order " +
            "WHERE created_at BETWEEN #{start} AND #{end} " +
            "GROUP BY status")
    List<Map<String, Object>> countWorkOrdersByStatus(@Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    @Select("SELECT created_at as createdAt, closed_at as closedAt, due_time as dueTime " +
            "FROM t_work_order WHERE status = 'CLOSED' " +
            "AND created_at BETWEEN #{start} AND #{end}")
    List<Map<String, Object>> listClosedWorkOrders(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    @Select("SELECT created_at as createdAt, due_time as dueTime " +
            "FROM t_work_order " +
            "WHERE status IN ('CREATED', 'ASSIGNED', 'PROCESSING')")
    List<Map<String, Object>> listOpenWorkOrders();

    @Select("SELECT DATE(created_at) as period, COUNT(*) as cnt FROM t_work_order " +
            "WHERE created_at BETWEEN #{start} AND #{end} " +
            "GROUP BY DATE(created_at) ORDER BY period")
    List<Map<String, Object>> countWorkOrdersByDay(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    @Select("SELECT " +
            "  COALESCE(SUM(CASE WHEN created_at BETWEEN #{curStart} AND #{curEnd} THEN 1 ELSE 0 END), 0) as curCount, " +
            "  COALESCE(SUM(CASE WHEN created_at BETWEEN #{prevStart} AND #{prevEnd} THEN 1 ELSE 0 END), 0) as prevCount " +
            "FROM t_work_order " +
            "WHERE created_at BETWEEN #{prevStart} AND #{curEnd}")
    Map<String, Object> workOrderPeriodComparison(@Param("curStart") LocalDateTime curStart,
                                                   @Param("curEnd") LocalDateTime curEnd,
                                                   @Param("prevStart") LocalDateTime prevStart,
                                                   @Param("prevEnd") LocalDateTime prevEnd);

    // ==================== Inspection Statistics ====================

    @Select("SELECT status, COUNT(*) as cnt FROM t_inspection_task " +
            "WHERE scheduled_time BETWEEN #{start} AND #{end} " +
            "GROUP BY status")
    List<Map<String, Object>> countInspectionsByStatus(@Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM t_inspection_result ir " +
            "JOIN t_inspection_task it ON ir.task_id = it.id " +
            "WHERE ir.check_result = 'ABNORMAL' " +
            "AND it.scheduled_time BETWEEN #{start} AND #{end}")
    long countAnomalies(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

    @Select("SELECT DATE(ir.created_at) as period, COUNT(*) as cnt " +
            "FROM t_inspection_result ir " +
            "JOIN t_inspection_task it ON ir.task_id = it.id " +
            "WHERE ir.check_result = 'ABNORMAL' " +
            "AND it.scheduled_time BETWEEN #{start} AND #{end} " +
            "GROUP BY DATE(ir.created_at) ORDER BY period")
    List<Map<String, Object>> countAnomaliesByDay(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Select("SELECT DATE(scheduled_time) as period, COUNT(*) as cnt " +
            "FROM t_inspection_task " +
            "WHERE scheduled_time BETWEEN #{start} AND #{end} " +
            "GROUP BY DATE(scheduled_time) ORDER BY period")
    List<Map<String, Object>> countInspectionsByDay(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    @Select("SELECT " +
            "  COALESCE(SUM(CASE WHEN scheduled_time BETWEEN #{curStart} AND #{curEnd} THEN 1 ELSE 0 END), 0) as curCount, " +
            "  COALESCE(SUM(CASE WHEN scheduled_time BETWEEN #{prevStart} AND #{prevEnd} THEN 1 ELSE 0 END), 0) as prevCount " +
            "FROM t_inspection_task " +
            "WHERE scheduled_time BETWEEN #{prevStart} AND #{curEnd}")
    Map<String, Object> inspectionPeriodComparison(@Param("curStart") LocalDateTime curStart,
                                                    @Param("curEnd") LocalDateTime curEnd,
                                                    @Param("prevStart") LocalDateTime prevStart,
                                                    @Param("prevEnd") LocalDateTime prevEnd);

    // ==================== Device Runtime Statistics ====================

    @Select("SELECT p.id " +
            "FROM t_probe p JOIN t_service s ON p.parent = s.id " +
            "WHERE s.parent = #{deviceId}")
    List<Integer> findProbeIdsByDevice(@Param("deviceId") int deviceId);

    @Select("<script>" +
            "SELECT COUNT(DISTINCT DATE(moment)) as onlineDays FROM (" +
            "  SELECT moment FROM t_sample_float" +
            "  WHERE monitor IN " +
            "  <foreach collection='monitorIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "  AND moment BETWEEN #{start} AND #{end}" +
            "  UNION ALL " +
            "  SELECT moment FROM t_sample_int" +
            "  WHERE monitor IN " +
            "  <foreach collection='monitorIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "  AND moment BETWEEN #{start} AND #{end}" +
            "  UNION ALL " +
            "  SELECT moment FROM t_sample_boolean" +
            "  WHERE monitor IN " +
            "  <foreach collection='monitorIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "  AND moment BETWEEN #{start} AND #{end}" +
            ") t" +
            "</script>")
    long countDistinctSampleDays(@Param("monitorIds") List<Integer> monitorIds,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    @Select("SELECT id, name, lifecycle_status as lifecycleStatus FROM t_device " +
            "WHERE lifecycle_status = 'IN_SERVICE'")
    List<Map<String, Object>> findInServiceDevices();

    // ==================== Maintenance Statistics ====================

    @Select("SELECT type, COUNT(*) as cnt, COALESCE(SUM(cost), 0) as totalCost " +
            "FROM t_maintenance_record " +
            "WHERE performed_at BETWEEN #{start} AND #{end} " +
            "GROUP BY type")
    List<Map<String, Object>> countMaintenanceByType(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(cost), 0) as total FROM t_maintenance_record " +
            "WHERE performed_at BETWEEN #{start} AND #{end}")
    Map<String, Object> sumMaintenanceCost(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    @Select("SELECT DATE(performed_at) as period, COUNT(*) as cnt " +
            "FROM t_maintenance_record " +
            "WHERE performed_at BETWEEN #{start} AND #{end} " +
            "GROUP BY DATE(performed_at) ORDER BY period")
    List<Map<String, Object>> countMaintenanceByDay(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    @Select("SELECT " +
            "  COALESCE(SUM(CASE WHEN performed_at BETWEEN #{curStart} AND #{curEnd} THEN 1 ELSE 0 END), 0) as curCount, " +
            "  COALESCE(SUM(CASE WHEN performed_at BETWEEN #{prevStart} AND #{prevEnd} THEN 1 ELSE 0 END), 0) as prevCount " +
            "FROM t_maintenance_record " +
            "WHERE performed_at BETWEEN #{prevStart} AND #{curEnd}")
    Map<String, Object> maintenancePeriodComparison(@Param("curStart") LocalDateTime curStart,
                                                     @Param("curEnd") LocalDateTime curEnd,
                                                     @Param("prevStart") LocalDateTime prevStart,
                                                     @Param("prevEnd") LocalDateTime prevEnd);

    // ==================== Dashboard ====================

    @Select("SELECT COUNT(*) FROM t_alarm_message " +
            "WHERE DATE(alarm_time) = CURRENT_DATE()")
    int countTodayAlarms();

    @Select("SELECT COUNT(*) FROM t_inspection_task " +
            "WHERE DATE(scheduled_time) = CURRENT_DATE()")
    int countTodayInspections();

    @Select("SELECT COUNT(*) FROM t_inspection_task " +
            "WHERE DATE(scheduled_time) = CURRENT_DATE() AND status = 'COMPLETED'")
    int countTodayCompletedInspections();

    // ==================== Drill-Down Queries ====================

    @Select("SELECT m.id, m.caption, m.alarm_time as alarmTime, m.state, m.recovered, " +
            "m.warn_id as warnId, e.asset_id as assetId, e.monitor_name as monitorName " +
            "FROM t_alarm_message m JOIN t_error_message_log e ON m.log_id = e.id " +
            "WHERE m.alarm_time BETWEEN #{start} AND #{end} " +
            "AND m.warn_id = #{level} " +
            "ORDER BY m.alarm_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listAlarmsByLevel(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("level") int level,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    @Select("SELECT COUNT(*) FROM t_alarm_message m " +
            "JOIN t_error_message_log e ON m.log_id = e.id " +
            "WHERE m.alarm_time BETWEEN #{start} AND #{end} AND m.warn_id = #{level}")
    Long countAlarmsByLevelFiltered(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     @Param("level") int level);

    @Select("SELECT wo.id, wo.order_no as orderNo, wo.title, wo.status, wo.priority, " +
            "wo.type, wo.created_at as createdAt, wo.closed_at as closedAt, " +
            "wo.due_time as dueTime, wo.device_id as deviceId " +
            "FROM t_work_order wo " +
            "WHERE wo.created_at BETWEEN #{start} AND #{end} " +
            "AND wo.status = #{status} " +
            "ORDER BY wo.created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listWorkOrdersByStatus(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("status") String status,
                                                      @Param("offset") int offset,
                                                      @Param("size") int size);

    @Select("SELECT COUNT(*) FROM t_work_order " +
            "WHERE created_at BETWEEN #{start} AND #{end} AND status = #{status}")
    Long countWorkOrdersByStatusFiltered(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("status") String status);

    @Select("SELECT COUNT(DISTINCT m.id) as alarmCount, " +
            "COUNT(DISTINCT mr.id) as maintenanceCount, " +
            "COALESCE(SUM(mr.cost), 0) as totalMaintenanceCost " +
            "FROM t_device d " +
            "LEFT JOIN t_error_message_log e ON e.asset_id IN (" +
            "  SELECT a.id FROM t_asset a WHERE a.device_id = d.id AND a.kind IN (3,4)" +
            ") " +
            "LEFT JOIN t_alarm_message m ON m.log_id = e.id " +
            "  AND m.alarm_time BETWEEN #{start} AND #{end} " +
            "LEFT JOIN t_maintenance_record mr ON mr.device_id = d.id " +
            "  AND mr.performed_at BETWEEN #{start} AND #{end} " +
            "WHERE d.id = #{deviceId} " +
            "GROUP BY d.id")
    Map<String, Object> getDeviceRuntimeHistory(@Param("deviceId") int deviceId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}
