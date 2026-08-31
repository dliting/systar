package com.systar.data.service;

import com.systar.data.entity.AlarmRuleEntity;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.alarm.AlarmStrategy;
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
class AlarmRuleServiceImplTest {

    @Autowired
    private AlarmRuleService service;

    @Test
    void saveAndRetrieve() {
        AlarmRuleEntity entity = createEntity(50, AlarmStrategy.ONLY_ONCE);
        assertThat(service.save(entity)).isTrue();

        AlarmRuleEntity found = service.getById(entity.getId());
        assertThat(found.getMonitorId()).isEqualTo(50);
        assertThat(found.getStrategy()).isEqualTo(AlarmStrategy.ONLY_ONCE);
    }

    @Test
    void update() {
        AlarmRuleEntity entity = createEntity(60, AlarmStrategy.CONTINUOUS);
        service.save(entity);

        entity.setEnabled(0);
        assertThat(service.updateById(entity)).isTrue();

        assertThat(service.getById(entity.getId()).getEnabled()).isEqualTo(0);
    }

    @Test
    void remove() {
        AlarmRuleEntity entity = createEntity(70, AlarmStrategy.ONLY_ONCE);
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }

    private static AlarmRuleEntity createEntity(int monitorId, AlarmStrategy strategy) {
        AlarmRuleEntity entity = new AlarmRuleEntity();
        entity.setMonitorId(monitorId);
        entity.setStrategy(strategy);
        entity.setWay(0);
        entity.setEventRankId(1);
        entity.setMessageTemplate("value > 10");
        entity.setEnabled(1);
        entity.setStart(0);
        return entity;
    }
}
