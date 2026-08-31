package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AlarmStatsVO {
    private long totalAlarms;
    private long pendingAlarms;
    private double handlingRate;
    private Map<Integer, Long> byLevel;
    private List<TrendPoint> trend;
    private long currentPeriodCount;
    private long prevPeriodCount;
    private List<DeviceAlarmCount> topDevices;

    public record DeviceAlarmCount(int deviceId, String deviceName, long alarmCount) {
    }
}
