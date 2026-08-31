package com.systar.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.entity.AlarmRuleEntity;
import com.systar.data.service.AlarmMessageService;
import com.systar.data.service.AlarmRuleService;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class AlarmController {

    private static final int DEFAULT_PAGE_SIZE_MAX = 500;

    private final AlarmRuleService alarmRuleService;
    private final AlarmMessageService alarmMessageService;

    public AlarmController(AlarmRuleService alarmRuleService,
                           AlarmMessageService alarmMessageService) {
        this.alarmRuleService = alarmRuleService;
        this.alarmMessageService = alarmMessageService;
    }

    @RequirePermission("iot:alarm:query")
    @GetMapping("/alarm-rules")
    public Result<List<AlarmRuleEntity>> getAlarmRules() {
        return Result.success(alarmRuleService.list());
    }

    @RequirePermission("iot:alarm:query")
    @GetMapping("/alarm-messages")
    public Result<Map<String, Object>> getAlarmMessages(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) Integer recovered) {
        if (page < 1) {
            return Result.error("Page number must be >= 1");
        }
        if (size < 1 || size > DEFAULT_PAGE_SIZE_MAX) {
            return Result.error("Page size must be between 1 and " + DEFAULT_PAGE_SIZE_MAX);
        }
        QueryWrapper<AlarmMessageEntity> qw = new QueryWrapper<AlarmMessageEntity>()
                .orderByDesc("id");
        if (state != null) {
            qw.eq("state", state);
        }
        if (recovered != null) {
            qw.eq("recovered", recovered);
        }
        Page<AlarmMessageEntity> pageResult = alarmMessageService.page(
                new Page<>(page, size), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("total", pageResult.getTotal());
        data.put("page", pageResult.getCurrent());
        data.put("size", pageResult.getSize());
        data.put("records", pageResult.getRecords());
        return Result.success(data);
    }
}
