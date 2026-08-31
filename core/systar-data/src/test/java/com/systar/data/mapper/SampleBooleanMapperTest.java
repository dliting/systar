package com.systar.data.mapper;

import com.systar.data.entity.SampleBooleanEntity;
import com.systar.data.test.DataTestApplication;
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

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SampleBooleanMapperTest {

    @Autowired
    private SampleBooleanMapper mapper;

    @Test
    void insertAndFindById() {
        SampleBooleanEntity entity = new SampleBooleanEntity();
        entity.setMonitorId(10);
        entity.setValue(true);
        entity.setSampleTime(LocalDateTime.now());

        assertThat(mapper.insert(entity)).isEqualTo(1);

        SampleBooleanEntity found = mapper.selectById(entity.getId());
        assertThat(found.getMonitorId()).isEqualTo(10);
        assertThat(found.getValue()).isTrue();
    }

    @Test
    void insertFalseValue() {
        SampleBooleanEntity entity = new SampleBooleanEntity();
        entity.setMonitorId(20);
        entity.setValue(false);
        entity.setSampleTime(LocalDateTime.now());
        mapper.insert(entity);

        SampleBooleanEntity found = mapper.selectById(entity.getId());
        assertThat(found.getValue()).isFalse();
    }

    private static SampleBooleanEntity createEntity(int monitorId, Boolean value) {
        SampleBooleanEntity entity = new SampleBooleanEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(LocalDateTime.now());
        return entity;
    }
}
