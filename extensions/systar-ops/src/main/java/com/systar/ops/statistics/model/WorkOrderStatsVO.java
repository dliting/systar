package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkOrderStatsVO {
    private Map<String, Long> byStatus;
    private double mttrHours;
    private double slaComplianceRate;
    private Map<String, Long> agingDistribution;
    private long currentPeriodTotal;
    private long prevPeriodTotal;
    private List<TrendPoint> trend;
}
