package com.systar.system.mapper;

import com.systar.system.entity.SysDeptEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SysDeptMapperTest {

    @Autowired
    private SysDeptMapper mapper;

    private SysDeptEntity buildEntity() {
        SysDeptEntity entity = new SysDeptEntity();
        entity.setDeptName("Test Dept");
        entity.setParentId(0L);
        entity.setAncestors("0");
        entity.setOrderNum(1);
        entity.setStatus(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysDeptEntity entity = buildEntity();
        mapper.insert(entity);

        SysDeptEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getDeptName()).isEqualTo("Test Dept");
    }

    @Test
    void update() {
        SysDeptEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setDeptName("Updated Dept");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getDeptName()).isEqualTo("Updated Dept");
    }

    @Test
    void delete() {
        SysDeptEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        mapper.insert(buildEntity());
        mapper.insert(buildEntity());

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
