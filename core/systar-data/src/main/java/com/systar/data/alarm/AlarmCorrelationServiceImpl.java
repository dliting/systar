package com.systar.data.alarm;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.systar.data.entity.AlarmCorrelationRuleEntity;
import com.systar.data.entity.AlarmSilenceWindowEntity;
import com.systar.data.mapper.AlarmCorrelationRuleMapper;
import com.systar.data.mapper.AlarmSilenceWindowMapper;
import com.systar.monitor.alarm.AlarmCorrelator;
import com.systar.monitor.alarm.AlarmSuppressionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmCorrelationServiceImpl implements AlarmCorrelator, AlarmSuppressionChecker {

    private static final String CORRELATION_GROUP_PREFIX = "CORR-";
    private static final DateTimeFormatter GROUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final AlarmSilenceWindowMapper silenceWindowMapper;
    private final AlarmCorrelationRuleMapper correlationRuleMapper;

    /** Tracks last alarm fire time per (ruleId, monitorId) for dedup. */
    private final Map<String, LocalDateTime> lastAlarmFireTime = new ConcurrentHashMap<>();

    // --- AlarmCorrelator ---

    @Override
    public String correlate(Integer deviceId) {
        if (deviceId == null) {
            return null;
        }

        List<AlarmCorrelationRuleEntity> rules = findMatchingRules(deviceId);
        if (rules.isEmpty()) {
            return null;
        }

        return CORRELATION_GROUP_PREFIX + deviceId + "-" + LocalDateTime.now().format(GROUP_TIME_FORMAT);
    }

    private List<AlarmCorrelationRuleEntity> findMatchingRules(Integer deviceId) {
        QueryWrapper<AlarmCorrelationRuleEntity> qw = new QueryWrapper<>();
        qw.eq("enabled", 1)
                .and(w -> w.isNull("device_id").or().eq("device_id", deviceId));
        return correlationRuleMapper.selectList(qw);
    }

    // --- AlarmSuppressionChecker ---

    @Override
    public boolean isSilenced(Integer deviceId, int monitorId) {
        LocalDateTime now = LocalDateTime.now();

        QueryWrapper<AlarmSilenceWindowEntity> qw = new QueryWrapper<>();
        qw.eq("enabled", 1)
                .le("start_time", now)
                .ge("end_time", now)
                .and(w -> {
                    w.isNull("device_id").or().eq("device_id", deviceId);
                })
                .and(w -> {
                    w.isNull("monitor_id").or().eq("monitor_id", monitorId);
                });

        return silenceWindowMapper.selectCount(qw) > 0;
    }

    @Override
    public boolean isDuplicate(int alarmRuleId, int monitorId, int dedupWindowSeconds) {
        if (dedupWindowSeconds <= 0) {
            return false;
        }

        String key = dedupKey(alarmRuleId, monitorId);
        LocalDateTime lastTime = lastAlarmFireTime.get(key);
        if (lastTime == null) {
            return false;
        }

        return lastTime.plusSeconds(dedupWindowSeconds).isAfter(LocalDateTime.now());
    }

    @Override
    public void recordAlarmFired(int alarmRuleId, int monitorId) {
        lastAlarmFireTime.put(dedupKey(alarmRuleId, monitorId), LocalDateTime.now());
    }

    private String dedupKey(int alarmRuleId, int monitorId) {
        return alarmRuleId + ":" + monitorId;
    }
}
