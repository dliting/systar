package com.systar.data.mapper;

import com.systar.data.entity.LinkageLogEntity;
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
class LinkageLogMapperTest {

    @Autowired
    private LinkageLogMapper mapper;

    @Test
    void insertAndFindById() {
        LinkageLogEntity entity = new LinkageLogEntity();
        entity.setRuleId(1);
        entity.setCauseMonitorId(10);
        entity.setEffectMonitorId(20);
        entity.setTriggerTime(LocalDateTime.now());
        entity.setEffectCommand("ON");
        entity.setSuccess(true);

        assertThat(mapper.insert(entity)).isEqualTo(1);

        LinkageLogEntity found = mapper.selectById(entity.getId());
        assertThat(found.getRuleId()).isEqualTo(1);
        assertThat(found.getCauseMonitorId()).isEqualTo(10);
        assertThat(found.getEffectCommand()).isEqualTo("ON");
        assertThat(found.getSuccess()).isTrue();
    }

    @Test
    void insertFailedLog() {
        LinkageLogEntity entity = new LinkageLogEntity();
        entity.setRuleId(2);
        entity.setCauseMonitorId(11);
        entity.setEffectMonitorId(21);
        entity.setTriggerTime(LocalDateTime.now());
        entity.setEffectCommand("OFF");
        entity.setSuccess(false);
        mapper.insert(entity);

        LinkageLogEntity found = mapper.selectById(entity.getId());
        assertThat(found.getSuccess()).isFalse();
    }
}
