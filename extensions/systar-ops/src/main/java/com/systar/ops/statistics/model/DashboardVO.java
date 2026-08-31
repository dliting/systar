package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;

@Data
public class DashboardVO {
    private AlarmSummary alarms;
    private WorkOrderSummary workOrders;
    private InspectionSummary inspections;
    private DeviceSummary devices;
    private List<AlarmStatsVO.DeviceAlarmCount> topAlarmDevices;

    public record AlarmSummary(int today, List<TrendPoint> trend, double handlingRate) {
    }

    public record WorkOrderSummary(long open, double mttrHours, double slaCompliance) {
    }

    public record InspectionSummary(int todayTotal, int completed, double completionRate) {
    }

    public record DeviceSummary(int total, int online, double availability) {
    }
}
