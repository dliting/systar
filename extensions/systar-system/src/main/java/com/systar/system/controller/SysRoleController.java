package com.systar.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.system.entity.SysRoleEntity;
import com.systar.system.service.SysRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/role")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    public SysRoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    @GetMapping
    @RequirePermission("sys:role:list")
    public Result<Page<SysRoleEntity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String roleName) {
        Page<SysRoleEntity> result = sysRoleService.listRoles(page, size, roleName);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:role:list")
    public Result<SysRoleEntity> getById(@PathVariable Long id) {
        return Result.success(sysRoleService.getRoleById(id));
    }

    @PostMapping
    @RequirePermission("sys:role:add")
    public Result<Void> create(@RequestBody SysRoleEntity role) {
        sysRoleService.createRole(role);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:role:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRoleEntity role) {
        role.setId(id);
        sysRoleService.updateRole(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:role:delete")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @PutMapping("/{id}/menus")
    @RequirePermission("sys:role:edit")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        sysRoleService.assignMenus(id, body.get("menuIds"));
        return Result.success();
    }
}
