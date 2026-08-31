package com.systar.system.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.system.entity.SysDeptEntity;
import com.systar.system.service.SysDeptService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys/dept")
public class SysDeptController {

    private final SysDeptService sysDeptService;

    public SysDeptController(SysDeptService sysDeptService) {
        this.sysDeptService = sysDeptService;
    }

    @GetMapping("/tree")
    @RequirePermission("sys:dept:list")
    public Result<List<SysDeptEntity>> getTree() {
        return Result.success(sysDeptService.getDeptTree());
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:dept:list")
    public Result<SysDeptEntity> getById(@PathVariable Long id) {
        return Result.success(sysDeptService.getDeptById(id));
    }

    @PostMapping
    @RequirePermission("sys:dept:add")
    public Result<Void> create(@RequestBody SysDeptEntity dept) {
        sysDeptService.createDept(dept);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:dept:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysDeptEntity dept) {
        dept.setId(id);
        sysDeptService.updateDept(dept);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:dept:delete")
    public Result<Void> delete(@PathVariable Long id) {
        sysDeptService.deleteDept(id);
        return Result.success();
    }
}
