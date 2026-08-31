package com.systar.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.system.entity.SysNoticeEntity;
import com.systar.system.service.SysNoticeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys/notice")
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    public SysNoticeController(SysNoticeService sysNoticeService) {
        this.sysNoticeService = sysNoticeService;
    }

    @GetMapping
    @RequirePermission("sys:notice:list")
    public Result<Page<SysNoticeEntity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status) {
        Page<SysNoticeEntity> result = sysNoticeService.listNotices(page, size, title, status);
        return Result.success(result);
    }

    @GetMapping("/active")
    public Result<List<SysNoticeEntity>> getActive() {
        return Result.success(sysNoticeService.getActiveNotices());
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:notice:list")
    public Result<SysNoticeEntity> getById(@PathVariable Long id) {
        return Result.success(sysNoticeService.getNoticeById(id));
    }

    @PostMapping
    @RequirePermission("sys:notice:add")
    public Result<Void> create(@RequestBody SysNoticeEntity notice) {
        sysNoticeService.createNotice(notice);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:notice:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysNoticeEntity notice) {
        notice.setId(id);
        sysNoticeService.updateNotice(notice);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:notice:delete")
    public Result<Void> delete(@PathVariable Long id) {
        sysNoticeService.deleteNotice(id);
        return Result.success();
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("sys:notice:edit")
    public Result<Void> publish(@PathVariable Long id) {
        sysNoticeService.publishNotice(id);
        return Result.success();
    }
}
