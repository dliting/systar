package com.systar.monitor.alarm;

/**
 * Correlates alarms into groups based on device and time window.
 * Implementations are provided by the data module.
 */
public interface AlarmCorrelator {

    /**
     * Generate a correlation group ID for the given device.
     * The group ID follows the pattern: CORR-{deviceId}-{yyyyMMddHH}
     *
     * @param deviceId device ID
     * @return correlation group ID, or null if no correlation rule matches
     */
    String correlate(Integer deviceId);
}
