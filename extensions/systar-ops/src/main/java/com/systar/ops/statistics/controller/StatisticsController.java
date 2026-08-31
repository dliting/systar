package com.systar.ops.statistics.controller;

import com.systar.ops.statistics.model.*;
import com.systar.ops.statistics.service.StatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/alarm")
    public AlarmStatsVO getAlarmStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer spaceId,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, deviceId, spaceId, granularity);
        return statisticsService.getAlarmStats(query);
    }

    @GetMapping("/work-order")
    public WorkOrderStatsVO getWorkOrderStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer spaceId,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, deviceId, spaceId, granularity);
        return statisticsService.getWorkOrderStats(query);
    }

    @GetMapping("/inspection")
    public InspectionStatsVO getInspectionStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer spaceId,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, deviceId, spaceId, granularity);
        return statisticsService.getInspectionStats(query);
    }

    @GetMapping("/device-runtime")
    public DeviceRuntimeVO getDeviceRuntimeStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer spaceId,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, deviceId, spaceId, granularity);
        return statisticsService.getDeviceRuntimeStats(query);
    }

    @GetMapping("/maintenance")
    public MaintenanceStatsVO getMaintenanceStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer spaceId,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, deviceId, spaceId, granularity);
        return statisticsService.getMaintenanceStats(query);
    }

    @GetMapping("/dashboard")
    public DashboardVO getDashboard() {
        return statisticsService.getDashboardData();
    }

    @GetMapping("/alarm-detail")
    public Map<String, Object> getAlarmDetail(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam int level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, null, null, "DAY");
        return statisticsService.getAlarmDetail(query, level, page, size);
    }

    @GetMapping("/workorder-detail")
    public Map<String, Object> getWorkOrderDetail(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, null, null, "DAY");
        return statisticsService.getWorkOrderDetail(query, status, page, size);
    }

    @GetMapping("/device-history")
    public Map<String, Object> getDeviceHistory(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam int deviceId) {
        StatisticsQuery query = new StatisticsQuery(startDate, endDate, null, null, "DAY");
        return statisticsService.getDeviceHistory(query, deviceId);
    }
}
