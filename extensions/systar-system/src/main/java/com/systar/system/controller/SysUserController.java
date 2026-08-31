package com.systar.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.system.entity.SysUserEntity;
import com.systar.system.service.SysUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/user")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping
    @RequirePermission("sys:user:list")
    public Result<Page<SysUserEntity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId) {
        Page<SysUserEntity> result = sysUserService.listUsers(page, size, username, status, deptId);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:user:list")
    public Result<SysUserEntity> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getUserById(id));
    }

    @PostMapping
    @RequirePermission("sys:user:add")
    public Result<Void> create(@RequestBody SysUserEntity user) {
        sysUserService.createUser(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:user:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUserEntity user) {
        user.setId(id);
        sysUserService.updateUser(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:user:delete")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }

    @PutMapping("/{id}/password")
    @RequirePermission("sys:user:resetPwd")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sysUserService.resetPassword(id, body.get("password"));
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    @RequirePermission("sys:user:edit")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        sysUserService.assignRoles(id, body.get("roleIds"));
        return Result.success();
    }
}
