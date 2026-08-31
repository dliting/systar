package com.systar.system.mapper;

import com.systar.system.entity.SysRoleEntity;
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
class SysRoleMapperTest {

    @Autowired
    private SysRoleMapper mapper;

    private SysRoleEntity buildEntity() {
        SysRoleEntity entity = new SysRoleEntity();
        entity.setRoleName("Test Role");
        entity.setRoleKey("test_role_" + System.nanoTime());
        entity.setStatus(0);
        entity.setRemark("Test remark");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysRoleEntity entity = buildEntity();
        mapper.insert(entity);

        SysRoleEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getRoleName()).isEqualTo("Test Role");
        assertThat(found.getRoleKey()).startsWith("test_role_");
    }

    @Test
    void update() {
        SysRoleEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setRoleName("Updated Role");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getRoleName()).isEqualTo("Updated Role");
    }

    @Test
    void delete() {
        SysRoleEntity entity = buildEntity();
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
