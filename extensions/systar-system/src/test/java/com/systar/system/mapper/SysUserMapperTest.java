package com.systar.system.mapper;

import com.systar.system.entity.SysUserEntity;
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
class SysUserMapperTest {

    @Autowired
    private SysUserMapper mapper;

    private SysUserEntity buildEntity() {
        SysUserEntity entity = new SysUserEntity();
        entity.setUsername("testuser_" + System.nanoTime());
        entity.setPassword("hashed_password");
        entity.setNickname("Test User");
        entity.setEmail("test@example.com");
        entity.setPhone("13800138000");
        entity.setDeptId(1L);
        entity.setStatus(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysUserEntity entity = buildEntity();
        mapper.insert(entity);

        SysUserEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getNickname()).isEqualTo("Test User");
    }

    @Test
    void update() {
        SysUserEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setNickname("Updated User");
        entity.setEmail("updated@example.com");
        mapper.updateById(entity);

        SysUserEntity found = mapper.selectById(entity.getId());
        assertThat(found.getNickname()).isEqualTo("Updated User");
        assertThat(found.getEmail()).isEqualTo("updated@example.com");
    }

    @Test
    void delete() {
        SysUserEntity entity = buildEntity();
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
