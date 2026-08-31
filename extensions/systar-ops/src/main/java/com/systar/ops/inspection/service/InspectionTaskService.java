package com.systar.ops.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.dto.DeviceDto;
import com.systar.common.service.DeviceInfoProvider;
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
import com.systar.ops.ledger.service.MaintenanceRecordService;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.service.WorkOrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InspectionTaskService {

    private static final String TASK_NO_PREFIX = "INS-";
    private static final DateTimeFormatter TASK_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String LIFECYCLE_RETIRED = "RETIRED";
    private static final String RESULT_ABNORMAL = "ABNORMAL";

    private final InspectionTaskMapper taskMapper;
    private final InspectionResultMapper resultMapper;
    private final InspectionPlanMapper planMapper;
    private final InspectionPlanDeviceMapper planDeviceMapper;
    private final InspectionItemTemplateMapper templateMapper;
    private final DeviceInfoProvider deviceInfo;
    private final MaintenanceRecordService maintenanceRecordService;
    private final WorkOrderService workOrderService;

    public InspectionTaskService(InspectionTaskMapper taskMapper,
                                 InspectionResultMapper resultMapper,
                                 InspectionPlanMapper planMapper,
                                 InspectionPlanDeviceMapper planDeviceMapper,
                                 InspectionItemTemplateMapper templateMapper,
                                 DeviceInfoProvider deviceInfo,
                                 MaintenanceRecordService maintenanceRecordService,
                                 WorkOrderService workOrderService) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.planMapper = planMapper;
        this.planDeviceMapper = planDeviceMapper;
        this.templateMapper = templateMapper;
        this.deviceInfo = deviceInfo;
        this.maintenanceRecordService = maintenanceRecordService;
        this.workOrderService = workOrderService;
    }

    @Transactional
    public InspectionTaskEntity generateFromPlan(InspectionPlanEntity plan, LocalDateTime scheduledTime) {
        InspectionTaskEntity existing = findExistingTask(plan.getId(), scheduledTime);
        if (existing != null) {
            return existing;
        }

        InspectionTaskEntity task = new InspectionTaskEntity();
        task.setPlanId(plan.getId());
        task.setTaskNo(generateTaskNo());
        task.setStatus(InspectionTaskStatus.PENDING.name());
        task.setAssigneeId(plan.getDefaultAssigneeId());
        task.setScheduledTime(scheduledTime);
        task.setCreatedAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            InspectionTaskEntity concurrentTask = findExistingTask(plan.getId(), scheduledTime);
            if (concurrentTask != null) {
                return concurrentTask;
            }
            throw ex;
        }

        List<InspectionItemTemplateEntity> templates = templateMapper.selectList(new LambdaQueryWrapper<InspectionItemTemplateEntity>()
                .eq(InspectionItemTemplateEntity::getPlanId, plan.getId())
                .orderByAsc(InspectionItemTemplateEntity::getSortOrder));
        List<InspectionPlanDeviceEntity> planDevices = planDeviceMapper.selectList(new LambdaQueryWrapper<InspectionPlanDeviceEntity>()
                .eq(InspectionPlanDeviceEntity::getPlanId, plan.getId()));
        for (InspectionPlanDeviceEntity planDevice : planDevices) {
            DeviceDto device = deviceInfo.getById(planDevice.getDeviceId());
            if (device == null || LIFECYCLE_RETIRED.equals(device.lifecycleStatus())) {
                continue;
            }
            for (InspectionItemTemplateEntity template : templates) {
                InspectionResultEntity result = new InspectionResultEntity();
                result.setTaskId(task.getId());
                result.setDeviceId(planDevice.getDeviceId());
                result.setTemplateId(template.getId());
                result.setItemName(template.getItemName());
                result.setExpectedValue(template.getExpectedValue());
                result.setCreatedAt(LocalDateTime.now());
                resultMapper.insert(result);
            }
        }
        return task;
    }

    @Transactional
    public void startTask(Long taskId) {
        InspectionTaskEntity task = getAndValidate(taskId, InspectionTaskStatus.IN_PROGRESS);
        task.setStatus(InspectionTaskStatus.IN_PROGRESS.name());
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void completeTask(Long taskId, String remark) {
        InspectionTaskEntity task = getAndValidate(taskId, InspectionTaskStatus.COMPLETED);
        List<InspectionResultEntity> results = listResults(taskId);
        for (InspectionResultEntity result : results) {
            if (result.getCheckResult() == null || result.getCheckResult().isBlank()) {
                throw new IllegalStateException("Inspection result is incomplete: " + result.getId());
            }
        }
        task.setStatus(InspectionTaskStatus.COMPLETED.name());
        task.setCompletedAt(LocalDateTime.now());
        task.setRemark(remark);
        taskMapper.updateById(task);
        handleAbnormalResults(task, results);
    }

    @Transactional
    public void cancelTask(Long taskId, String remark) {
        InspectionTaskEntity task = getAndValidate(taskId, InspectionTaskStatus.CANCELLED);
        task.setStatus(InspectionTaskStatus.CANCELLED.name());
        task.setRemark(remark);
        taskMapper.updateById(task);
    }

    @Transactional
    public InspectionTaskEntity reassignTask(Long taskId, Long assigneeId) {
        InspectionTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Inspection task not found: " + taskId);
        }
        InspectionTaskStatus status = InspectionTaskStatus.valueOf(task.getStatus());
        if (status != InspectionTaskStatus.PENDING && status != InspectionTaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot reassign inspection task in status " + status);
        }
        task.setAssigneeId(assigneeId);
        taskMapper.updateById(task);
        return task;
    }

    public InspectionTaskEntity getById(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    public Page<InspectionTaskEntity> list(int page, int size, String status, Long assigneeId) {
        LambdaQueryWrapper<InspectionTaskEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(InspectionTaskEntity::getStatus, status);
        }
        if (assigneeId != null) {
            wrapper.eq(InspectionTaskEntity::getAssigneeId, assigneeId);
        }
        wrapper.orderByDesc(InspectionTaskEntity::getScheduledTime);
        return taskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public List<InspectionResultEntity> listResults(Long taskId) {
        return resultMapper.selectList(new LambdaQueryWrapper<InspectionResultEntity>()
                .eq(InspectionResultEntity::getTaskId, taskId)
                .orderByAsc(InspectionResultEntity::getId));
    }

    @Transactional
    public void submitResults(Long taskId, List<InspectionResultEntity> submittedResults) {
        InspectionTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Inspection task not found: " + taskId);
        }
        if (InspectionTaskStatus.valueOf(task.getStatus()) != InspectionTaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Inspection results can only be submitted for IN_PROGRESS tasks");
        }
        for (InspectionResultEntity submitted : submittedResults) {
            InspectionResultEntity result = resultMapper.selectById(submitted.getId());
            if (result == null || !taskId.equals(result.getTaskId())) {
                throw new IllegalArgumentException("Inspection result not found: " + submitted.getId());
            }
            result.setCheckResult(submitted.getCheckResult());
            result.setActualValue(submitted.getActualValue());
            result.setRemark(submitted.getRemark());
            resultMapper.updateById(result);
        }
    }

    private void handleAbnormalResults(InspectionTaskEntity task, List<InspectionResultEntity> results) {
        InspectionPlanEntity plan = planMapper.selectById(task.getPlanId());
        boolean createWorkOrder = plan != null && Integer.valueOf(1).equals(plan.getAutoCreateWorkorder());
        for (InspectionResultEntity result : results) {
            if (!RESULT_ABNORMAL.equals(result.getCheckResult())) {
                continue;
            }
            MaintenanceRecordEntity record = new MaintenanceRecordEntity();
            record.setDeviceId(result.getDeviceId());
            record.setType("INSPECTION");
            record.setTitle("Inspection abnormal: " + result.getItemName());
            record.setDescription(result.getRemark());
            record.setPerformerId(task.getAssigneeId() == null ? 0L : task.getAssigneeId());
            record.setCreatorId(plan == null ? 0L : plan.getCreatorId());
            record.setPerformedAt(LocalDateTime.now());
            record.setResult(result.getActualValue());
            record.setInspectionTaskId(task.getId());
            maintenanceRecordService.create(record);

            if (createWorkOrder) {
                WorkOrderEntity order = new WorkOrderEntity();
                order.setTitle("Inspection abnormal: " + result.getItemName());
                order.setDescription(result.getActualValue());
                order.setType("REPAIR");
                order.setSource("INSPECTION");
                order.setInspectionTaskId(task.getId());
                order.setDeviceId(result.getDeviceId());
                order.setPriority(2);
                order.setCreatorId(plan == null ? 0L : plan.getCreatorId());
                workOrderService.createWorkOrder(order);
            }
        }
    }

    private InspectionTaskEntity findExistingTask(Long planId, LocalDateTime scheduledTime) {
        return taskMapper.selectOne(new LambdaQueryWrapper<InspectionTaskEntity>()
                .eq(InspectionTaskEntity::getPlanId, planId)
                .eq(InspectionTaskEntity::getScheduledTime, scheduledTime));
    }

    private InspectionTaskEntity getAndValidate(Long taskId, InspectionTaskStatus targetStatus) {
        InspectionTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Inspection task not found: " + taskId);
        }
        InspectionTaskStatus current = InspectionTaskStatus.valueOf(task.getStatus());
        if (!current.canTransitionTo(targetStatus)) {
            throw new IllegalStateException("Cannot transition from " + current + " to " + targetStatus);
        }
        return task;
    }

    private String generateTaskNo() {
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return TASK_NO_PREFIX + LocalDateTime.now().format(TASK_NO_FMT) + "-" + random;
    }
}
