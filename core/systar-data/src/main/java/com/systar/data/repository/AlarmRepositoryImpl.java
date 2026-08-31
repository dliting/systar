package com.systar.data.repository;

import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.entity.ErrorMessageLogEntity;
import com.systar.data.event.AlarmPersistedEvent;
import com.systar.data.mapper.AlarmMessageMapper;
import com.systar.data.mapper.ErrorMessageLogMapper;
import com.systar.monitor.alarm.AlarmRepository;
import com.systar.monitor.alarm.ErrorMessageLog;
import com.systar.monitor.asset.AssetState;
import com.systar.monitor.linkage.CorrelationGroupEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Repository implementation that persists alarm messages
 * as error message log records in the database,
 * creates alarm message records, and publishes events.
 */
@Component
public class AlarmRepositoryImpl implements AlarmRepository {

    private static final int DEFAULT_ALARM_RULE_ID = 0;
    private static final int STATE_ERROR = 1;
    private static final int STATE_WARNING = 2;

    private final ErrorMessageLogMapper errorLogMapper;
    private final AlarmMessageMapper alarmMessageMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AlarmRepositoryImpl(ErrorMessageLogMapper errorLogMapper,
                               AlarmMessageMapper alarmMessageMapper,
                               ApplicationEventPublisher eventPublisher) {
        this.errorLogMapper = errorLogMapper;
        this.alarmMessageMapper = alarmMessageMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void saveAlarm(ErrorMessageLog message) {
        ErrorMessageLogEntity entity = new ErrorMessageLogEntity();
        entity.setAlarmRuleId(DEFAULT_ALARM_RULE_ID);
        entity.setMonitorId(message.getMonitorId());
        entity.setMonitorName(message.getMonitorName());
        entity.setError(message.getError());
        entity.setValue(String.valueOf(message.getValue()));
        entity.setState(toDbState(message.getState()));
        entity.setEventRankId(message.getEventRankId());
        entity.setLogTime(LocalDateTime.now());
        errorLogMapper.insert(entity);

        AlarmMessageEntity alarmMsg = new AlarmMessageEntity();
        alarmMsg.setMonitorId(entity.getId());
        alarmMsg.setWarnId(message.getEventRankId());
        alarmMsg.setState(AlarmMessageEntity.STATE_PENDING);
        alarmMsg.setAlarmTime(entity.getLogTime());
        alarmMsg.setCorrelationGroup(message.getCorrelationGroup());
        alarmMsg.setRootCauseId(message.getRootCauseId());
        alarmMsg.setSuppressed(message.isSuppressed()
                ? AlarmMessageEntity.SUPPRESSED_YES : AlarmMessageEntity.SUPPRESSED_NO);
        alarmMsg.setSilenced(message.isSilenced()
                ? AlarmMessageEntity.SILENCED_YES : AlarmMessageEntity.SILENCED_NO);
        alarmMsg.setEscalationLevel(message.getEscalationLevel());
        alarmMsg.setDeviceId(message.getDeviceId());
        alarmMessageMapper.insert(alarmMsg);

        message.setId(entity.getId());
        eventPublisher.publishEvent(
            new AlarmPersistedEvent(this, alarmMsg.getId(), message.getEventRankId(), message.getMonitorId()));

        if (message.getCorrelationGroup() != null) {
            eventPublisher.publishEvent(new CorrelationGroupEvent(
                    this, message.getCorrelationGroup(), message.getDeviceId(), message.getMonitorId()));
        }
    }

    private static int toDbState(AssetState state) {
        return state == AssetState.ERROR ? STATE_ERROR : STATE_WARNING;
    }
}
