package com.systar.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.system.entity.SysDeptEntity;
import com.systar.system.mapper.SysDeptMapper;
import com.systar.system.service.SysDeptService;
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
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDeptEntity> implements SysDeptService {

    private static final Logger log = LoggerFactory.getLogger(SysDeptServiceImpl.class);

    @Override
    public List<SysDeptEntity> getDeptTree() {
        List<SysDeptEntity> allDepts = list(new LambdaQueryWrapper<SysDeptEntity>()
                .orderByAsc(SysDeptEntity::getOrderNum));

        Map<Long, List<SysDeptEntity>> parentMap = allDepts.stream()
                .collect(Collectors.groupingBy(SysDeptEntity::getParentId));

        for (SysDeptEntity dept : allDepts) {
            List<SysDeptEntity> children = parentMap.getOrDefault(dept.getId(), Collections.emptyList());
            dept.setChildren(children);
        }

        return allDepts.stream()
                .filter(d -> d.getParentId() == null || d.getParentId() == 0L)
                .collect(Collectors.toList());
    }

    @Override
    public SysDeptEntity getDeptById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public void createDept(SysDeptEntity dept) {
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());

        if (dept.getParentId() != null && dept.getParentId() != 0L) {
            SysDeptEntity parent = getById(dept.getParentId());
            if (parent != null) {
                dept.setAncestors(parent.getAncestors() + "," + dept.getParentId());
            } else {
                dept.setAncestors("0");
            }
        } else {
            dept.setAncestors("0");
            dept.setParentId(0L);
        }

        save(dept);
        log.info("Created dept: {}", dept.getDeptName());
    }

    @Override
    @Transactional
    public void updateDept(SysDeptEntity dept) {
        dept.setUpdateTime(LocalDateTime.now());
        updateById(dept);
        log.info("Updated dept id={}", dept.getId());
    }

    @Override
    @Transactional
    public void deleteDept(Long id) {
        List<Long> idsToDelete = collectChildIds(id);
        idsToDelete.add(id);
        removeByIds(idsToDelete);
        log.info("Deleted dept id={} and {} children", id, idsToDelete.size() - 1);
    }

    private List<Long> collectChildIds(Long parentId) {
        List<Long> childIds = new ArrayList<>();
        LambdaQueryWrapper<SysDeptEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDeptEntity::getParentId, parentId);
        List<SysDeptEntity> children = list(wrapper);

        for (SysDeptEntity child : children) {
            childIds.add(child.getId());
            childIds.addAll(collectChildIds(child.getId()));
        }
        return childIds;
    }
}
