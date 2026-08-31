package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.control.*;
import com.systar.server.controller.vo.ScheduledTaskLogVO;
import com.systar.server.controller.vo.ScheduledTaskVO;
import com.systar.server.dto.ScheduledTaskCreateRequest;
import com.systar.server.dto.ScheduledTaskUpdateRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitor/scheduled-tasks")
public class ScheduledTaskController {

    private static final int DEFAULT_LOG_LIMIT = 50;
    private static final int MAX_LOG_LIMIT     = 500;

    private final ScheduledTaskRepository    taskRepo;
    private final ScheduledTaskLogRepository logRepo;
    private final TimeControlService         timeControlService;
    private final AssetStore                 assetStore;

    public ScheduledTaskController(ScheduledTaskRepository taskRepo,
                                   ScheduledTaskLogRepository logRepo,
                                   TimeControlService timeControlService,
                                   AssetStore assetStore) {
        this.taskRepo           = taskRepo;
        this.logRepo            = logRepo;
        this.timeControlService = timeControlService;
        this.assetStore         = assetStore;
    }

    @RequirePermission("iot:task:list")
    @GetMapping
    public Result<List<ScheduledTaskVO>> listTasks(
            @RequestParam(required = false) Integer controlId,
            @RequestParam(required = false) String keyword) {
        List<ScheduledTask> tasks = taskRepo.findAll();
        if (controlId != null) {
            tasks = tasks.stream()
                    .filter(t -> t.getControlId() == controlId)
                    .collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            tasks = tasks.stream()
                    .filter(t -> t.getName() != null && t.getName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }
        return Result.success(tasks.stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
    }

    @RequirePermission("iot:task:query")
    @GetMapping("/{id}")
    public Result<ScheduledTaskVO> getTask(@PathVariable int id) {
        ScheduledTask task = taskRepo.findById(id);
        if (task == null) {
            return Result.error(Result.CODE_NOT_FOUND,
                    "Scheduled task not found: " + id);
        }
        return Result.success(toVO(task));
    }

    @RequirePermission("iot:task:add")
    @PostMapping
    public Result<Integer> createTask(@RequestBody ScheduledTaskCreateRequest request) {
        if (request == null) {
            return Result.error(Result.CODE_BAD_REQUEST, "Request body is required.");
        }
        String validationError = validateCreate(request);
        if (validationError != null) {
            return Result.error(Result.CODE_BAD_REQUEST, validationError);
        }

        ScheduledTask task = new ScheduledTask();
        task.setName(request.getName());
        task.setControlId(request.getControlId());
        task.setCommand(request.getCommand());
        task.setCronExpression(request.getCronExpression());
        task.setDescription(request.getDescription());
        task.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        taskRepo.save(task);
        timeControlService.addTask(task);
        return Result.success(task.getId());
    }

    @RequirePermission("iot:task:edit")
    @PutMapping("/{id}")
    public Result<Void> updateTask(@PathVariable int id,
                                   @RequestBody ScheduledTaskUpdateRequest request) {
        ScheduledTask existing = taskRepo.findById(id);
        if (existing == null) {
            return Result.error(Result.CODE_NOT_FOUND,
                    "Scheduled task not found: " + id);
        }
        if (request.getCronExpression() != null
                && !request.getCronExpression().isBlank()) {
            try {
                CronExpression.parse(request.getCronExpression());
            } catch (IllegalArgumentException e) {
                return Result.error(Result.CODE_BAD_REQUEST,
                        "Invalid cron expression: " + request.getCronExpression());
            }
        }

        if (request.getName() != null)             existing.setName(request.getName());
        if (request.getControlId() != null)         existing.setControlId(request.getControlId());
        if (request.getCommand() != null)           existing.setCommand(request.getCommand());
        if (request.getCronExpression() != null)    existing.setCronExpression(request.getCronExpression());
        existing.setDescription(request.getDescription());

        taskRepo.update(existing);
        timeControlService.removeTask(id);
        timeControlService.addTask(existing);
        return Result.success();
    }

    @RequirePermission("iot:task:delete")
    @DeleteMapping("/{id}")
    public Result<Void> deleteTask(@PathVariable int id) {
        timeControlService.removeTask(id);
        taskRepo.deleteById(id);
        return Result.success();
    }

    @RequirePermission("iot:task:query")
    @GetMapping("/cron-preview")
    public Result<LocalDateTime> previewCron(@RequestParam String expression) {
        try {
            CronExpression cron = CronExpression.parse(expression);
            return Result.success(cron.next(LocalDateTime.now()));
        } catch (IllegalArgumentException e) {
            return Result.error(Result.CODE_BAD_REQUEST, "Invalid cron expression");
        }
    }

    @RequirePermission("iot:task:enable")
    @PutMapping("/{id}/enable")
    public Result<Void> enableTask(@PathVariable int id) {
        timeControlService.enableTask(id);
        return Result.success();
    }

    @RequirePermission("iot:task:disable")
    @PutMapping("/{id}/disable")
    public Result<Void> disableTask(@PathVariable int id) {
        timeControlService.disableTask(id);
        return Result.success();
    }

    @RequirePermission("iot:task:list")
    @GetMapping("/{id}/logs")
    public Result<List<ScheduledTaskLogVO>> getTaskLogs(
            @PathVariable int id,
            @RequestParam(required = false) Integer limit) {
        int effectiveLimit = limit != null
                ? Math.min(Math.max(1, limit), MAX_LOG_LIMIT)
                : DEFAULT_LOG_LIMIT;
        List<ScheduledTaskLog> logs = logRepo.findRecent(id, effectiveLimit);
        return Result.success(logs.stream()
                .map(this::toLogVO)
                .collect(Collectors.toList()));
    }

    // ======================== helpers ========================

    private String validateCreate(ScheduledTaskCreateRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return "Task name is required.";
        }
        if (req.getCommand() == null || req.getCommand().isBlank()) {
            return "Command is required.";
        }
        if (req.getCronExpression() == null || req.getCronExpression().isBlank()) {
            return "Cron expression is required.";
        }
        if (req.getControlId() <= 0) {
            return "Control ID must be positive.";
        }
        try {
            CronExpression.parse(req.getCronExpression());
        } catch (IllegalArgumentException e) {
            return "Invalid cron expression: " + req.getCronExpression();
        }
        return null;
    }

    private ScheduledTaskVO toVO(ScheduledTask task) {
        ScheduledTaskVO vo = new ScheduledTaskVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setControlId(task.getControlId());
        vo.setCommand(task.getCommand());
        vo.setCronExpression(task.getCronExpression());
        vo.setEnabled(task.isEnabled());
        vo.setDescription(task.getDescription());
        Asset<?> asset = assetStore.findAsset(task.getControlId());
        if (asset != null) {
            vo.setTargetName(asset.getCaption() != null ? asset.getCaption() : asset.getName());
        }
        if (task.isEnabled() && task.getCronExpression() != null) {
            try {
                CronExpression cron = CronExpression.parse(task.getCronExpression());
                vo.setNextFireTime(cron.next(LocalDateTime.now()));
            } catch (IllegalArgumentException ignored) {
                // invalid cron — leave nextFireTime null
            }
        }
        return vo;
    }

    private ScheduledTaskLogVO toLogVO(ScheduledTaskLog log) {
        ScheduledTaskLogVO vo = new ScheduledTaskLogVO();
        vo.setId(log.getId());
        vo.setTaskId(log.getTaskId());
        vo.setTaskName(log.getTaskName());
        vo.setControlId(log.getControlId());
        vo.setCommand(log.getCommand());
        vo.setExecuteTime(log.getExecuteTime());
        vo.setSuccess(log.isSuccess());
        vo.setErrorMessage(log.getErrorMessage());
        return vo;
    }
}
