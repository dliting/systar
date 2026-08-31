package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;

@Data
public class DeviceRuntimeVO {
    private int totalDevices;
    private int onlineDevices;
    private double availabilityRate;
    private List<DeviceOnlineDetail> details;

    public record DeviceOnlineDetail(int deviceId, String deviceName,
                                      long onlineDays, long totalDays, double rate) {
    }
}
