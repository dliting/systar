package com.systar.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.system.entity.SysRoleEntity;
import com.systar.system.entity.SysRoleMenuEntity;
import com.systar.system.entity.SysUserRoleEntity;
import com.systar.system.mapper.SysRoleMapper;
import com.systar.system.mapper.SysRoleMenuMapper;
import com.systar.system.mapper.SysUserRoleMapper;
import com.systar.system.service.SysRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRoleEntity> implements SysRoleService {

    private static final Logger log = LoggerFactory.getLogger(SysRoleServiceImpl.class);

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    public SysRoleServiceImpl(SysRoleMenuMapper roleMenuMapper, SysUserRoleMapper userRoleMapper) {
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public Page<SysRoleEntity> listRoles(int page, int size, String roleName) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<>();
        if (roleName != null && !roleName.isEmpty()) {
            wrapper.like(SysRoleEntity::getRoleName, roleName);
        }
        wrapper.orderByAsc(SysRoleEntity::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public SysRoleEntity getRoleById(Long id) {
        SysRoleEntity role = getById(id);
        if (role != null) {
            role.setMenuIds(getMenuIdsByRoleId(id));
        }
        return role;
    }

    @Override
    @Transactional
    public void createRole(SysRoleEntity role) {
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        save(role);
        log.info("Created role: {}", role.getRoleName());
    }

    @Override
    @Transactional
    public void updateRole(SysRoleEntity role) {
        role.setUpdateTime(LocalDateTime.now());
        updateById(role);
        log.info("Updated role id={}", role.getId());
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        LambdaQueryWrapper<SysRoleMenuEntity> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.eq(SysRoleMenuEntity::getRoleId, id);
        roleMenuMapper.delete(menuWrapper);

        LambdaQueryWrapper<SysUserRoleEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUserRoleEntity::getRoleId, id);
        userRoleMapper.delete(userWrapper);

        removeById(id);
        log.info("Deleted role id={}", id);
    }

    @Override
    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        LambdaQueryWrapper<SysRoleMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenuEntity::getRoleId, roleId);
        roleMenuMapper.delete(wrapper);

        for (Long menuId : menuIds) {
            SysRoleMenuEntity rm = new SysRoleMenuEntity();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
        log.info("Assigned {} menus to role id={}", menuIds.size(), roleId);
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        LambdaQueryWrapper<SysRoleMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenuEntity::getRoleId, roleId);
        List<SysRoleMenuEntity> list = roleMenuMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(SysRoleMenuEntity::getMenuId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRoleEntity::getUserId, userId);
        List<SysUserRoleEntity> list = userRoleMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(SysUserRoleEntity::getRoleId)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getRoleKeysByUserId(Long userId) {
        List<Long> roleIds = getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysRoleEntity::getId, roleIds);
        wrapper.eq(SysRoleEntity::getStatus, 0);
        List<SysRoleEntity> roles = list(wrapper);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(SysRoleEntity::getRoleKey)
                .collect(Collectors.toList());
    }
}
