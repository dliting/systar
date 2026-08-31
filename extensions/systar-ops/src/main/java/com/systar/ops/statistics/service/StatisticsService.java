package com.systar.ops.statistics.service;

import com.systar.monitor.asset.AssetKind;
import com.systar.ops.statistics.mapper.StatisticsMapper;
import com.systar.ops.statistics.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final StatisticsMapper statisticsMapper;
    private final JdbcTemplate jdbc;

    private static final int TOP_DEVICES_LIMIT = 10;
    private static final long AGING_24H = 24;
    private static final long AGING_72H = 72;
    private static final long AGING_7D = 168;

    public StatisticsService(StatisticsMapper statisticsMapper,
                             @Qualifier("mainJdbcTemplate") JdbcTemplate jdbc) {
        this.statisticsMapper = statisticsMapper;
        this.jdbc = jdbc;
    }

    // ==================== Alarm Statistics ====================

    public AlarmStatsVO getAlarmStats(StatisticsQuery query) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();

        AlarmStatsVO vo = new AlarmStatsVO();

        // Level distribution
        List<Map<String, Object>> levelRows = statisticsMapper.countAlarmsByLevel(start, end);
        Map<Integer, Long> byLevel = new LinkedHashMap<>();
        long total = 0;
        for (Map<String, Object> row : levelRows) {
            Number warnId = getNumber(row, "warn_id");
            Number cnt = getNumber(row, "cnt");
            int level = warnId != null ? warnId.intValue() : 0;
            long count = cnt != null ? cnt.longValue() : 0;
            byLevel.put(level, count);
            total += count;
        }
        vo.setByLevel(byLevel);
        vo.setTotalAlarms(total);

        // Pending alarms
        long pending = statisticsMapper.countPendingAlarms(start, end);
        vo.setPendingAlarms(pending);

        // Handling rate
        Map<String, Object> rateMap = statisticsMapper.alarmHandlingRate(start, end);
        long rateTotal = getNumber(rateMap, "total").longValue();
        long handled = getNumber(rateMap, "handled").longValue();
        vo.setHandlingRate(rateTotal > 0 ? (double) handled / rateTotal : 0);

        // Trend
        List<Map<String, Object>> dayRows = statisticsMapper.countAlarmsByDay(start, end);
        vo.setTrend(bucketByGranularity(dayRows, "period", "cnt", query.granularity()));

        // Period comparison (prevEnd = startDate exclusive)
        Map<String, Object> comp = statisticsMapper.alarmPeriodComparison(
                start, end,
                query.getPrevPeriodStart().atStartOfDay(),
                query.startDate().atStartOfDay());
        vo.setCurrentPeriodCount(getNumber(comp, "curCount").longValue());
        vo.setPrevPeriodCount(getNumber(comp, "prevCount").longValue());

        // Top alarm devices
        vo.setTopDevices(resolveTopAlarmDevices(start, end));

        return vo;
    }

    // ==================== Work Order Statistics ====================

    public WorkOrderStatsVO getWorkOrderStats(StatisticsQuery query) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();

        WorkOrderStatsVO vo = new WorkOrderStatsVO();

        // Status distribution
        List<Map<String, Object>> statusRows = statisticsMapper.countWorkOrdersByStatus(start, end);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> row : statusRows) {
            String status = (String) getValue(row, "status");
            Number cnt = getNumber(row, "cnt");
            byStatus.put(status != null ? status : "UNKNOWN", cnt != null ? cnt.longValue() : 0);
        }
        vo.setByStatus(byStatus);

        // MTTR & SLA from closed work orders
        List<Map<String, Object>> closed = statisticsMapper.listClosedWorkOrders(start, end);
        vo.setMttrHours(calculateMttr(closed));
        vo.setSlaComplianceRate(calculateSla(closed));

        // Aging distribution
        List<Map<String, Object>> open = statisticsMapper.listOpenWorkOrders();
        vo.setAgingDistribution(calculateAging(open));

        // Trend
        List<Map<String, Object>> dayRows = statisticsMapper.countWorkOrdersByDay(start, end);
        vo.setTrend(bucketByGranularity(dayRows, "period", "cnt", query.granularity()));

        // Period comparison
        Map<String, Object> comp = statisticsMapper.workOrderPeriodComparison(
                start, end,
                query.getPrevPeriodStart().atStartOfDay(),
                query.startDate().atStartOfDay());
        vo.setCurrentPeriodTotal(getNumber(comp, "curCount").longValue());
        vo.setPrevPeriodTotal(getNumber(comp, "prevCount").longValue());

        return vo;
    }

    // ==================== Inspection Statistics ====================

    public InspectionStatsVO getInspectionStats(StatisticsQuery query) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();

        InspectionStatsVO vo = new InspectionStatsVO();

        // Status counts
        List<Map<String, Object>> statusRows = statisticsMapper.countInspectionsByStatus(start, end);
        long total = 0, completed = 0;
        for (Map<String, Object> row : statusRows) {
            String status = (String) getValue(row, "status");
            long cnt = (getNumber(row, "cnt")).longValue();
            total += cnt;
            if ("COMPLETED".equals(status)) {
                completed = cnt;
            }
        }
        vo.setTotalTasks(total);
        vo.setCompletedTasks(completed);
        vo.setCompletionRate(total > 0 ? (double) completed / total : 0);

        // Anomalies
        long anomalyCount = statisticsMapper.countAnomalies(start, end);
        vo.setAnomalyCount(anomalyCount);
        vo.setAnomalyRate(total > 0 ? (double) anomalyCount / total : 0);

        // Trends
        List<Map<String, Object>> completionDay = statisticsMapper.countInspectionsByDay(start, end);
        vo.setCompletionTrend(bucketByGranularity(completionDay, "period", "cnt", query.granularity()));

        List<Map<String, Object>> anomalyDay = statisticsMapper.countAnomaliesByDay(start, end);
        vo.setAnomalyTrend(bucketByGranularity(anomalyDay, "period", "cnt", query.granularity()));

        // Period comparison
        Map<String, Object> comp = statisticsMapper.inspectionPeriodComparison(
                start, end,
                query.getPrevPeriodStart().atStartOfDay(),
                query.startDate().atStartOfDay());
        vo.setCurrentPeriodTotal(getNumber(comp, "curCount").longValue());
        vo.setPrevPeriodTotal(getNumber(comp, "prevCount").longValue());

        return vo;
    }

    // ==================== Device Runtime Statistics ====================

    public DeviceRuntimeVO getDeviceRuntimeStats(StatisticsQuery query) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();
        long totalDays = ChronoUnit.DAYS.between(query.startDate(), query.endDate()) + 1;

        DeviceRuntimeVO vo = new DeviceRuntimeVO();
        List<DeviceRuntimeVO.DeviceOnlineDetail> details = new ArrayList<>();

        // Get in-service devices
        List<Map<String, Object>> devices = statisticsMapper.findInServiceDevices();
        int onlineCount = 0;

        for (Map<String, Object> device : devices) {
            int deviceId = (getNumber(device, "id")).intValue();
            String deviceName = (String) getValue(device, "name");
            String lifecycleStatus = (String) getValue(device, "lifecycleStatus");

            // Only count IN_SERVICE devices for online rate
            if (!"IN_SERVICE".equals(lifecycleStatus)) {
                continue;
            }

            // Find probe IDs for this device
            List<Integer> probeIds = statisticsMapper.findProbeIdsByDevice(deviceId);
            if (probeIds.isEmpty()) {
                details.add(new DeviceRuntimeVO.DeviceOnlineDetail(
                        deviceId, deviceName, 0, totalDays, 0));
                continue;
            }

            long onlineDays = statisticsMapper.countDistinctSampleDays(probeIds, start, end);
            double rate = totalDays > 0 ? (double) onlineDays / totalDays : 0;
            if (rate > 0) {
                onlineCount++;
            }
            details.add(new DeviceRuntimeVO.DeviceOnlineDetail(
                    deviceId, deviceName, onlineDays, totalDays, rate));
        }

        vo.setTotalDevices(devices.size());
        vo.setOnlineDevices(onlineCount);
        vo.setAvailabilityRate(devices.isEmpty() ? 0 : (double) onlineCount / devices.size());
        vo.setDetails(details);

        return vo;
    }

    // ==================== Maintenance Statistics ====================

    public MaintenanceStatsVO getMaintenanceStats(StatisticsQuery query) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();

        MaintenanceStatsVO vo = new MaintenanceStatsVO();

        // By type
        List<Map<String, Object>> typeRows = statisticsMapper.countMaintenanceByType(start, end);
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, BigDecimal> costByType = new LinkedHashMap<>();
        long totalRecords = 0;
        for (Map<String, Object> row : typeRows) {
            String type = (String) getValue(row, "type");
            Number cnt = getNumber(row, "cnt");
            Number cost = getNumber(row, "totalCost");
            long c = cnt != null ? cnt.longValue() : 0;
            totalRecords += c;
            byType.put(type != null ? type : "OTHER", c);
            costByType.put(type != null ? type : "OTHER",
                    cost != null ? BigDecimal.valueOf(cost.doubleValue()) : BigDecimal.ZERO);
        }
        vo.setTotalRecords(totalRecords);
        vo.setByType(byType);
        vo.setCostByType(costByType);

        // Total cost
        Map<String, Object> costMap = statisticsMapper.sumMaintenanceCost(start, end);
        Number totalCostNum = getNumber(costMap, "total");
        vo.setTotalCost(totalCostNum != null
                ? BigDecimal.valueOf(totalCostNum.doubleValue()) : BigDecimal.ZERO);

        // Trend
        List<Map<String, Object>> dayRows = statisticsMapper.countMaintenanceByDay(start, end);
        vo.setFrequencyTrend(bucketByGranularity(dayRows, "period", "cnt", query.granularity()));

        // Period comparison
        Map<String, Object> comp = statisticsMapper.maintenancePeriodComparison(
                start, end,
                query.getPrevPeriodStart().atStartOfDay(),
                query.startDate().atStartOfDay());
        vo.setCurrentPeriodTotal(getNumber(comp, "curCount").longValue());
        vo.setPrevPeriodTotal(getNumber(comp, "prevCount").longValue());

        return vo;
    }

    // ==================== Dashboard ====================

    @Cacheable(value = "dashboard", key = "'daily'")
    public DashboardVO getDashboardData() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        LocalDate weekAgo = today.minusDays(7);

        DashboardVO vo = new DashboardVO();

        // Alarms summary
        int todayAlarms = statisticsMapper.countTodayAlarms();
        Map<String, Object> rateMap = statisticsMapper.alarmHandlingRate(
                weekAgo.atStartOfDay(), todayEnd);
        long rateTotal = getNumber(rateMap, "total").longValue();
        long handled = getNumber(rateMap, "handled").longValue();
        double handlingRate = rateTotal > 0 ? (double) handled / rateTotal : 0;
        List<Map<String, Object>> alarmDayRows = statisticsMapper.countAlarmsByDay(
                weekAgo.atStartOfDay(), todayEnd);
        vo.setAlarms(new DashboardVO.AlarmSummary(todayAlarms,
                bucketByGranularity(alarmDayRows, "period", "cnt", "DAY"), handlingRate));

        // Work orders summary
        Map<String, Long> woByStatus = new LinkedHashMap<>();
        List<Map<String, Object>> woStatusRows = statisticsMapper.countWorkOrdersByStatus(
                weekAgo.atStartOfDay(), todayEnd);
        long openCount = 0;
        for (Map<String, Object> row : woStatusRows) {
            String status = (String) getValue(row, "status");
            long cnt = (getNumber(row, "cnt")).longValue();
            woByStatus.put(status, cnt);
            if (!"CLOSED".equals(status) && !"CANCELLED".equals(status)) {
                openCount += cnt;
            }
        }
        List<Map<String, Object>> closed = statisticsMapper.listClosedWorkOrders(
                weekAgo.atStartOfDay(), todayEnd);
        vo.setWorkOrders(new DashboardVO.WorkOrderSummary(
                openCount, calculateMttr(closed), calculateSla(closed)));

        // Inspections summary
        int todayInspections = statisticsMapper.countTodayInspections();
        int todayCompleted = statisticsMapper.countTodayCompletedInspections();
        double completionRate = todayInspections > 0
                ? (double) todayCompleted / todayInspections : 0;
        vo.setInspections(new DashboardVO.InspectionSummary(
                todayInspections, todayCompleted, completionRate));

        // Devices summary
        List<Map<String, Object>> devices = statisticsMapper.findInServiceDevices();
        int totalDevices = devices.size();

        // Quick online check: count devices with any probe sample today
        int onlineDevices = 0;
        for (Map<String, Object> device : devices) {
            int deviceId = (getNumber(device, "id")).intValue();
            String lifecycleStatus = (String) getValue(device, "lifecycleStatus");
            if (!"IN_SERVICE".equals(lifecycleStatus)) {
                continue;
            }
            List<Integer> probeIds = statisticsMapper.findProbeIdsByDevice(deviceId);
            if (!probeIds.isEmpty()) {
                long todaySamples = statisticsMapper.countDistinctSampleDays(
                        probeIds, todayStart, todayEnd);
                if (todaySamples > 0) {
                    onlineDevices++;
                }
            }
        }
        double availability = totalDevices > 0 ? (double) onlineDevices / totalDevices : 0;
        vo.setDevices(new DashboardVO.DeviceSummary(totalDevices, onlineDevices, availability));

        // Top alarm devices
        vo.setTopAlarmDevices(resolveTopAlarmDevices(
                weekAgo.atStartOfDay(), todayEnd));

        return vo;
    }

    // ==================== Helpers ====================

    private List<TrendPoint> bucketByGranularity(List<Map<String, Object>> dayRows,
                                                  String dateKey, String countKey,
                                                  String granularity) {
        if (dayRows == null || dayRows.isEmpty()) {
            return Collections.emptyList();
        }

        switch (granularity) {
            case "WEEK":
                return bucketByWeek(dayRows, dateKey, countKey);
            case "MONTH":
                return bucketByMonth(dayRows, dateKey, countKey);
            case "HOUR":
                return dayRows.stream()
                        .map(r -> new TrendPoint(
                                String.valueOf(getValue(r, dateKey)),
                                getNumber(r, countKey)))
                        .collect(Collectors.toList());
            case "DAY":
            default:
                return dayRows.stream()
                        .map(r -> new TrendPoint(
                                String.valueOf(getValue(r, dateKey)),
                                getNumber(r, countKey)))
                        .collect(Collectors.toList());
        }
    }

    private List<TrendPoint> bucketByWeek(List<Map<String, Object>> dayRows,
                                           String dateKey, String countKey) {
        Map<String, Long> weekBuckets = new LinkedHashMap<>();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        for (Map<String, Object> row : dayRows) {
            Object dateVal = getValue(row, dateKey);
            if (dateVal == null) continue;
            LocalDate date;
            if (dateVal instanceof java.sql.Date) {
                date = ((java.sql.Date) dateVal).toLocalDate();
            } else if (dateVal instanceof LocalDate) {
                date = (LocalDate) dateVal;
            } else {
                date = LocalDate.parse(String.valueOf(dateVal));
            }
            int weekOfYear = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.get(weekFields.weekBasedYear());
            String key = year + "-W" + String.format("%02d", weekOfYear);
            long cnt = getNumber(row, countKey).longValue();
            weekBuckets.merge(key, cnt, Long::sum);
        }
        return weekBuckets.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<TrendPoint> bucketByMonth(List<Map<String, Object>> dayRows,
                                            String dateKey, String countKey) {
        Map<String, Long> monthBuckets = new LinkedHashMap<>();
        for (Map<String, Object> row : dayRows) {
            Object dateVal = getValue(row, dateKey);
            if (dateVal == null) continue;
            LocalDate date;
            if (dateVal instanceof java.sql.Date) {
                date = ((java.sql.Date) dateVal).toLocalDate();
            } else if (dateVal instanceof LocalDate) {
                date = (LocalDate) dateVal;
            } else {
                date = LocalDate.parse(String.valueOf(dateVal));
            }
            String key = String.format("%d-%02d", date.getYear(), date.getMonthValue());
            long cnt = getNumber(row, countKey).longValue();
            monthBuckets.merge(key, cnt, Long::sum);
        }
        return monthBuckets.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private double calculateMttr(List<Map<String, Object>> closedOrders) {
        if (closedOrders == null || closedOrders.isEmpty()) {
            return 0;
        }
        double totalHours = 0;
        int count = 0;
        for (Map<String, Object> row : closedOrders) {
            Object createdAtObj = getValue(row, "createdAt");
            Object closedAtObj = getValue(row, "closedAt");
            if (createdAtObj == null || closedAtObj == null) continue;
            LocalDateTime createdAt = toLocalDateTime(createdAtObj);
            LocalDateTime closedAt = toLocalDateTime(closedAtObj);
            if (closedAt.isBefore(createdAt)) continue;
            totalHours += ChronoUnit.SECONDS.between(createdAt, closedAt) / 3600.0;
            count++;
        }
        return count > 0 ? totalHours / count : 0;
    }

    private double calculateSla(List<Map<String, Object>> closedOrders) {
        if (closedOrders == null || closedOrders.isEmpty()) {
            return 0;
        }
        int total = 0;
        int met = 0;
        for (Map<String, Object> row : closedOrders) {
            Object createdAtObj = getValue(row, "createdAt");
            Object closedAtObj = getValue(row, "closedAt");
            Object dueTimeObj = getValue(row, "dueTime");
            if (createdAtObj == null || closedAtObj == null) continue;
            if (dueTimeObj == null) {
                met++;
                total++;
                continue;
            }
            LocalDateTime closedAt = toLocalDateTime(closedAtObj);
            LocalDateTime dueTime = toLocalDateTime(dueTimeObj);
            total++;
            if (!closedAt.isAfter(dueTime)) {
                met++;
            }
        }
        return total > 0 ? (double) met / total : 0;
    }

    private Map<String, Long> calculateAging(List<Map<String, Object>> openOrders) {
        Map<String, Long> aging = new LinkedHashMap<>();
        aging.put("within24h", 0L);
        aging.put("24h-72h", 0L);
        aging.put("72h-7d", 0L);
        aging.put("over7d", 0L);

        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : openOrders) {
            Object createdAtObj = getValue(row, "createdAt");
            if (createdAtObj == null) continue;
            LocalDateTime createdAt = toLocalDateTime(createdAtObj);
            long hours = ChronoUnit.HOURS.between(createdAt, now);
            if (hours < AGING_24H) {
                aging.merge("within24h", 1L, Long::sum);
            } else if (hours < AGING_72H) {
                aging.merge("24h-72h", 1L, Long::sum);
            } else if (hours < AGING_7D) {
                aging.merge("72h-7d", 1L, Long::sum);
            } else {
                aging.merge("over7d", 1L, Long::sum);
            }
        }
        return aging;
    }

    private List<AlarmStatsVO.DeviceAlarmCount> resolveTopAlarmDevices(
            LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> topAssets = statisticsMapper.topAlarmAssets(
                start, end, TOP_DEVICES_LIMIT);
        if (topAssets.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect all asset IDs
        List<Long> assetIds = topAssets.stream()
                .map(r -> ((Number) getNumber(r, "assetId")).longValue())
                .collect(Collectors.toList());

        // Load assets in one batch
        Map<Long, Map<String, Object>> assetMap = queryAssetsByIds(assetIds);

        // Walk up to find device for each alarm asset
        Map<Integer, String> deviceNameCache = new HashMap<>();
        Map<Integer, Long> deviceAlarmCount = new LinkedHashMap<>();

        for (Map<String, Object> row : topAssets) {
            long assetId = getNumber(row, "assetId").longValue();
            long cnt = (getNumber(row, "cnt")).longValue();
            Map<String, Object> asset = assetMap.get(assetId);
            if (asset == null) continue;

            Long deviceId = resolveDeviceId(asset, assetMap);
            if (deviceId == null) continue;

            int devIdInt = deviceId.intValue();
            deviceAlarmCount.merge(devIdInt, cnt, Long::sum);
            deviceNameCache.computeIfAbsent(devIdInt, id -> {
                String name = queryDeviceName(id);
                return name != null ? name : "Unknown-" + id;
            });
        }

        return deviceAlarmCount.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(TOP_DEVICES_LIMIT)
                .map(e -> new AlarmStatsVO.DeviceAlarmCount(
                        e.getKey(), deviceNameCache.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    private Long resolveDeviceId(Map<String, Object> asset,
                                  Map<Long, Map<String, Object>> cache) {
        // Walk up the asset tree: PROBE/CONTROL → SERVICE → DEVICE
        Long currentId = (Long) asset.get("id");
        int maxDepth = 5; // safety limit
        for (int i = 0; i < maxDepth; i++) {
            Map<String, Object> current = cache.get(currentId);
            if (current == null) {
                current = queryAssetById(currentId);
                if (current == null) return null;
                cache.put(currentId, current);
            }
            AssetKind kind = (AssetKind) current.get("kind");
            if (kind == AssetKind.DEVICE) {
                return (Long) current.get("deviceId");
            }
            Long parentId = (Long) current.get("parentId");
            if (parentId == null || parentId == 0) {
                return null;
            }
            currentId = parentId;
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val instanceof LocalDateTime) {
            return (LocalDateTime) val;
        }
        if (val instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) val).toLocalDateTime();
        }
        if (val instanceof java.util.Date) {
            return ((java.util.Date) val).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(val));
    }

    /**
     * Get numeric value from map key case-insensitively (H2 returns
     * uppercase column aliases, MySQL returns them as written).
     */
    static Object getValue(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static Number getNumber(Map<String, Object> map, String key) {
        Object val = getValue(map, key);
        return val != null ? (Number) val : 0L;
    }

    // ==================== Drill-Down Services ====================

    public Map<String, Object> getAlarmDetail(StatisticsQuery query, int level, int page, int size) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = statisticsMapper.listAlarmsByLevel(start, end, level, offset, size);
        long total = statisticsMapper.countAlarmsByLevelFiltered(start, end, level);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getWorkOrderDetail(StatisticsQuery query, String status, int page, int size) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = statisticsMapper.listWorkOrdersByStatus(start, end, status, offset, size);
        long total = statisticsMapper.countWorkOrdersByStatusFiltered(start, end, status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getDeviceHistory(StatisticsQuery query, int deviceId) {
        LocalDateTime start = query.startDate().atStartOfDay();
        LocalDateTime end = query.endDate().plusDays(1).atStartOfDay();
        long totalDays = ChronoUnit.DAYS.between(query.startDate(), query.endDate()) + 1;
        Map<String, Object> history = statisticsMapper.getDeviceRuntimeHistory(deviceId, start, end);
        List<Integer> probeIds = statisticsMapper.findProbeIdsByDevice(deviceId);
        long onlineDays = probeIds.isEmpty() ? 0
                : statisticsMapper.countDistinctSampleDays(probeIds, start, end);
        if (history == null) {
            history = new LinkedHashMap<>();
            history.put("deviceId", deviceId);
            history.put("deviceName", "Unknown");
            history.put("alarmCount", 0L);
            history.put("maintenanceCount", 0L);
            history.put("totalMaintenanceCost", 0L);
        }
        history.put("onlineDays", onlineDays);
        history.put("totalDays", totalDays);
        history.put("onlineRate", totalDays > 0 ? (double) onlineDays / totalDays : 0);
        return history;
    }

    // ==================== JdbcTemplate Queries ====================

    /**
     * Loads a single t_asset row by id, returning key fields as a map.
     * Returns null if not found.
     */
    private Map<String, Object> queryAssetById(long id) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT id, kind, device_id, parent_id FROM t_asset WHERE id = ?",
                (rs, i) -> mapAssetRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Loads multiple t_asset rows in one batch query.
     * Returns a map keyed by asset id.
     */
    private Map<Long, Map<String, Object>> queryAssetsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT id, kind, device_id, parent_id FROM t_asset WHERE id IN (" + placeholders + ")",
                (rs, i) -> mapAssetRow(rs), ids.toArray());
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((Long) row.get("id"), row);
        }
        return result;
    }

    /**
     * Returns the name of a device from t_device, or null if not found.
     */
    private String queryDeviceName(int deviceId) {
        List<String> names = jdbc.query(
                "SELECT name FROM t_device WHERE id = ?",
                (rs, i) -> rs.getString("name"), deviceId);
        return names.isEmpty() ? null : names.get(0);
    }

    private Map<String, Object> mapAssetRow(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        int kindOrdinal = rs.getInt("kind");
        AssetKind[] values = AssetKind.values();
        map.put("kind", kindOrdinal >= 0 && kindOrdinal < values.length
                ? values[kindOrdinal] : null);
        map.put("deviceId", longOrNull(rs, "device_id"));
        map.put("parentId", longOrNull(rs, "parent_id"));
        return map;
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        Number val = (Number) rs.getObject(column);
        if (val == null) return null;
        if (val instanceof Long l) return l;
        return val.longValue();
    }
}
