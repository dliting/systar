package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
class SysOperLogServiceImplTest {

    @Autowired
    private SysOperLogService operLogService;

    private SysOperLogEntity buildEntity() {
        SysOperLogEntity entity = new SysOperLogEntity();
        entity.setUserId(1L);
        entity.setUsername("admin");
        entity.setOperation("Query");
        entity.setMethod("com.systar.Test.list()");
        entity.setParams("{}");
        entity.setResult("success");
        entity.setIp("127.0.0.1");
        entity.setCostTime(50L);
        return entity;
    }

    @Test
    void saveLog_shouldSetOperTime() {
        SysOperLogEntity entity = buildEntity();
        operLogService.saveLog(entity);

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getOperTime()).isNotNull();
    }

    @Test
    void getLogById_shouldReturnSaved() {
        SysOperLogEntity entity = buildEntity();
        operLogService.saveLog(entity);

        SysOperLogEntity found = operLogService.getLogById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("admin");
    }

    @Test
    void listLogs_shouldReturnPagedResult() {
        operLogService.saveLog(buildEntity());
        operLogService.saveLog(buildEntity());

        Page<SysOperLogEntity> page = operLogService.listLogs(1, 10, null, null, null);
        assertThat(page.getRecords()).isNotEmpty();
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listLogs_shouldFilterByUsername() {
        SysOperLogEntity entity = buildEntity();
        entity.setUsername("specific_user");
        operLogService.saveLog(entity);

        Page<SysOperLogEntity> page = operLogService.listLogs(1, 10, "specific_user", null, null);
        assertThat(page.getRecords()).isNotEmpty();
        assertThat(page.getRecords().get(0).getUsername()).isEqualTo("specific_user");
    }

    @Test
    void listLogs_shouldFilterByTimeRange() {
        operLogService.saveLog(buildEntity());

        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end   = LocalDateTime.now().plusHours(1);
        Page<SysOperLogEntity> page = operLogService.listLogs(1, 10, null, start, end);
        assertThat(page.getRecords()).isNotEmpty();
    }
}
