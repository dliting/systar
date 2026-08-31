package com.systar.data.service;

import com.systar.data.entity.AlarmMessageEntity;
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
class AlarmMessageServiceImplTest {

    @Autowired
    private AlarmMessageService service;

    @Test
    void saveAndRetrieve() {
        AlarmMessageEntity entity = new AlarmMessageEntity();
        entity.setMonitorId(10);
        entity.setCaption("Test alarm");
        entity.setState(2);
        entity.setAlarmTime(LocalDateTime.now());

        assertThat(service.save(entity)).isTrue();
        assertThat(entity.getId()).isNotNull();

        AlarmMessageEntity found = service.getById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCaption()).isEqualTo("Test alarm");
    }

    @Test
    void update() {
        AlarmMessageEntity entity = new AlarmMessageEntity();
        entity.setMonitorId(20);
        entity.setCaption("Original");
        entity.setState(1);
        entity.setAlarmTime(LocalDateTime.now());
        service.save(entity);

        entity.setCaption("Updated");
        assertThat(service.updateById(entity)).isTrue();

        assertThat(service.getById(entity.getId()).getCaption()).isEqualTo("Updated");
    }

    @Test
    void remove() {
        AlarmMessageEntity entity = new AlarmMessageEntity();
        entity.setMonitorId(30);
        entity.setCaption("ToDelete");
        entity.setState(1);
        entity.setAlarmTime(LocalDateTime.now());
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }

    @Test
    void list() {
        AlarmMessageEntity e1 = new AlarmMessageEntity();
        e1.setMonitorId(1);
        e1.setCaption("A");
        e1.setState(1);
        e1.setAlarmTime(LocalDateTime.now());
        service.save(e1);

        AlarmMessageEntity e2 = new AlarmMessageEntity();
        e2.setMonitorId(2);
        e2.setCaption("B");
        e2.setState(2);
        e2.setAlarmTime(LocalDateTime.now());
        service.save(e2);

        assertThat(service.list()).hasSizeGreaterThanOrEqualTo(2);
    }
}
