package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.system.entity.SysRoleEntity;

import java.util.List;

/**
 * Role management service.
 */
public interface SysRoleService {

    Page<SysRoleEntity> listRoles(int page, int size, String roleName);

    SysRoleEntity getRoleById(Long id);

    void createRole(SysRoleEntity role);

    void updateRole(SysRoleEntity role);

    void deleteRole(Long id);

    void assignMenus(Long roleId, List<Long> menuIds);

    List<Long> getMenuIdsByRoleId(Long roleId);

    List<Long> getRoleIdsByUserId(Long userId);

    List<String> getRoleKeysByUserId(Long userId);
}
