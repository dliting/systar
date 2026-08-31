package com.systar.data.mapper;

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
class AlarmRuleMapperTest {

    @Autowired
    private AlarmRuleMapper mapper;

    @Test
    void insertAndFindById() {
        AlarmRuleEntity entity = createEntity(50, AlarmStrategy.ONLY_ONCE);
        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);
        assertThat(entity.getId()).isNotNull().isPositive();

        AlarmRuleEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getMonitorId()).isEqualTo(50);
        assertThat(found.getStrategy()).isEqualTo(AlarmStrategy.ONLY_ONCE);
        assertThat(found.getEnabled()).isEqualTo(1);
    }

    @Test
    void update() {
        AlarmRuleEntity entity = createEntity(60, AlarmStrategy.CONTINUOUS);
        mapper.insert(entity);

        entity.setStrategy(AlarmStrategy.ONLY_ONCE);
        entity.setEnabled(0);
        mapper.updateById(entity);

        AlarmRuleEntity updated = mapper.selectById(entity.getId());
        assertThat(updated.getStrategy()).isEqualTo(AlarmStrategy.ONLY_ONCE);
        assertThat(updated.getEnabled()).isEqualTo(0);
    }

    @Test
    void delete() {
        AlarmRuleEntity entity = createEntity(70, AlarmStrategy.ONLY_ONCE);
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    private static AlarmRuleEntity createEntity(int monitorId, AlarmStrategy strategy) {
        AlarmRuleEntity entity = new AlarmRuleEntity();
        entity.setMonitorId(monitorId);
        entity.setStrategy(strategy);
        entity.setWay(0);
        entity.setEventRankId(1);
        entity.setMessageTemplate("value > threshold");
        entity.setEnabled(1);
        entity.setStart(0);
        return entity;
    }
}
