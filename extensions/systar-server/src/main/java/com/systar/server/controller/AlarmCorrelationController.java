package com.systar.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.data.entity.AlarmCorrelationRuleEntity;
import com.systar.data.entity.AlarmEscalationPolicyEntity;
import com.systar.data.entity.AlarmSilenceWindowEntity;
import com.systar.data.service.AlarmCorrelationRuleService;
import com.systar.data.service.AlarmEscalationPolicyService;
import com.systar.data.service.AlarmSilenceWindowService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class AlarmCorrelationController {

    private static final int DEFAULT_PAGE_SIZE_MAX = 500;

    private final AlarmCorrelationRuleService correlationRuleService;
    private final AlarmEscalationPolicyService escalationPolicyService;
    private final AlarmSilenceWindowService silenceWindowService;

    public AlarmCorrelationController(AlarmCorrelationRuleService correlationRuleService,
                                       AlarmEscalationPolicyService escalationPolicyService,
                                       AlarmSilenceWindowService silenceWindowService) {
        this.correlationRuleService   = correlationRuleService;
        this.escalationPolicyService  = escalationPolicyService;
        this.silenceWindowService     = silenceWindowService;
    }

    // ======================== correlation rules ========================

    @RequirePermission("iot:alarm:query")
    @GetMapping("/correlation-rules")
    public Result<Map<String, Object>> getCorrelationRules(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return paginated(correlationRuleService, page, size,
                new QueryWrapper<AlarmCorrelationRuleEntity>().orderByDesc("id"));
    }

    @RequirePermission("iot:alarm:edit")
    @PostMapping("/correlation-rules")
    public Result<AlarmCorrelationRuleEntity> addCorrelationRule(
            @RequestBody AlarmCorrelationRuleEntity entity) {
        correlationRuleService.save(entity);
        return Result.success(entity);
    }

    @RequirePermission("iot:alarm:edit")
    @PutMapping("/correlation-rules/{id}")
    public Result<AlarmCorrelationRuleEntity> updateCorrelationRule(
            @PathVariable Integer id,
            @RequestBody AlarmCorrelationRuleEntity entity) {
        entity.setId(id);
        correlationRuleService.updateById(entity);
        return Result.success(entity);
    }

    @RequirePermission("iot:alarm:edit")
    @DeleteMapping("/correlation-rules/{id}")
    public Result<Void> deleteCorrelationRule(@PathVariable Integer id) {
        correlationRuleService.removeById(id);
        return Result.success(null);
    }

    // ======================== escalation policies ========================

    @RequirePermission("iot:alarm:query")
    @GetMapping("/escalation-policies")
    public Result<Map<String, Object>> getEscalationPolicies(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return paginated(escalationPolicyService, page, size,
                new QueryWrapper<AlarmEscalationPolicyEntity>().orderByDesc("id"));
    }

    @RequirePermission("iot:alarm:edit")
    @PostMapping("/escalation-policies")
    public Result<AlarmEscalationPolicyEntity> addEscalationPolicy(
            @RequestBody AlarmEscalationPolicyEntity entity) {
        escalationPolicyService.save(entity);
        return Result.success(entity);
    }

    @RequirePermission("iot:alarm:edit")
    @PutMapping("/escalation-policies/{id}")
    public Result<AlarmEscalationPolicyEntity> updateEscalationPolicy(
            @PathVariable Integer id,
            @RequestBody AlarmEscalationPolicyEntity entity) {
        entity.setId(id);
        escalationPolicyService.updateById(entity);
        return Result.success(entity);
    }

    @RequirePermission("iot:alarm:edit")
    @DeleteMapping("/escalation-policies/{id}")
    public Result<Void> deleteEscalationPolicy(@PathVariable Integer id) {
        escalationPolicyService.removeById(id);
        return Result.success(null);
    }

    // ======================== silence windows ========================

    @RequirePermission("iot:alarm:query")
    @GetMapping("/silence-windows")
    public Result<Map<String, Object>> getSilenceWindows(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer enabled) {
        QueryWrapper<AlarmSilenceWindowEntity> qw =
                new QueryWrapper<AlarmSilenceWindowEntity>().orderByDesc("id");
        if (enabled != null) {
            qw.eq("enabled", enabled);
        }
        return paginated(silenceWindowService, page, size, qw);
    }

    @RequirePermission("iot:alarm:edit")
    @PostMapping("/silence-windows")
    public Result<AlarmSilenceWindowEntity> addSilenceWindow(
            @RequestBody AlarmSilenceWindowEntity entity) {
        silenceWindowService.save(entity);
        return Result.success(entity);
    }

    @RequirePermission("iot:alarm:edit")
    @PutMapping("/silence-windows/{id}")
    public Result<AlarmSilenceWindowEntity> updateSilenceWindow(
            @PathVariable Integer id,
            @RequestBody AlarmSilenceWindowEntity entity) {
        entity.setId(id);
        silenceWindowService.updateById(entity);
        return Result.success(entity);
    }

    @RequirePermission("iot:alarm:edit")
    @DeleteMapping("/silence-windows/{id}")
    public Result<Void> deleteSilenceWindow(@PathVariable Integer id) {
        silenceWindowService.removeById(id);
        return Result.success(null);
    }

    // ======================== helper ========================

    private <T> Result<Map<String, Object>> paginated(
            com.baomidou.mybatisplus.extension.service.IService<T> service,
            int page, int size, QueryWrapper<T> qw) {
        if (page < 1) {
            return Result.error("Page number must be >= 1");
        }
        if (size < 1 || size > DEFAULT_PAGE_SIZE_MAX) {
            return Result.error("Page size must be between 1 and " + DEFAULT_PAGE_SIZE_MAX);
        }
        Page<T> pageResult = service.page(new Page<>(page, size), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("total",  pageResult.getTotal());
        data.put("page",   pageResult.getCurrent());
        data.put("size",   pageResult.getSize());
        data.put("records", pageResult.getRecords());
        return Result.success(data);
    }
}