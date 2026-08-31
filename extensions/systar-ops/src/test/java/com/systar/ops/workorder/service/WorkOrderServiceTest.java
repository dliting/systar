package com.systar.ops.workorder.service;

import com.systar.ops.workorder.WorkOrderStatus;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class WorkOrderServiceTest {

    @Autowired
    private WorkOrderService workOrderService;

    private WorkOrderEntity buildOrder() {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setTitle("Test work order");
        order.setType("REPAIR");
        order.setSource("MANUAL");
        order.setDeviceId(1001);
        order.setPriority(2);
        order.setCreatorId(1L);
        return order;
    }

    @Test
    void createWorkOrder_setsStatusCreated() {
        WorkOrderEntity order = buildOrder();
        WorkOrderEntity created = workOrderService.createWorkOrder(order);

        assertThat(created.getStatus()).isEqualTo(WorkOrderStatus.CREATED.name());
        assertThat(created.getOrderNo()).startsWith("WO-");
        assertThat(created.getDueTime()).isNotNull();
        assertThat(created.getId()).isNotNull();
    }

    @Test
    void createWorkOrder_nullPriority_defaultsToMedium() {
        WorkOrderEntity order = buildOrder();
        order.setPriority(null);
        WorkOrderEntity created = workOrderService.createWorkOrder(order);

        assertThat(created.getPriority()).isEqualTo(2);
        assertThat(created.getDueTime()).isNotNull();
    }

    @Test
    void assign_transitionsToAssigned_andSetsDeviceUnderRepair() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.assign(order.getId(), 10L, 1L);

        WorkOrderEntity updated = workOrderService.getById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(WorkOrderStatus.ASSIGNED.name());
        assertThat(updated.getAssigneeId()).isEqualTo(10L);
    }

    @Test
    void startProcessing_transitionsToProcessing() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.assign(order.getId(), 10L, 1L);
        workOrderService.startProcessing(order.getId(), 1L);

        WorkOrderEntity updated = workOrderService.getById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(WorkOrderStatus.PROCESSING.name());
    }

    @Test
    void close_transitionsToClosed_andResolutionRequired() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.assign(order.getId(), 10L, 1L);
        workOrderService.startProcessing(order.getId(), 1L);
        workOrderService.close(order.getId(), "Fixed the issue", 1L);

        WorkOrderEntity updated = workOrderService.getById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(WorkOrderStatus.CLOSED.name());
        assertThat(updated.getResolution()).isEqualTo("Fixed the issue");
    }

    @Test
    void close_withoutResolution_throws() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.assign(order.getId(), 10L, 1L);
        workOrderService.startProcessing(order.getId(), 1L);

        assertThatThrownBy(() -> workOrderService.close(order.getId(), "", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resolution is required");
    }

    @Test
    void cancel_fromCreated_transitionsToCancelled() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.cancel(order.getId(), "Not needed", 1L);

        WorkOrderEntity updated = workOrderService.getById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED.name());
    }

    @Test
    void cancel_fromProcessing_throws() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.assign(order.getId(), 10L, 1L);
        workOrderService.startProcessing(order.getId(), 1L);

        assertThatThrownBy(() -> workOrderService.cancel(order.getId(), "Too late", 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_withoutComment_throws() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());

        assertThatThrownBy(() -> workOrderService.cancel(order.getId(), "", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Comment is required");
    }

    @Test
    void assign_fromClosed_throws() {
        WorkOrderEntity order = workOrderService.createWorkOrder(buildOrder());
        workOrderService.assign(order.getId(), 10L, 1L);
        workOrderService.startProcessing(order.getId(), 1L);
        workOrderService.close(order.getId(), "Done", 1L);

        assertThatThrownBy(() -> workOrderService.assign(order.getId(), 20L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
