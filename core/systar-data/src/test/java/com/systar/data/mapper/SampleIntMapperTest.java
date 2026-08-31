package com.systar.data.mapper;

import com.systar.data.entity.SampleIntEntity;
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
class SampleIntMapperTest {

    @Autowired
    private SampleIntMapper mapper;

    @Test
    void insertAndFindById() {
        SampleIntEntity entity = new SampleIntEntity();
        entity.setMonitorId(10);
        entity.setValue(42);
        entity.setSampleTime(LocalDateTime.now());

        assertThat(mapper.insert(entity)).isEqualTo(1);
        assertThat(entity.getId()).isNotNull().isPositive();

        SampleIntEntity found = mapper.selectById(entity.getId());
        assertThat(found.getMonitorId()).isEqualTo(10);
        assertThat(found.getValue()).isEqualTo(42);
    }

    @Test
    void selectList_returnsInsertedRecords() {
        mapper.insert(createEntity(1, 100));
        mapper.insert(createEntity(2, 200));
        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }

    private static SampleIntEntity createEntity(int monitorId, Integer value) {
        SampleIntEntity entity = new SampleIntEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(LocalDateTime.now());
        return entity;
    }
}
