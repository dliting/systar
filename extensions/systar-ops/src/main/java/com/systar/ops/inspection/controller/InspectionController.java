package com.systar.ops.inspection.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.ops.inspection.InspectionTaskStatus;
import com.systar.ops.inspection.entity.InspectionItemTemplateEntity;
import com.systar.ops.inspection.entity.InspectionPlanDeviceEntity;
import com.systar.ops.inspection.entity.InspectionPlanEntity;
import com.systar.ops.inspection.entity.InspectionResultEntity;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
import com.systar.ops.inspection.service.InspectionPlanService;
import com.systar.ops.inspection.service.InspectionTaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/inspection")
public class InspectionController {

    private final InspectionPlanService planService;
    private final InspectionTaskService taskService;

    public InspectionController(InspectionPlanService planService, InspectionTaskService taskService) {
        this.planService = planService;
        this.taskService = taskService;
    }

    @GetMapping("/plans")
    public Page<InspectionPlanEntity> listPlans(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) Boolean enabled) {
        return planService.list(page, size, enabled);
    }

    @GetMapping("/plans/{id}")
    public Map<String, Object> getPlan(@PathVariable Long id) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("plan", planService.getRequiredPlan(id));
        detail.put("devices", planService.getPlanDevices(id));
        detail.put("items", planService.getTemplates(id));
        return detail;
    }

    @PostMapping("/plans")
    public InspectionPlanEntity createPlan(@RequestBody PlanRequest request) {
        return planService.create(requirePlan(request), request.deviceIds(), request.items());
    }

    @PutMapping("/plans/{id}")
    public InspectionPlanEntity updatePlan(@PathVariable Long id, @RequestBody PlanRequest request) {
        InspectionPlanEntity plan = requirePlan(request);
        plan.setId(id);
        return planService.update(plan, request.deviceIds(), request.items());
    }

    @DeleteMapping("/plans/{id}")
    public void deletePlan(@PathVariable Long id) {
        planService.delete(id);
    }

    @GetMapping("/plans/{id}/devices")
    public List<InspectionPlanDeviceEntity> getPlanDevices(@PathVariable Long id) {
        return planService.getPlanDevices(id);
    }

    @PostMapping("/plans/{id}/devices")
    public InspectionPlanDeviceEntity addPlanDevice(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return planService.addDevice(id, body.get("deviceId"));
    }

    @DeleteMapping("/plans/{id}/devices/{deviceId}")
    public void removePlanDevice(@PathVariable Long id, @PathVariable Integer deviceId) {
        planService.removeDevice(id, deviceId);
    }

    @GetMapping("/plans/{id}/items")
    public List<InspectionItemTemplateEntity> getPlanItems(@PathVariable Long id) {
        return planService.getTemplates(id);
    }

    @PostMapping("/plans/{id}/items")
    public InspectionItemTemplateEntity addPlanItem(@PathVariable Long id,
                                                    @RequestBody InspectionItemTemplateEntity item) {
        return planService.addTemplate(id, item);
    }

    @PutMapping("/plans/{id}/items/{itemId}")
    public InspectionItemTemplateEntity updatePlanItem(@PathVariable Long id,
                                                       @PathVariable Long itemId,
                                                       @RequestBody InspectionItemTemplateEntity item) {
        return planService.updateTemplate(id, itemId, item);
    }

    @DeleteMapping("/plans/{id}/items/{itemId}")
    public void deletePlanItem(@PathVariable Long id, @PathVariable Long itemId) {
        planService.deleteTemplate(id, itemId);
    }

    @GetMapping("/tasks")
    public Page<InspectionTaskEntity> listTasks(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long assigneeId) {
        return taskService.list(page, size, status, assigneeId);
    }

    @GetMapping("/tasks/{id}")
    public Map<String, Object> getTask(@PathVariable Long id) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("task", taskService.getById(id));
        detail.put("results", taskService.listResults(id));
        return detail;
    }

    @PutMapping("/tasks/{id}/start")
    public void startTask(@PathVariable Long id) {
        taskService.startTask(id);
    }

    @PostMapping("/tasks/{id}/results")
    public void submitResults(@PathVariable Long id, @RequestBody List<InspectionResultEntity> results) {
        taskService.submitResults(id, results);
    }

    @PutMapping("/tasks/{id}/results")
    public void updateResults(@PathVariable Long id, @RequestBody List<InspectionResultEntity> results) {
        taskService.submitResults(id, results);
    }

    @PutMapping("/tasks/{id}/complete")
    public void completeTask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        taskService.completeTask(id, body.get("remark"));
    }

    @PutMapping("/tasks/{id}/cancel")
    public void cancelTask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        taskService.cancelTask(id, body.get("remark"));
    }

    @PutMapping("/tasks/{id}/reassign")
    public InspectionTaskEntity reassignTask(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return taskService.reassignTask(id, body.get("assigneeId"));
    }

    @GetMapping("/task-stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", taskService.list(1, 1, InspectionTaskStatus.PENDING.name(), null).getTotal());
        stats.put("inProgress", taskService.list(1, 1, InspectionTaskStatus.IN_PROGRESS.name(), null).getTotal());
        stats.put("completed", taskService.list(1, 1, InspectionTaskStatus.COMPLETED.name(), null).getTotal());
        stats.put("cancelled", taskService.list(1, 1, InspectionTaskStatus.CANCELLED.name(), null).getTotal());
        return stats;
    }

    private InspectionPlanEntity requirePlan(PlanRequest request) {
        if (request == null || request.plan() == null) {
            throw new IllegalArgumentException("Inspection plan request body is required");
        }
        return request.plan();
    }

    public record PlanRequest(InspectionPlanEntity plan,
                              List<Integer> deviceIds,
                              List<InspectionItemTemplateEntity> items) {
    }
}
