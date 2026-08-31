package com.systar.system.service;

import com.systar.system.entity.SysMenuEntity;

import java.util.List;

/**
 * Menu management service.
 */
public interface SysMenuService {

    /**
     * Get all menus as a tree structure (flat list of root nodes with children populated).
     */
    List<SysMenuEntity> getMenuTree();

    SysMenuEntity getMenuById(Long id);

    void createMenu(SysMenuEntity menu);

    void updateMenu(SysMenuEntity menu);

    /**
     * Delete menu and all child menus recursively.
     */
    void deleteMenu(Long id);

    /**
     * Get distinct non-null non-empty permission strings for given role IDs.
     */
    List<String> getPermissionsByRoleIds(List<Long> roleIds);

    /**
     * Get menu entities for given role IDs.
     */
    List<SysMenuEntity> getMenusByRoleIds(List<Long> roleIds);
}
