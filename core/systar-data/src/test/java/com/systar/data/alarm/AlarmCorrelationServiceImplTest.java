package com.systar.data.alarm;

import com.systar.data.entity.AlarmCorrelationRuleEntity;
import com.systar.data.mapper.AlarmCorrelationRuleMapper;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.alarm.AlarmCorrelator;
import com.systar.monitor.alarm.AlarmSuppressionChecker;
import org.junit.jupiter.api.BeforeEach;
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
class AlarmCorrelationServiceImplTest {

    @Autowired
    private AlarmSuppressionChecker suppressionChecker;

    @Autowired
    private AlarmCorrelator correlator;

    @Autowired
    private AlarmCorrelationRuleMapper correlationRuleMapper;

    @BeforeEach
    void insertDefaultCorrelationRule() {
        AlarmCorrelationRuleEntity rule = new AlarmCorrelationRuleEntity();
        rule.setName("default-rule");
        rule.setDeviceId(null);
        rule.setWindowSeconds(300);
        rule.setEnabled(1);
        correlationRuleMapper.insert(rule);
    }

    @Test
    void correlate_shouldReturnNull_whenDeviceIdIsNull() {
        assertThat(correlator.correlate(null)).isNull();
    }

    @Test
    void correlate_shouldReturnGroupId_whenDeviceIdProvided() {
        String groupId = correlator.correlate(10);
        assertThat(groupId).isNotNull();
        assertThat(groupId).startsWith("CORR-10-");
    }

    @Test
    void correlate_shouldReturnDifferentGroupsForDifferentDevices() {
        String group1 = correlator.correlate(10);
        String group2 = correlator.correlate(20);
        assertThat(group1).isNotEqualTo(group2);
    }

    @Test
    void isSilenced_shouldReturnFalse_whenNoSilenceWindow() {
        assertThat(suppressionChecker.isSilenced(999, 999)).isFalse();
    }

    @Test
    void isDuplicate_shouldReturnFalse_whenDedupWindowIsZero() {
        assertThat(suppressionChecker.isDuplicate(1, 1, 0)).isFalse();
    }

    @Test
    void isDuplicate_shouldReturnFalse_whenNoPreviousAlarm() {
        assertThat(suppressionChecker.isDuplicate(999, 999, 300)).isFalse();
    }

    @Test
    void isDuplicate_shouldReturnTrue_afterRecordingAlarmWithinWindow() {
        suppressionChecker.recordAlarmFired(1, 100);
        assertThat(suppressionChecker.isDuplicate(1, 100, 300)).isTrue();
    }

    @Test
    void isDuplicate_shouldReturnFalse_forDifferentMonitor() {
        suppressionChecker.recordAlarmFired(1, 100);
        assertThat(suppressionChecker.isDuplicate(1, 200, 300)).isFalse();
    }

    @Test
    void isDuplicate_shouldReturnFalse_forDifferentRule() {
        suppressionChecker.recordAlarmFired(1, 100);
        assertThat(suppressionChecker.isDuplicate(2, 100, 300)).isFalse();
    }
}
