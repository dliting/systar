package com.systar.system.mapper;

import com.systar.system.entity.SysMenuEntity;
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
class SysMenuMapperTest {

    @Autowired
    private SysMenuMapper mapper;

    private SysMenuEntity buildEntity() {
        SysMenuEntity entity = new SysMenuEntity();
        entity.setMenuName("Test Menu");
        entity.setParentId(0L);
        entity.setPath("/test");
        entity.setComponent("test/index");
        entity.setMenuType(SysMenuEntity.TYPE_MENU);
        entity.setOrderNum(1);
        entity.setStatus(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysMenuEntity entity = buildEntity();
        mapper.insert(entity);

        SysMenuEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getMenuName()).isEqualTo("Test Menu");
    }

    @Test
    void update() {
        SysMenuEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setMenuName("Updated Menu");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getMenuName()).isEqualTo("Updated Menu");
    }

    @Test
    void delete() {
        SysMenuEntity entity = buildEntity();
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
