package com.systar.data.mapper;

import com.systar.data.entity.SampleFloatEntity;
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
class SampleFloatMapperTest {

    @Autowired
    private SampleFloatMapper mapper;

    @Test
    void insertAndFindById() {
        SampleFloatEntity entity = createEntity(10, 25.5f);
        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);
        assertThat(entity.getId()).isNotNull().isPositive();

        SampleFloatEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getMonitorId()).isEqualTo(10);
        assertThat(found.getValue()).isEqualTo(25.5f);
        assertThat(found.getSampleTime()).isNotNull();
    }

    @Test
    void insertNullValue() {
        SampleFloatEntity entity = createEntity(20, null);
        mapper.insert(entity);

        SampleFloatEntity found = mapper.selectById(entity.getId());
        assertThat(found.getValue()).isNull();
    }

    @Test
    void selectList_returnsInsertedRecords() {
        mapper.insert(createEntity(1, 10.0f));
        mapper.insert(createEntity(2, 20.0f));

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void delete() {
        SampleFloatEntity entity = createEntity(30, 99.9f);
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    private static SampleFloatEntity createEntity(int monitorId, Float value) {
        SampleFloatEntity entity = new SampleFloatEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(LocalDateTime.now());
        return entity;
    }
}
