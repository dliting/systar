package com.systar.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.system.entity.SysUserEntity;
import com.systar.system.entity.SysUserRoleEntity;
import com.systar.system.mapper.SysUserMapper;
import com.systar.system.mapper.SysUserRoleMapper;
import com.systar.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUserEntity> implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    private final SysUserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public Page<SysUserEntity> listUsers(int page, int size, String username, Integer status, Long deptId) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.like(SysUserEntity::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(SysUserEntity::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(SysUserEntity::getDeptId, deptId);
        }
        wrapper.orderByAsc(SysUserEntity::getId);
        Page<SysUserEntity> result = page(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    @Override
    public SysUserEntity getUserById(Long id) {
        SysUserEntity user = getById(id);
        if (user != null) {
            user.setPassword(null);
            LambdaQueryWrapper<SysUserRoleEntity> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(SysUserRoleEntity::getUserId, id);
            List<SysUserRoleEntity> roles = userRoleMapper.selectList(roleWrapper);
            if (roles != null && !roles.isEmpty()) {
                user.setRoleIds(roles.stream()
                        .map(SysUserRoleEntity::getRoleId)
                        .collect(Collectors.toList()));
            } else {
                user.setRoleIds(Collections.emptyList());
            }
        }
        return user;
    }

    @Override
    public SysUserEntity getByUsername(String username) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserEntity::getUsername, username);
        return getOne(wrapper, false);
    }

    @Override
    @Transactional
    public void createUser(SysUserEntity user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
        log.info("Created user: {}", user.getUsername());
    }

    @Override
    @Transactional
    public void updateUser(SysUserEntity user) {
        user.setPassword(null);
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
        log.info("Updated user id={}", user.getId());
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        LambdaQueryWrapper<SysUserRoleEntity> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysUserRoleEntity::getUserId, id);
        userRoleMapper.delete(roleWrapper);
        removeById(id);
        log.info("Deleted user id={}", id);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        LambdaUpdateWrapper<SysUserEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUserEntity::getId, id)
                .set(SysUserEntity::getPassword, passwordEncoder.encode(newPassword))
                .set(SysUserEntity::getUpdateTime, LocalDateTime.now());
        update(wrapper);
        log.info("Reset password for user id={}", id);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        LambdaQueryWrapper<SysUserRoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRoleEntity::getUserId, userId);
        userRoleMapper.delete(wrapper);

        for (Long roleId : roleIds) {
            SysUserRoleEntity ur = new SysUserRoleEntity();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        log.info("Assigned {} roles to user id={}", roleIds.size(), userId);
    }
}
