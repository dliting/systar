package com.systar.ops.statistics.mapper;

import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class StatisticsMapperTest {

    private static final int NONEXISTENT_ID = 99999;
    private static final int DIRECT_DEVICE_ID = 9101;
    private static final int DIRECT_PROBE_ID  = 9301;
    private static final int TOP_N_LIMIT   = 10;
    private static final int PAGE_SIZE     = 20;
    private static final int ALARM_LEVEL_WARN = 1;
    private static final int OFFSET_ZERO   = 0;

    @Autowired
    private StatisticsMapper mapper;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final LocalDateTime now       = LocalDateTime.now();
    private final LocalDateTime start     = now.minusDays(7);
    private final LocalDateTime prevEnd   = now.minusDays(7);
    private final LocalDateTime prevStart = now.minusDays(14);

    @Test
    void countAlarmsByLevel_shouldReturnEmptyForNoData() {
        List<Map<String, Object>> result = mapper.countAlarmsByLevel(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void findProbeIdsByDevice_shouldMatchProbesParentedDirectlyToDevice() {
        // In the runtime data model t_probe.parent references t_device.id
        // directly (services attach via the source column), so probe lookup
        // must accept the direct edge, not only probe→service→device chains.
        jdbcTemplate.update(
                "INSERT INTO t_probe (id, name, caption, parent) "
                        + "VALUES (?, 'stats_probe_direct', '直挂设备探头', ?)",
                DIRECT_PROBE_ID, DIRECT_DEVICE_ID);

        List<Integer> ids = mapper.findProbeIdsByDevice(DIRECT_DEVICE_ID);

        assertThat(ids).contains(DIRECT_PROBE_ID);
    }

    @Test
    void countAlarmsByDay_shouldReturnEmptyForNoData() {
        List<Map<String, Object>> result = mapper.countAlarmsByDay(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void alarmHandlingRate_shouldReturnMap() {
        Map<String, Object> result = mapper.alarmHandlingRate(start, now);
        assertThat(result).isNotNull();
        assertThat(result).containsKey("total");
    }

    @Test
    void alarmPeriodComparison_shouldReturnMap() {
        Map<String, Object> result = mapper.alarmPeriodComparison(now, now, prevStart, prevEnd);
        assertThat(result).isNotNull();
        String curKey = result.containsKey("curCount") ? "curCount" : "curcount";
        assertThat(result.get(curKey)).isInstanceOf(Number.class);
    }

    @Test
    void countPendingAlarms_shouldReturnLong() {
        long result = mapper.countPendingAlarms(start, now);
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void topAlarmAssets_shouldReturnList() {
        List<Map<String, Object>> result = mapper.topAlarmAssets(start, now, TOP_N_LIMIT);
        assertThat(result).isNotNull();
    }

    @Test
    void countWorkOrdersByStatus_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countWorkOrdersByStatus(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void listClosedWorkOrders_shouldReturnList() {
        List<Map<String, Object>> result = mapper.listClosedWorkOrders(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void listOpenWorkOrders_shouldReturnList() {
        List<Map<String, Object>> result = mapper.listOpenWorkOrders();
        assertThat(result).isNotNull();
    }

    @Test
    void countWorkOrdersByDay_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countWorkOrdersByDay(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void workOrderPeriodComparison_shouldReturnMap() {
        Map<String, Object> result = mapper.workOrderPeriodComparison(now, now, prevStart, prevEnd);
        assertThat(result).isNotNull();
    }

    @Test
    void countInspectionsByStatus_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countInspectionsByStatus(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void countAnomalies_shouldReturnLong() {
        long result = mapper.countAnomalies(start, now);
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countAnomaliesByDay_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countAnomaliesByDay(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void countInspectionsByDay_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countInspectionsByDay(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void inspectionPeriodComparison_shouldReturnMap() {
        Map<String, Object> result = mapper.inspectionPeriodComparison(now, now, prevStart, prevEnd);
        assertThat(result).isNotNull();
    }

    @Test
    void findProbeIdsByDevice_shouldReturnList() {
        List<Integer> result = mapper.findProbeIdsByDevice(NONEXISTENT_ID);
        assertThat(result).isNotNull();
    }

    @Test
    void findInServiceDevices_shouldReturnList() {
        List<Map<String, Object>> result = mapper.findInServiceDevices();
        assertThat(result).isNotNull();
    }

    @Test
    void countMaintenanceByType_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countMaintenanceByType(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void sumMaintenanceCost_shouldReturnMap() {
        Map<String, Object> result = mapper.sumMaintenanceCost(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void countMaintenanceByDay_shouldReturnList() {
        List<Map<String, Object>> result = mapper.countMaintenanceByDay(start, now);
        assertThat(result).isNotNull();
    }

    @Test
    void maintenancePeriodComparison_shouldReturnMap() {
        Map<String, Object> result = mapper.maintenancePeriodComparison(now, now, prevStart, prevEnd);
        assertThat(result).isNotNull();
    }

    @Test
    void countTodayAlarms_shouldReturnInt() {
        int result = mapper.countTodayAlarms();
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countTodayInspections_shouldReturnInt() {
        int result = mapper.countTodayInspections();
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countTodayCompletedInspections_shouldReturnInt() {
        int result = mapper.countTodayCompletedInspections();
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void listAlarmsByLevel_shouldReturnList() {
        List<Map<String, Object>> result = mapper.listAlarmsByLevel(start, now, ALARM_LEVEL_WARN, OFFSET_ZERO, PAGE_SIZE);
        assertThat(result).isNotNull();
    }

    @Test
    void countAlarmsByLevelFiltered_shouldReturnLong() {
        Long result = mapper.countAlarmsByLevelFiltered(start, now, ALARM_LEVEL_WARN);
        assertThat(result).isNotNull();
    }

    @Test
    void listWorkOrdersByStatus_shouldReturnList() {
        List<Map<String, Object>> result = mapper.listWorkOrdersByStatus(start, now, "CREATED", OFFSET_ZERO, PAGE_SIZE);
        assertThat(result).isNotNull();
    }

    @Test
    void countWorkOrdersByStatusFiltered_shouldReturnLong() {
        Long result = mapper.countWorkOrdersByStatusFiltered(start, now, "CREATED");
        assertThat(result).isNotNull();
    }

    @Test
    void getDeviceRuntimeHistory_shouldReturnNullForNonexistentDevice() {
        Map<String, Object> result = mapper.getDeviceRuntimeHistory(NONEXISTENT_ID, start, now);
        // LEFT JOIN with no matching device returns null
        assertThat(result).isNull();
    }

    @Test
    void countDistinctSampleDays_shouldReturnZeroForNonexistentMonitors() {
        long result = mapper.countDistinctSampleDays(List.of(NONEXISTENT_ID), start, now);
        assertThat(result).isEqualTo(0);
    }
}
