package com.systar.data.service;

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
class SampleFloatServiceImplTest {

    @Autowired
    private SampleFloatService service;

    @Test
    void saveAndRetrieve() {
        SampleFloatEntity entity = createEntity(10, 25.5f);
        assertThat(service.save(entity)).isTrue();

        SampleFloatEntity found = service.getById(entity.getId());
        assertThat(found.getMonitorId()).isEqualTo(10);
        assertThat(found.getValue()).isEqualTo(25.5f);
    }

    @Test
    void remove() {
        SampleFloatEntity entity = createEntity(20, 30.0f);
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }

    @Test
    void list() {
        service.save(createEntity(1, 1.0f));
        service.save(createEntity(2, 2.0f));
        assertThat(service.list()).hasSizeGreaterThanOrEqualTo(2);
    }

    private static SampleFloatEntity createEntity(int monitorId, float value) {
        SampleFloatEntity entity = new SampleFloatEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(LocalDateTime.now());
        return entity;
    }
}
