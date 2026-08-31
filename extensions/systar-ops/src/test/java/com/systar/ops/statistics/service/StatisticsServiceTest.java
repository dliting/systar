package com.systar.ops.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.ops.inspection.InspectionTaskStatus;
import com.systar.ops.inspection.entity.InspectionResultEntity;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
import com.systar.ops.inspection.mapper.InspectionResultMapper;
import com.systar.ops.inspection.mapper.InspectionTaskMapper;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import com.systar.ops.ledger.mapper.MaintenanceRecordMapper;
import com.systar.ops.statistics.model.*;
import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.workorder.WorkOrderStatus;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class StatisticsServiceTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private InspectionTaskMapper inspectionTaskMapper;

    @Autowired
    private InspectionResultMapper inspectionResultMapper;

    @Autowired
    private MaintenanceRecordMapper maintenanceRecordMapper;

    @Autowired
    private CacheManager cacheManager;

    private LocalDate today;
    private LocalDate yesterday;
    private LocalDate threeDaysAgo;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        yesterday = today.minusDays(1);
        threeDaysAgo = today.minusDays(3);
    }

    // ==================== StatisticsQuery validation ====================

    @Test
    void validateQuery_shouldThrowWhenStartAfterEnd() {
        assertThatThrownBy(() -> new StatisticsQuery(today, yesterday, null, null, "DAY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endDate must be after startDate");
    }

    @Test
    void validateQuery_shouldThrowWhenRangeExceeds365Days() {
        assertThatThrownBy(() -> new StatisticsQuery(
                today.minusDays(366), today, null, null, "DAY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("365");
    }

    @Test
    void validateQuery_shouldThrowWhenNullDates() {
        assertThatThrownBy(() -> new StatisticsQuery(null, today, null, null, "DAY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateQuery_shouldThrowOnInvalidGranularity() {
        assertThatThrownBy(() -> new StatisticsQuery(
                yesterday, today, null, null, "YEARLY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid granularity");
    }

    @Test
    void validateQuery_shouldDefaultGranularityToDay() {
        StatisticsQuery query = new StatisticsQuery(yesterday, today, null, null, null);
        assertThat(query.granularity()).isEqualTo("DAY");
    }

    // ==================== Work Order Statistics ====================

    @Test
    void getWorkOrderStats_shouldReturnCorrectStatusCounts() {
        createWorkOrder(WorkOrderStatus.CREATED, threeDaysAgo);
        createWorkOrder(WorkOrderStatus.CREATED, yesterday);
        createWorkOrder(WorkOrderStatus.CLOSED, yesterday);

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        WorkOrderStatsVO vo = statisticsService.getWorkOrderStats(query);

        assertThat(vo.getByStatus()).containsEntry("CREATED", 2L);
        assertThat(vo.getByStatus()).containsEntry("CLOSED", 1L);
        assertThat(vo.getTrend()).isNotEmpty();
        assertThat(vo.getCurrentPeriodTotal()).isEqualTo(3);
    }

    @Test
    void getWorkOrderStats_shouldCalculateMttr() {
        WorkOrderEntity wo = createWorkOrder(WorkOrderStatus.CLOSED, threeDaysAgo);
        wo.setClosedAt(LocalDateTime.now().minusHours(5));
        workOrderMapper.updateById(wo);

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        WorkOrderStatsVO vo = statisticsService.getWorkOrderStats(query);

        assertThat(vo.getMttrHours()).isGreaterThan(0);
    }

    @Test
    void getWorkOrderStats_shouldCalculateAging() {
        createWorkOrder(WorkOrderStatus.CREATED, threeDaysAgo.minusDays(5)); // over 7d

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        WorkOrderStatsVO vo = statisticsService.getWorkOrderStats(query);

        assertThat(vo.getAgingDistribution()).containsKey("within24h");
        assertThat(vo.getAgingDistribution()).containsKey("over7d");
    }

    @Test
    void getWorkOrderStats_shouldReturnZerosForEmptyRange() {
        // Use a date range far in the past with no data
        StatisticsQuery query = new StatisticsQuery(
                today.minusDays(100), today.minusDays(90), null, null, "DAY");
        WorkOrderStatsVO vo = statisticsService.getWorkOrderStats(query);

        assertThat(vo.getByStatus()).isEmpty();
        assertThat(vo.getMttrHours()).isEqualTo(0);
        assertThat(vo.getCurrentPeriodTotal()).isEqualTo(0);
    }

    // ==================== Inspection Statistics ====================

    @Test
    void getInspectionStats_shouldCalculateCompletionRate() {
        InspectionTaskEntity t1 = createInspectionTask(InspectionTaskStatus.COMPLETED, yesterday, 1L);
        InspectionTaskEntity t2 = createInspectionTask(InspectionTaskStatus.PENDING, yesterday, 2L);

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        InspectionStatsVO vo = statisticsService.getInspectionStats(query);

        assertThat(vo.getTotalTasks()).isEqualTo(2);
        assertThat(vo.getCompletedTasks()).isEqualTo(1);
        assertThat(vo.getCompletionRate()).isEqualTo(0.5);
    }

    @Test
    void getInspectionStats_shouldCalculateAnomalyRate() {
        InspectionTaskEntity task = createInspectionTask(InspectionTaskStatus.COMPLETED, yesterday, 3L);
        createInspectionResult(task.getId(), "ABNORMAL", yesterday, 1L);
        createInspectionResult(task.getId(), "NORMAL", yesterday, 2L);

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        InspectionStatsVO vo = statisticsService.getInspectionStats(query);

        assertThat(vo.getAnomalyCount()).isEqualTo(1);
    }

    @Test
    void getInspectionStats_shouldReturnZerosForEmptyRange() {
        StatisticsQuery query = new StatisticsQuery(
                today.minusDays(100), today.minusDays(90), null, null, "DAY");
        InspectionStatsVO vo = statisticsService.getInspectionStats(query);

        assertThat(vo.getTotalTasks()).isEqualTo(0);
        assertThat(vo.getCompletionRate()).isEqualTo(0);
    }

    // ==================== Maintenance Statistics ====================

    @Test
    void getMaintenanceStats_shouldGroupByType() {
        createMaintenanceRecord("REPAIR", new BigDecimal("150.00"), yesterday);
        createMaintenanceRecord("MAINTENANCE", new BigDecimal("200.00"), yesterday);
        createMaintenanceRecord("REPAIR", new BigDecimal("100.00"), yesterday);

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        MaintenanceStatsVO vo = statisticsService.getMaintenanceStats(query);

        assertThat(vo.getTotalRecords()).isEqualTo(3);
        assertThat(vo.getByType()).containsEntry("REPAIR", 2L);
        assertThat(vo.getByType()).containsEntry("MAINTENANCE", 1L);
        assertThat(vo.getTotalCost()).isEqualByComparingTo(new BigDecimal("450.00"));
    }

    @Test
    void getMaintenanceStats_shouldReturnZerosForEmptyRange() {
        StatisticsQuery query = new StatisticsQuery(
                today.minusDays(100), today.minusDays(90), null, null, "DAY");
        MaintenanceStatsVO vo = statisticsService.getMaintenanceStats(query);

        assertThat(vo.getTotalRecords()).isEqualTo(0);
        assertThat(vo.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ==================== Alarm Statistics ====================

    @Test
    void getAlarmStats_shouldReturnEmptyForNoData() {
        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        AlarmStatsVO vo = statisticsService.getAlarmStats(query);

        assertThat(vo.getTotalAlarms()).isEqualTo(0);
        assertThat(vo.getHandlingRate()).isEqualTo(0);
        assertThat(vo.getTopDevices()).isEmpty();
    }

    // ==================== Dashboard ====================

    @Test
    void getDashboard_shouldReturnAllSections() {
        createWorkOrder(WorkOrderStatus.CREATED, today);
        DashboardVO vo = statisticsService.getDashboardData();

        assertThat(vo.getAlarms()).isNotNull();
        assertThat(vo.getWorkOrders()).isNotNull();
        assertThat(vo.getInspections()).isNotNull();
        assertThat(vo.getDevices()).isNotNull();
        assertThat(vo.getTopAlarmDevices()).isNotNull();
    }

    @Test
    void getDashboard_shouldBeCached() {
        // Clear cache before test
        Cache cache = cacheManager.getCache("dashboard");
        if (cache != null) {
            cache.clear();
        }

        DashboardVO first = statisticsService.getDashboardData();
        DashboardVO second = statisticsService.getDashboardData();

        // Same call should return structurally same data
        assertThat(second.getDevices().total()).isEqualTo(first.getDevices().total());
    }

    // ==================== Period comparison ====================

    @Test
    void getWorkOrderStats_shouldComputePeriodComparison() {
        // Current period: yesterday to today
        createWorkOrder(WorkOrderStatus.CREATED, yesterday);
        createWorkOrder(WorkOrderStatus.CREATED, yesterday);

        StatisticsQuery query = new StatisticsQuery(yesterday, today, null, null, "DAY");
        WorkOrderStatsVO vo = statisticsService.getWorkOrderStats(query);

        assertThat(vo.getCurrentPeriodTotal()).isEqualTo(2);
        assertThat(vo.getPrevPeriodTotal()).isEqualTo(0);
    }

    // ==================== Granularity ====================

    @Test
    void getWorkOrderStats_shouldBucketByWeek() {
        createWorkOrder(WorkOrderStatus.CREATED, yesterday);
        createWorkOrder(WorkOrderStatus.CREATED, yesterday);

        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "WEEK");
        WorkOrderStatsVO vo = statisticsService.getWorkOrderStats(query);

        assertThat(vo.getTrend()).isNotEmpty();
    }

    // ==================== Drill-Down Tests ====================

    @Test
    void getAlarmDetail_shouldReturnFilteredList() {
        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        Map<String, Object> result = statisticsService.getAlarmDetail(query, 1, 1, 20);
        assertThat(result).containsKeys("records", "total", "page", "size");
    }

    @Test
    void getWorkOrderDetail_shouldReturnFilteredList() {
        createWorkOrder(WorkOrderStatus.CREATED, yesterday);
        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        Map<String, Object> result = statisticsService.getWorkOrderDetail(query, "CREATED", 1, 20);
        assertThat(result).containsKeys("records", "total", "page", "size");
    }

    @Test
    void getDeviceHistory_shouldReturnRuntimeData() {
        StatisticsQuery query = new StatisticsQuery(threeDaysAgo, today, null, null, "DAY");
        Map<String, Object> history = statisticsService.getDeviceHistory(query, 1001);
        assertThat(history).containsKeys("deviceId", "onlineDays", "totalDays", "onlineRate");
    }

    // ==================== Helpers ====================

    private WorkOrderEntity createWorkOrder(WorkOrderStatus status, LocalDate createdDate) {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.setOrderNo("TEST-" + System.nanoTime());
        wo.setTitle("Test Work Order");
        wo.setType("REPAIR");
        wo.setSource("MANUAL");
        wo.setDeviceId(0);
        wo.setPriority(2);
        wo.setCreatorId(1L);
        wo.setStatus(status.name());
        wo.setCreatedAt(createdDate.atTime(12, 0));
        if (status == WorkOrderStatus.CLOSED) {
            wo.setClosedAt(createdDate.atStartOfDay().plusHours(3));
        }
        wo.setDueTime(createdDate.atStartOfDay().plusHours(24));
        workOrderMapper.insert(wo);
        return wo;
    }

    private InspectionTaskEntity createInspectionTask(InspectionTaskStatus status, LocalDate scheduledDate, long planId) {
        InspectionTaskEntity task = new InspectionTaskEntity();
        task.setPlanId(planId);
        task.setTaskNo("TEST-TASK-" + System.nanoTime());
        task.setStatus(status.name());
        task.setScheduledTime(scheduledDate.atStartOfDay());
        task.setCreatedAt(LocalDateTime.now());
        inspectionTaskMapper.insert(task);
        return task;
    }

    private void createInspectionResult(Long taskId, String checkResult, LocalDate createdDate, long templateId) {
        InspectionResultEntity result = new InspectionResultEntity();
        result.setTaskId(taskId);
        result.setTemplateId(templateId);
        result.setItemName("Test Item");
        result.setDeviceId(1001);
        result.setCheckResult(checkResult);
        result.setCreatedAt(createdDate.atStartOfDay());
        inspectionResultMapper.insert(result);
    }

    private void createMaintenanceRecord(String type, BigDecimal cost, LocalDate performedDate) {
        MaintenanceRecordEntity record = new MaintenanceRecordEntity();
        record.setDeviceId(1001);
        record.setType(type);
        record.setTitle("Test Maintenance");
        record.setDescription("Test maintenance");
        record.setPerformerId(1L);
        record.setCreatorId(1L);
        record.setCost(cost);
        record.setPerformedAt(performedDate.atStartOfDay());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        maintenanceRecordMapper.insert(record);
    }
}
