package com.systar.system.mapper;

import com.systar.system.entity.SysRoleMenuEntity;
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
class SysRoleMenuMapperTest {

    @Autowired
    private SysRoleMenuMapper mapper;

    private SysRoleMenuEntity buildEntity() {
        SysRoleMenuEntity entity = new SysRoleMenuEntity();
        entity.setRoleId(9999L);
        entity.setMenuId(9999L);
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysRoleMenuEntity entity = buildEntity();
        mapper.insert(entity);

        SysRoleMenuEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getRoleId()).isEqualTo(9999L);
        assertThat(found.getMenuId()).isEqualTo(9999L);
    }

    // No update test: join-table has no mutable business fields

    @Test
    void delete() {
        SysRoleMenuEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        SysRoleMenuEntity e1 = buildEntity();
        mapper.insert(e1);

        SysRoleMenuEntity e2 = new SysRoleMenuEntity();
        e2.setRoleId(9998L);
        e2.setMenuId(9998L);
        mapper.insert(e2);

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
