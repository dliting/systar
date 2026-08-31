package com.systar.data.repository;

import com.systar.data.entity.ErrorMessageLogEntity;
import com.systar.data.mapper.ErrorMessageLogMapper;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.alarm.AlarmRepository;
import com.systar.monitor.alarm.ErrorMessageLog;
import com.systar.monitor.asset.AssetState;
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
class AlarmRepositoryImplTest {

    private static final int STATE_ERROR = 1;
    private static final int STATE_WARNING = 2;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private ErrorMessageLogMapper errorLogMapper;

    @Test
    void saveAlarm_errorState_mapsCorrectly() {
        ErrorMessageLog message = createMessage(10, "TempHigh", "Over limit", 99.5, AssetState.ERROR);

        alarmRepository.saveAlarm(message);

        assertThat(errorLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getAlarmRuleId()).isEqualTo(0);
                    assertThat(entity.getMonitorId()).isEqualTo(10);
                    assertThat(entity.getMonitorName()).isEqualTo("TempHigh");
                    assertThat(entity.getError()).isEqualTo("Over limit");
                    assertThat(entity.getValue()).isEqualTo("99.5");
                    assertThat(entity.getState()).isEqualTo(STATE_ERROR);
                    assertThat(entity.getLogTime()).isNotNull();
                });
    }

    @Test
    void saveAlarm_warningState_mapsCorrectly() {
        ErrorMessageLog message = createMessage(20, "HumidityLow", "Below threshold", 12.3, AssetState.WARNING);

        alarmRepository.saveAlarm(message);

        assertThat(errorLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getState()).isEqualTo(STATE_WARNING);
                    assertThat(entity.getMonitorId()).isEqualTo(20);
                });
    }

    @Test
    void saveAlarm_nullValue_convertsToNullString() {
        ErrorMessageLog message = createMessage(30, "Pressure", "Sensor fault", null, AssetState.ERROR);

        alarmRepository.saveAlarm(message);

        assertThat(errorLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> assertThat(entity.getValue()).isEqualTo("null"));
    }

    @Test
    void saveAlarm_stringValue_preservedAsString() {
        ErrorMessageLog message = createMessage(40, "Status", "Comm error", "OFFLINE", AssetState.ERROR);

        alarmRepository.saveAlarm(message);

        assertThat(errorLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> assertThat(entity.getValue()).isEqualTo("OFFLINE"));
    }

    private static ErrorMessageLog createMessage(int monitorId, String monitorName,
                                                  String error, Object value, AssetState state) {
        ErrorMessageLog msg = new ErrorMessageLog();
        msg.setMonitorId(monitorId);
        msg.setMonitorName(monitorName);
        msg.setError(error);
        msg.setValue(value);
        msg.setState(state);
        return msg;
    }
}
