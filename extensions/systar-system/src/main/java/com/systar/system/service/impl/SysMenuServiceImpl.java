package com.systar.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.system.entity.SysMenuEntity;
import com.systar.system.entity.SysRoleMenuEntity;
import com.systar.system.mapper.SysMenuMapper;
import com.systar.system.mapper.SysRoleMenuMapper;
import com.systar.system.service.SysMenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenuEntity> implements SysMenuService {

    private static final Logger log = LoggerFactory.getLogger(SysMenuServiceImpl.class);

    private final SysRoleMenuMapper roleMenuMapper;

    public SysMenuServiceImpl(SysRoleMenuMapper roleMenuMapper) {
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public List<SysMenuEntity> getMenuTree() {
        List<SysMenuEntity> allMenus = list(new LambdaQueryWrapper<SysMenuEntity>()
                .orderByAsc(SysMenuEntity::getOrderNum));

        Map<Long, List<SysMenuEntity>> parentMap = allMenus.stream()
                .collect(Collectors.groupingBy(SysMenuEntity::getParentId));

        for (SysMenuEntity menu : allMenus) {
            List<SysMenuEntity> children = parentMap.getOrDefault(menu.getId(), Collections.emptyList());
            menu.setChildren(children);
        }

        return allMenus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0L)
                .collect(Collectors.toList());
    }

    @Override
    public SysMenuEntity getMenuById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public void createMenu(SysMenuEntity menu) {
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        save(menu);
        log.info("Created menu: {}", menu.getMenuName());
    }

    @Override
    @Transactional
    public void updateMenu(SysMenuEntity menu) {
        menu.setUpdateTime(LocalDateTime.now());
        updateById(menu);
        log.info("Updated menu id={}", menu.getId());
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        List<Long> idsToDelete = collectChildIds(id);
        idsToDelete.add(id);

        for (Long menuId : idsToDelete) {
            LambdaQueryWrapper<SysRoleMenuEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysRoleMenuEntity::getMenuId, menuId);
            roleMenuMapper.delete(wrapper);
        }

        removeByIds(idsToDelete);
        log.info("Deleted menu id={} and {} children", id, idsToDelete.size() - 1);
    }

    private List<Long> collectChildIds(Long parentId) {
        List<Long> childIds = new ArrayList<>();
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenuEntity::getParentId, parentId);
        List<SysMenuEntity> children = list(wrapper);

        for (SysMenuEntity child : children) {
            childIds.add(child.getId());
            childIds.addAll(collectChildIds(child.getId()));
        }
        return childIds;
    }

    @Override
    public List<String> getPermissionsByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<SysRoleMenuEntity> rmWrapper = new LambdaQueryWrapper<>();
        rmWrapper.in(SysRoleMenuEntity::getRoleId, roleIds);
        List<SysRoleMenuEntity> roleMenus = roleMenuMapper.selectList(rmWrapper);

        if (roleMenus == null || roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> menuIds = roleMenus.stream()
                .map(SysRoleMenuEntity::getMenuId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<SysMenuEntity> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.in(SysMenuEntity::getId, menuIds)
                .eq(SysMenuEntity::getStatus, 0);
        List<SysMenuEntity> menus = list(menuWrapper);

        return menus.stream()
                .map(SysMenuEntity::getPerms)
                .filter(perms -> perms != null && !perms.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<SysMenuEntity> getMenusByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<SysRoleMenuEntity> rmWrapper = new LambdaQueryWrapper<>();
        rmWrapper.in(SysRoleMenuEntity::getRoleId, roleIds);
        List<SysRoleMenuEntity> roleMenus = roleMenuMapper.selectList(rmWrapper);

        if (roleMenus == null || roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> menuIds = roleMenus.stream()
                .map(SysRoleMenuEntity::getMenuId)
                .distinct()
                .collect(Collectors.toList());

        return list(new LambdaQueryWrapper<SysMenuEntity>()
                .in(SysMenuEntity::getId, menuIds)
                .eq(SysMenuEntity::getStatus, 0)
                .orderByAsc(SysMenuEntity::getOrderNum));
    }
}
