package com.systar.data.mapper;

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
class AlarmMessageMapperTest {

    @Autowired
    private AlarmMessageMapper mapper;

    @Test
    void insertAndFindById() {
        AlarmMessageEntity entity = createEntity(100, "TempAlarm", 1);
        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);
        assertThat(entity.getId()).isNotNull().isPositive();

        AlarmMessageEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getMonitorId()).isEqualTo(100);
        assertThat(found.getCaption()).isEqualTo("TempAlarm");
        assertThat(found.getState()).isEqualTo(1);
        assertThat(found.getAlarmTime()).isNotNull();
    }

    @Test
    void update() {
        AlarmMessageEntity entity = createEntity(200, "Original", 2);
        mapper.insert(entity);

        entity.setCaption("Updated");
        entity.setState(1);
        mapper.updateById(entity);

        AlarmMessageEntity updated = mapper.selectById(entity.getId());
        assertThat(updated.getCaption()).isEqualTo("Updated");
        assertThat(updated.getState()).isEqualTo(1);
    }

    @Test
    void delete() {
        AlarmMessageEntity entity = createEntity(300, "ToDelete", 1);
        mapper.insert(entity);

        int rows = mapper.deleteById(entity.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList_returnsInsertedRecords() {
        mapper.insert(createEntity(10, "First", 1));
        mapper.insert(createEntity(20, "Second", 2));

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }

    private static AlarmMessageEntity createEntity(int monitorId, String caption, int state) {
        AlarmMessageEntity entity = new AlarmMessageEntity();
        entity.setMonitorId(monitorId);
        entity.setCaption(caption);
        entity.setState(state);
        entity.setAlarmTime(LocalDateTime.now());
        return entity;
    }
}
