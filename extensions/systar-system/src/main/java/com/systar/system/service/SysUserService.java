package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.system.entity.SysUserEntity;

import java.util.List;

/**
 * User management service.
 */
public interface SysUserService {

    /**
     * Paginated query with optional filters. Password is always set to null on results.
     */
    Page<SysUserEntity> listUsers(int page, int size, String username, Integer status, Long deptId);

    /**
     * Get user by ID, password null.
     */
    SysUserEntity getUserById(Long id);

    /**
     * Get user by username WITH password (for auth).
     */
    SysUserEntity getByUsername(String username);

    /**
     * Create user with BCrypt-encoded password.
     */
    void createUser(SysUserEntity user);

    /**
     * Update user non-null fields, NOT password.
     */
    void updateUser(SysUserEntity user);

    /**
     * Delete user and user-role associations.
     */
    void deleteUser(Long id);

    /**
     * Reset password with BCrypt encoding.
     */
    void resetPassword(Long id, String newPassword);

    /**
     * Assign roles to user (replace all existing).
     */
    void assignRoles(Long userId, List<Long> roleIds);
}
