package com.systar.system.mapper;

import com.systar.system.entity.SysOperLogEntity;
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
class SysOperLogMapperTest {

    @Autowired
    private SysOperLogMapper mapper;

    private SysOperLogEntity buildEntity() {
        SysOperLogEntity entity = new SysOperLogEntity();
        entity.setUserId(1L);
        entity.setUsername("admin");
        entity.setOperation("Query");
        entity.setMethod("com.systar.server.controller.TestController.list()");
        entity.setParams("{}");
        entity.setResult("success");
        entity.setIp("127.0.0.1");
        entity.setCostTime(50L);
        entity.setOperTime(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        SysOperLogEntity entity = buildEntity();
        mapper.insert(entity);

        SysOperLogEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("admin");
        assertThat(found.getOperation()).isEqualTo("Query");
    }

    @Test
    void update() {
        SysOperLogEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setResult("error");
        entity.setErrorMsg("NullPointerException");
        mapper.updateById(entity);

        SysOperLogEntity found = mapper.selectById(entity.getId());
        assertThat(found.getResult()).isEqualTo("error");
        assertThat(found.getErrorMsg()).isEqualTo("NullPointerException");
    }

    @Test
    void delete() {
        SysOperLogEntity entity = buildEntity();
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
