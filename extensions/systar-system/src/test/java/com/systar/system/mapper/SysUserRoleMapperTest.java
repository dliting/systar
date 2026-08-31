package com.systar.system.mapper;

import com.systar.system.entity.SysUserRoleEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SysUserRoleMapperTest {

    @Autowired
    private SysUserRoleMapper mapper;

    private SysUserRoleEntity buildEntity() {
        SysUserRoleEntity entity = new SysUserRoleEntity();
        entity.setUserId(9999L);
        entity.setRoleId(9999L);
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysUserRoleEntity entity = buildEntity();
        mapper.insert(entity);

        SysUserRoleEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getUserId()).isEqualTo(9999L);
        assertThat(found.getRoleId()).isEqualTo(9999L);
    }

    // No update test: join-table has no mutable business fields

    @Test
    void delete() {
        SysUserRoleEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        SysUserRoleEntity e1 = buildEntity();
        mapper.insert(e1);

        SysUserRoleEntity e2 = new SysUserRoleEntity();
        e2.setUserId(9998L);
        e2.setRoleId(9998L);
        mapper.insert(e2);

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
