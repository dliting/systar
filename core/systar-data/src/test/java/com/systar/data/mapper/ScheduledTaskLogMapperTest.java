package com.systar.data.mapper;

import com.systar.data.entity.ScheduledTaskLogEntity;
import com.systar.data.test.DataTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ScheduledTaskLogMapperTest {

    @Autowired
    private ScheduledTaskLogMapper mapper;

    @Test
    void insertAndFindById() {
        ScheduledTaskLogEntity entity = new ScheduledTaskLogEntity();
        entity.setTaskId(1);
        entity.setTaskName("Test task");
        entity.setControlId(100);
        entity.setCommand("ON");
        entity.setExecuteTime(System.currentTimeMillis());
        entity.setSuccess(true);

        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);
        assertThat(entity.getId()).isPositive();

        ScheduledTaskLogEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTaskId()).isEqualTo(1);
        assertThat(found.getTaskName()).isEqualTo("Test task");
        assertThat(found.getControlId()).isEqualTo(100);
        assertThat(found.getCommand()).isEqualTo("ON");
        assertThat(found.getExecuteTime()).isEqualTo(entity.getExecuteTime());
        assertThat(found.getSuccess()).isTrue();
        assertThat(found.getErrorMessage()).isNull();
    }

    @Test
    void insertWithErrorMessage() {
        ScheduledTaskLogEntity entity = new ScheduledTaskLogEntity();
        entity.setTaskId(2);
        entity.setTaskName("Fail task");
        entity.setControlId(200);
        entity.setCommand("OFF");
        entity.setExecuteTime(System.currentTimeMillis());
        entity.setSuccess(false);
        entity.setErrorMessage("Connection refused");

        mapper.insert(entity);

        ScheduledTaskLogEntity found = mapper.selectById(entity.getId());
        assertThat(found.getSuccess()).isFalse();
        assertThat(found.getErrorMessage()).isEqualTo("Connection refused");
    }

    @Test
    void delete() {
        ScheduledTaskLogEntity entity = new ScheduledTaskLogEntity();
        entity.setTaskId(3);
        entity.setTaskName("Tmp");
        entity.setControlId(300);
        entity.setCommand("PING");
        entity.setExecuteTime(0L);
        entity.setSuccess(true);
        mapper.insert(entity);
        long id = entity.getId();

        mapper.deleteById(id);
        assertThat(mapper.selectById(id)).isNull();
    }
}
