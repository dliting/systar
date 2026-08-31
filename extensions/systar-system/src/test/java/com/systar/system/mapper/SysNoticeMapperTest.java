package com.systar.system.mapper;

import com.systar.system.entity.SysNoticeEntity;
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
class SysNoticeMapperTest {

    @Autowired
    private SysNoticeMapper mapper;

    private SysNoticeEntity buildEntity() {
        SysNoticeEntity entity = new SysNoticeEntity();
        entity.setTitle("Test Notice");
        entity.setContent("Test content");
        entity.setType(SysNoticeEntity.TYPE_NOTICE);
        entity.setStatus(SysNoticeEntity.STATUS_DRAFT);
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysNoticeEntity entity = buildEntity();
        mapper.insert(entity);

        SysNoticeEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test Notice");
    }

    @Test
    void update() {
        SysNoticeEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setTitle("Updated Notice");
        entity.setStatus(SysNoticeEntity.STATUS_PUBLISHED);
        mapper.updateById(entity);

        SysNoticeEntity found = mapper.selectById(entity.getId());
        assertThat(found.getTitle()).isEqualTo("Updated Notice");
        assertThat(found.getStatus()).isEqualTo(SysNoticeEntity.STATUS_PUBLISHED);
    }

    @Test
    void delete() {
        SysNoticeEntity entity = buildEntity();
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
