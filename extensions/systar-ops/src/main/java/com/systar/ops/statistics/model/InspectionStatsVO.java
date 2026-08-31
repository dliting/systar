package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;

@Data
public class InspectionStatsVO {
    private long totalTasks;
    private long completedTasks;
    private double completionRate;
    private long anomalyCount;
    private double anomalyRate;
    private List<TrendPoint> completionTrend;
    private List<TrendPoint> anomalyTrend;
    private long currentPeriodTotal;
    private long prevPeriodTotal;
}
