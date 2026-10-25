package com.systar.data.repository;

import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.entity.ErrorMessageLogEntity;
import com.systar.data.event.AlarmPersistedEvent;
import com.systar.data.mapper.AlarmMessageMapper;
import com.systar.data.mapper.ErrorMessageLogMapper;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.alarm.AlarmRepository;
import com.systar.monitor.alarm.ErrorMessageLog;
import com.systar.monitor.asset.AssetState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {DataTestApplication.class, AlarmRepositoryImplTest.EventCapture.class})
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

    @Autowired
    private AlarmMessageMapper alarmMessageMapper;

    @Autowired
    private EventCapture eventCapture;

    /** Collects the events the repository publishes so tests can assert their payload. */
    @TestConfiguration
    static class EventCapture {
        final List<AlarmPersistedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void capture(AlarmPersistedEvent event) {
            events.add(event);
        }
    }

    // The capture bean is a context singleton — events published by earlier test
    // methods would otherwise leak into later assertions.
    @BeforeEach
    void clearCapturedEvents() {
        eventCapture.events.clear();
    }

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

    // The alarm center and the notification bell show t_alarm_message.caption as
    // the alarm title; leaving it NULL renders an empty title for every alarm.
    // The rendered message template (ErrorMessageLog.error, already caption-
    // substituted by the alarm handler) is the operator-facing title.
    @Test
    void saveAlarm_persistedAlarmMessageCarriesCaptionFromRenderedError() {
        ErrorMessageLog message = createMessage(50, "humi_102", "102湿度 湿度过高: 85.3", 85.3, AssetState.ERROR);

        alarmRepository.saveAlarm(message);

        assertThat(alarmMessageMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> assertThat(entity.getCaption())
                        .isEqualTo("102湿度 湿度过高: 85.3"));
    }

    // The WebSocket push is the primary alarm channel; without the caption in
    // the event the bell can only show "资产 #<id>" (an unresolvable raw id).
    @Test
    void saveAlarm_publishedEventCarriesCaption() {
        ErrorMessageLog message = createMessage(60, "temp_101", "101温度 温度过高: 40.2", 40.2, AssetState.ERROR);

        alarmRepository.saveAlarm(message);

        assertThat(eventCapture.events)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getCaption()).isEqualTo("101温度 温度过高: 40.2");
                    assertThat(event.getAssetId()).isEqualTo(60);
                });
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
