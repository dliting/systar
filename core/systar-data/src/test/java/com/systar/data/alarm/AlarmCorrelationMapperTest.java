package com.systar.data.alarm;

import com.systar.data.entity.AlarmCorrelationRuleEntity;
import com.systar.data.entity.AlarmEscalationPolicyEntity;
import com.systar.data.entity.AlarmSilenceWindowEntity;
import com.systar.data.mapper.AlarmCorrelationRuleMapper;
import com.systar.data.mapper.AlarmEscalationPolicyMapper;
import com.systar.data.mapper.AlarmSilenceWindowMapper;
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
class AlarmCorrelationMapperTest {

    private static final int NONEXISTENT_ID = 99999;

    @Autowired
    private AlarmCorrelationRuleMapper correlationRuleMapper;

    @Autowired
    private AlarmEscalationPolicyMapper escalationPolicyMapper;

    @Autowired
    private AlarmSilenceWindowMapper silenceWindowMapper;

    @Test
    void correlationRule_insertAndSelect() {
        AlarmCorrelationRuleEntity entity = new AlarmCorrelationRuleEntity();
        entity.setName("test-rule");
        entity.setDeviceId(null);
        entity.setWindowSeconds(300);
        entity.setEnabled(1);

        correlationRuleMapper.insert(entity);
        assertThat(entity.getId()).isNotNull();

        AlarmCorrelationRuleEntity loaded = correlationRuleMapper.selectById(entity.getId());
        assertThat(loaded.getName()).isEqualTo("test-rule");
        assertThat(loaded.getWindowSeconds()).isEqualTo(300);
    }

    @Test
    void escalationPolicy_insertAndSelect() {
        AlarmEscalationPolicyEntity entity = new AlarmEscalationPolicyEntity();
        entity.setName("test-policy");
        entity.setFromLevel(1);
        entity.setToLevel(2);
        entity.setTimeoutMinutes(30);
        entity.setNotifyType("site_notice");
        entity.setEnabled(1);

        escalationPolicyMapper.insert(entity);
        assertThat(entity.getId()).isNotNull();

        AlarmEscalationPolicyEntity loaded = escalationPolicyMapper.selectById(entity.getId());
        assertThat(loaded.getName()).isEqualTo("test-policy");
        assertThat(loaded.getTimeoutMinutes()).isEqualTo(30);
    }

    @Test
    void silenceWindow_insertAndSelect() {
        AlarmSilenceWindowEntity entity = new AlarmSilenceWindowEntity();
        entity.setName("test-silence");
        entity.setDeviceId(null);
        entity.setMonitorId(null);
        entity.setStartTime(LocalDateTime.now().minusHours(1));
        entity.setEndTime(LocalDateTime.now().plusHours(1));
        entity.setReason("maintenance");
        entity.setEnabled(1);

        silenceWindowMapper.insert(entity);
        assertThat(entity.getId()).isNotNull();

        AlarmSilenceWindowEntity loaded = silenceWindowMapper.selectById(entity.getId());
        assertThat(loaded.getName()).isEqualTo("test-silence");
        assertThat(loaded.getReason()).isEqualTo("maintenance");
    }

    @Test
    void silenceWindow_isSilencedWhenActive() {
        AlarmSilenceWindowEntity entity = new AlarmSilenceWindowEntity();
        entity.setName("active-silence");
        entity.setDeviceId(NONEXISTENT_ID);
        entity.setMonitorId(null);
        entity.setStartTime(LocalDateTime.now().minusHours(1));
        entity.setEndTime(LocalDateTime.now().plusHours(1));
        entity.setEnabled(1);
        silenceWindowMapper.insert(entity);

        assertThat(silenceWindowMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AlarmSilenceWindowEntity>()
                        .eq("enabled", 1)
                        .eq("device_id", NONEXISTENT_ID)
        )).isEqualTo(1);
    }
}
