package com.systar.ops.workorder.service;

import com.systar.common.config.SystemConfigManager;
import com.systar.common.dto.DeviceDto;
import com.systar.common.service.DeviceInfoProvider;
import com.systar.common.service.DeviceLifecycleManager;
import com.systar.ops.workorder.WorkOrderStatus;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.entity.WorkOrderLogEntity;
import com.systar.ops.workorder.mapper.WorkOrderLogMapper;
import com.systar.ops.workorder.mapper.WorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkOrderService {

    private static final String ORDER_NO_PREFIX = "WO-";
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String CONFIG_SLA_URGENT = "ops.workorder.sla_hours_urgent";
    private static final String CONFIG_SLA_HIGH = "ops.workorder.sla_hours_high";
    private static final String CONFIG_SLA_MEDIUM = "ops.workorder.sla_hours_medium";
    private static final String CONFIG_SLA_LOW = "ops.workorder.sla_hours_low";
    private static final String LIFECYCLE_IN_SERVICE = "IN_SERVICE";
    private static final String LIFECYCLE_UNDER_REPAIR = "UNDER_REPAIR";
    private static final int DEFAULT_PRIORITY = 2;

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final DeviceInfoProvider deviceInfo;
    private final DeviceLifecycleManager lifecycleManager;
    private final SystemConfigManager configManager;

    public WorkOrderService(WorkOrderMapper workOrderMapper,
                            WorkOrderLogMapper workOrderLogMapper,
                            DeviceInfoProvider deviceInfo,
                            DeviceLifecycleManager lifecycleManager,
                            SystemConfigManager configManager) {
        this.workOrderMapper = workOrderMapper;
        this.workOrderLogMapper = workOrderLogMapper;
        this.deviceInfo = deviceInfo;
        this.lifecycleManager = lifecycleManager;
        this.configManager = configManager;
    }

    @Transactional
    public WorkOrderEntity createWorkOrder(WorkOrderEntity order) {
        order.setOrderNo(generateOrderNo());
        order.setStatus(WorkOrderStatus.CREATED.name());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        if (order.getSpaceId() == null) {
            order.setSpaceId(deviceInfo.resolveSpaceId(order.getDeviceId()));
        }
        if (order.getPriority() == null) {
            order.setPriority(DEFAULT_PRIORITY);
        }
        order.setDueTime(calculateDueTime(order.getPriority()));
        workOrderMapper.insert(order);
        appendLog(order.getId(), order.getCreatorId(), "CREATE", null);
        return order;
    }

    @Transactional
    public void assign(Long orderId, Long assigneeId, Long operatorId) {
        WorkOrderEntity order = getAndValidate(orderId, WorkOrderStatus.ASSIGNED);
        order.setStatus(WorkOrderStatus.ASSIGNED.name());
        order.setAssigneeId(assigneeId);
        order.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(order);
        appendLog(orderId, operatorId, "ASSIGN", null);
        lifecycleManager.updateLifecycleStatus(order.getDeviceId(), LIFECYCLE_UNDER_REPAIR);
    }

    @Transactional
    public void startProcessing(Long orderId, Long operatorId) {
        WorkOrderEntity order = getAndValidate(orderId, WorkOrderStatus.PROCESSING);
        order.setStatus(WorkOrderStatus.PROCESSING.name());
        order.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(order);
        appendLog(orderId, operatorId, "PROCESS", null);
    }

    @Transactional
    public void close(Long orderId, String resolution, Long operatorId) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution is required when closing a work order");
        }
        WorkOrderEntity order = getAndValidate(orderId, WorkOrderStatus.CLOSED);
        order.setStatus(WorkOrderStatus.CLOSED.name());
        order.setResolution(resolution);
        order.setClosedBy(operatorId);
        order.setClosedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(order);
        appendLog(orderId, operatorId, "CLOSE", resolution);
        lifecycleManager.updateLifecycleStatus(order.getDeviceId(), LIFECYCLE_IN_SERVICE);
    }

    @Transactional
    public void cancel(Long orderId, String comment, Long operatorId) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Comment is required when cancelling a work order");
        }
        WorkOrderEntity order = requireOrder(orderId);
        WorkOrderStatus current = WorkOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(WorkOrderStatus.CANCELLED)) {
            throw new IllegalStateException("Cannot cancel work order in status " + current);
        }
        order.setStatus(WorkOrderStatus.CANCELLED.name());
        order.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(order);
        appendLog(orderId, operatorId, "CANCEL", comment);
        DeviceDto device = deviceInfo.getById(order.getDeviceId());
        if (device != null && LIFECYCLE_UNDER_REPAIR.equals(device.lifecycleStatus())) {
            lifecycleManager.updateLifecycleStatus(order.getDeviceId(), LIFECYCLE_IN_SERVICE);
        }
    }

    public WorkOrderEntity getById(Long orderId) {
        return workOrderMapper.selectById(orderId);
    }

    private String generateOrderNo() {
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return ORDER_NO_PREFIX + LocalDateTime.now().format(ORDER_NO_FMT) + "-" + random;
    }

    private LocalDateTime calculateDueTime(int priority) {
        String configKey = switch (priority) {
            case 4 -> CONFIG_SLA_URGENT;
            case 3 -> CONFIG_SLA_HIGH;
            case 2 -> CONFIG_SLA_MEDIUM;
            default -> CONFIG_SLA_LOW;
        };
        int slaHours = configManager.getIntValue(configKey, 72);
        return LocalDateTime.now().plusHours(slaHours);
    }

    private WorkOrderEntity requireOrder(Long orderId) {
        WorkOrderEntity order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Work order not found: " + orderId);
        }
        return order;
    }

    private WorkOrderEntity getAndValidate(Long orderId, WorkOrderStatus targetStatus) {
        WorkOrderEntity order = requireOrder(orderId);
        WorkOrderStatus current = WorkOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(targetStatus)) {
            throw new IllegalStateException("Cannot transition from " + current + " to " + targetStatus);
        }
        return order;
    }

    private void appendLog(Long orderId, Long operatorId, String action, String comment) {
        WorkOrderLogEntity log = new WorkOrderLogEntity();
        log.setWorkOrderId(orderId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setComment(comment);
        log.setCreatedAt(LocalDateTime.now());
        workOrderLogMapper.insert(log);
    }
}
