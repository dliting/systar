package com.systar.ops.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.ops.inspection.InspectionTaskStatus;
import com.systar.ops.inspection.entity.InspectionItemTemplateEntity;
import com.systar.ops.inspection.entity.InspectionPlanDeviceEntity;
import com.systar.ops.inspection.entity.InspectionPlanEntity;
import com.systar.ops.inspection.entity.InspectionResultEntity;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
import com.systar.ops.inspection.mapper.InspectionItemTemplateMapper;
import com.systar.ops.inspection.mapper.InspectionPlanDeviceMapper;
import com.systar.ops.inspection.mapper.InspectionPlanMapper;
import com.systar.ops.inspection.mapper.InspectionResultMapper;
import com.systar.ops.inspection.mapper.InspectionTaskMapper;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import com.systar.ops.ledger.mapper.MaintenanceRecordMapper;
import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.mapper.WorkOrderMapper;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class InspectionTaskServiceTest {

    @Autowired
    private InspectionTaskService service;

    @Autowired
    private InspectionPlanService planService;

    @Autowired
    private InspectionPlanMapper planMapper;

    @Autowired
    private InspectionPlanDeviceMapper planDeviceMapper;

    @Autowired
    private InspectionItemTemplateMapper templateMapper;

    @Autowired
    private InspectionTaskMapper taskMapper;

    @Autowired
    private InspectionResultMapper resultMapper;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbc;

    @Autowired
    private MaintenanceRecordMapper maintenanceRecordMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Test
    void generateFromPlan_createsTaskAndTemplateSnapshotsSkippingRetiredDevices() {
        InspectionPlanEntity plan = insertPlan(0);
        insertDevice(3101, "IN_SERVICE");
        insertDevice(3102, "RETIRED");
        insertPlanDevice(plan.getId(), 3101);
        insertPlanDevice(plan.getId(), 3102);
        insertTemplate(plan.getId(), "Temperature", "NUMBER", "25");

        InspectionTaskEntity task = service.generateFromPlan(plan, LocalDateTime.of(2026, 5, 23, 8, 0));

        assertThat(task.getId()).isNotNull();
        assertThat(task.getStatus()).isEqualTo(InspectionTaskStatus.PENDING.name());
        assertThat(task.getAssigneeId()).isEqualTo(11L);
        List<InspectionResultEntity> results = resultMapper.selectList(null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDeviceId()).isEqualTo(3101);
        assertThat(results.get(0).getItemName()).isEqualTo("Temperature");
        assertThat(results.get(0).getExpectedValue()).isEqualTo("25");
        assertThat(results.get(0).getCheckResult()).isNull();
    }

    @Test
    void startTask_transitionsPendingToInProgress() {
        InspectionTaskEntity task = insertTask(InspectionTaskStatus.PENDING);

        service.startTask(task.getId());

        InspectionTaskEntity updated = taskMapper.selectById(task.getId());
        assertThat(updated.getStatus()).isEqualTo(InspectionTaskStatus.IN_PROGRESS.name());
        assertThat(updated.getStartedAt()).isNotNull();
    }

    @Test
    void completeTask_requiresAllResults() {
        InspectionPlanEntity plan = insertPlan(0);
        InspectionTaskEntity task = insertTask(plan.getId(), InspectionTaskStatus.IN_PROGRESS);
        insertResult(task.getId(), 3201, null, null);

        assertThatThrownBy(() -> service.completeTask(task.getId(), "done"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inspection result is incomplete");
    }

    @Test
    void completeTask_marksCompletedWhenResultsAreFilled() {
        InspectionPlanEntity plan = insertPlan(0);
        InspectionTaskEntity task = insertTask(plan.getId(), InspectionTaskStatus.IN_PROGRESS);
        insertResult(task.getId(), 3202, "NORMAL", "24");

        service.completeTask(task.getId(), "done");

        InspectionTaskEntity updated = taskMapper.selectById(task.getId());
        assertThat(updated.getStatus()).isEqualTo(InspectionTaskStatus.COMPLETED.name());
        assertThat(updated.getCompletedAt()).isNotNull();
        assertThat(updated.getRemark()).isEqualTo("done");
    }

    @Test
    void cancelTask_allowsPendingTaskCancellation() {
        InspectionTaskEntity task = insertTask(InspectionTaskStatus.PENDING);

        service.cancelTask(task.getId(), "weather");

        InspectionTaskEntity updated = taskMapper.selectById(task.getId());
        assertThat(updated.getStatus()).isEqualTo(InspectionTaskStatus.CANCELLED.name());
        assertThat(updated.getRemark()).isEqualTo("weather");
    }

    @Test
    void completeTask_withAbnormalResultCreatesMaintenanceRecordAndWorkOrderWhenEnabled() {
        InspectionPlanEntity plan = insertPlan(1);
        insertDevice(3301, "IN_SERVICE");
        InspectionTaskEntity task = insertTask(plan.getId(), InspectionTaskStatus.IN_PROGRESS);
        insertResult(task.getId(), 3301, "ABNORMAL", "leaking");

        service.completeTask(task.getId(), "abnormal found");

        List<MaintenanceRecordEntity> records = maintenanceRecordMapper.selectList(null);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getDeviceId()).isEqualTo(3301);
        assertThat(records.get(0).getType()).isEqualTo("INSPECTION");
        assertThat(records.get(0).getInspectionTaskId()).isEqualTo(task.getId());
        WorkOrderEntity order = workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getInspectionTaskId, task.getId()));
        assertThat(order).isNotNull();
        assertThat(order.getDeviceId()).isEqualTo(3301);
        assertThat(order.getSource()).isEqualTo("INSPECTION");
    }

    @Test
    void completeTask_withAbnormalResultDoesNotCreateWorkOrderWhenDisabled() {
        InspectionPlanEntity plan = insertPlan(0);
        insertDevice(3302, "IN_SERVICE");
        InspectionTaskEntity task = insertTask(plan.getId(), InspectionTaskStatus.IN_PROGRESS);
        insertResult(task.getId(), 3302, "ABNORMAL", "leaking");

        service.completeTask(task.getId(), "abnormal found");

        assertThat(maintenanceRecordMapper.selectList(null)).hasSize(1);
        assertThat(workOrderMapper.selectList(null)).isEmpty();
    }

    @Test
    void deletePlan_withPendingTaskThrows() {
        InspectionPlanEntity plan = insertPlan(0);
        insertTask(plan.getId(), InspectionTaskStatus.PENDING);

        assertThatThrownBy(() -> planService.delete(plan.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active tasks");
    }

    @Test
    void deletePlan_withInProgressTaskThrows() {
        InspectionPlanEntity plan = insertPlan(0);
        insertTask(plan.getId(), InspectionTaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> planService.delete(plan.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active tasks");
    }

    private InspectionPlanEntity insertPlan(Integer autoCreateWorkorder) {
        InspectionPlanEntity plan = new InspectionPlanEntity();
        plan.setName("Daily plan " + autoCreateWorkorder);
        plan.setCronExpression("0 0 8 * * *");
        plan.setEnabled(1);
        plan.setDefaultAssigneeId(11L);
        plan.setAutoCreateWorkorder(autoCreateWorkorder);
        plan.setCreatorId(22L);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return plan;
    }

    private void insertDevice(Integer id, String lifecycleStatus) {
        jdbc.update(
                "INSERT INTO t_device (id, name, parent, lifecycle_status) VALUES (?, ?, ?, ?)",
                id, "device_" + id, 10, lifecycleStatus);
    }

    private void insertPlanDevice(Long planId, Integer deviceId) {
        InspectionPlanDeviceEntity planDevice = new InspectionPlanDeviceEntity();
        planDevice.setPlanId(planId);
        planDevice.setDeviceId(deviceId);
        planDeviceMapper.insert(planDevice);
    }

    private void insertTemplate(Long planId, String itemName, String itemType, String expectedValue) {
        InspectionItemTemplateEntity template = new InspectionItemTemplateEntity();
        template.setPlanId(planId);
        template.setItemName(itemName);
        template.setItemType(itemType);
        template.setExpectedValue(expectedValue);
        template.setSortOrder(1);
        templateMapper.insert(template);
    }

    private InspectionTaskEntity insertTask(InspectionTaskStatus status) {
        return insertTask(insertPlan(0).getId(), status);
    }

    private InspectionTaskEntity insertTask(Long planId, InspectionTaskStatus status) {
        InspectionTaskEntity task = new InspectionTaskEntity();
        task.setPlanId(planId);
        task.setTaskNo("IT-" + System.nanoTime());
        task.setStatus(status.name());
        task.setAssigneeId(11L);
        task.setScheduledTime(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        if (status == InspectionTaskStatus.IN_PROGRESS) {
            task.setStartedAt(LocalDateTime.now());
        }
        taskMapper.insert(task);
        return task;
    }

    private void insertResult(Long taskId, Integer deviceId, String checkResult, String actualValue) {
        InspectionResultEntity result = new InspectionResultEntity();
        result.setTaskId(taskId);
        result.setDeviceId(deviceId);
        result.setTemplateId(1L);
        result.setItemName("Temperature");
        result.setExpectedValue("25");
        result.setCheckResult(checkResult);
        result.setActualValue(actualValue);
        result.setCreatedAt(LocalDateTime.now());
        resultMapper.insert(result);
    }
}
