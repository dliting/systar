package com.systar.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.system.entity.SysOperLogEntity;
import com.systar.system.service.SysOperLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/sys/log")
public class SysLogController {

    private final SysOperLogService sysOperLogService;

    public SysLogController(SysOperLogService sysOperLogService) {
        this.sysOperLogService = sysOperLogService;
    }

    @GetMapping
    @RequirePermission("sys:log:list")
    public Result<Page<SysOperLogEntity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Page<SysOperLogEntity> result = sysOperLogService.listLogs(page, size, username, startTime, endTime);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:log:list")
    public Result<SysOperLogEntity> getById(@PathVariable Long id) {
        return Result.success(sysOperLogService.getLogById(id));
    }
}
