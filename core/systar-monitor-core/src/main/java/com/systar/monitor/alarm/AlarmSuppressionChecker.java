package com.systar.monitor.alarm;

/**
 * Checks whether an alarm should be suppressed (silenced or deduplicated).
 * Implementations are provided by the data module.
 */
public interface AlarmSuppressionChecker {

    /**
     * Check if the given device/monitor is currently in a silence window.
     *
     * @param deviceId  device ID (may be null)
     * @param monitorId monitor ID
     * @return true if the alarm should be silenced
     */
    boolean isSilenced(Integer deviceId, int monitorId);

    /**
     * Check if a similar alarm was recently fired within the dedup window.
     *
     * @param alarmRuleId         alarm rule ID
     * @param monitorId           monitor ID
     * @param dedupWindowSeconds  dedup time window in seconds (0 = no dedup)
     * @return true if this is a duplicate alarm within the window
     */
    boolean isDuplicate(int alarmRuleId, int monitorId, int dedupWindowSeconds);

    /**
     * Record that an alarm was just fired for dedup tracking.
     *
     * @param alarmRuleId alarm rule ID
     * @param monitorId   monitor ID
     */
    void recordAlarmFired(int alarmRuleId, int monitorId);
}
