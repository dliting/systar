package com.systar.data.alarm;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.systar.data.entity.AlarmEscalationPolicyEntity;
import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.mapper.AlarmEscalationPolicyMapper;
import com.systar.data.mapper.AlarmMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEscalationService {

    private static final int ESCALATION_CHECK_INTERVAL_MS = 60_000;

    private final AlarmMessageMapper alarmMessageMapper;
    private final AlarmEscalationPolicyMapper escalationPolicyMapper;

    @Scheduled(fixedDelay = ESCALATION_CHECK_INTERVAL_MS)
    public void checkEscalation() {
        List<AlarmEscalationPolicyEntity> policies = findEnabledPolicies();
        if (policies.isEmpty()) {
            return;
        }

        for (AlarmEscalationPolicyEntity policy : policies) {
            escalateByPolicy(policy);
        }
    }

    private List<AlarmEscalationPolicyEntity> findEnabledPolicies() {
        QueryWrapper<AlarmEscalationPolicyEntity> qw = new QueryWrapper<>();
        qw.eq("enabled", 1);
        return escalationPolicyMapper.selectList(qw);
    }

    private void escalateByPolicy(AlarmEscalationPolicyEntity policy) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(policy.getTimeoutMinutes());

        QueryWrapper<AlarmMessageEntity> qw = new QueryWrapper<>();
        qw.eq("state", AlarmMessageEntity.STATE_PENDING)
                .eq("warn_id", policy.getFromLevel())
                .eq("escalation_level", 0)
                .le("alarm_time", cutoff);

        List<AlarmMessageEntity> candidates = alarmMessageMapper.selectList(qw);
        if (candidates.isEmpty()) {
            return;
        }

        for (AlarmMessageEntity alarm : candidates) {
            UpdateWrapper<AlarmMessageEntity> update = new UpdateWrapper<>();
            update.eq("id", alarm.getId())
                    .set("warn_id", policy.getToLevel())
                    .set("escalation_level", alarm.getEscalationLevel() + 1);
            alarmMessageMapper.update(null, update);
            log.info("Alarm escalated: id={}, fromLevel={}, toLevel={}, policy={}",
                    alarm.getId(), policy.getFromLevel(), policy.getToLevel(), policy.getName());
        }
    }
}
