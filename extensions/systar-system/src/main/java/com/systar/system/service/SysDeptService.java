package com.systar.system.service;

import com.systar.system.entity.SysDeptEntity;

import java.util.List;

/**
 * Department management service.
 */
public interface SysDeptService {

    /**
     * Get all departments as a tree structure.
     */
    List<SysDeptEntity> getDeptTree();

    SysDeptEntity getDeptById(Long id);

    void createDept(SysDeptEntity dept);

    void updateDept(SysDeptEntity dept);

    /**
     * Delete department and all child departments recursively.
     */
    void deleteDept(Long id);
}
