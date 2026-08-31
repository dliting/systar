package com.systar.system.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.system.entity.SysMenuEntity;
import com.systar.system.service.SysMenuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys/menu")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    @GetMapping("/tree")
    @RequirePermission("sys:menu:list")
    public Result<List<SysMenuEntity>> getTree() {
        return Result.success(sysMenuService.getMenuTree());
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:menu:list")
    public Result<SysMenuEntity> getById(@PathVariable Long id) {
        return Result.success(sysMenuService.getMenuById(id));
    }

    @PostMapping
    @RequirePermission("sys:menu:add")
    public Result<Void> create(@RequestBody SysMenuEntity menu) {
        sysMenuService.createMenu(menu);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:menu:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenuEntity menu) {
        menu.setId(id);
        sysMenuService.updateMenu(menu);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:menu:delete")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.deleteMenu(id);
        return Result.success();
    }
}
