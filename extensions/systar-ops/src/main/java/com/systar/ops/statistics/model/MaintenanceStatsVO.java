package com.systar.ops.statistics.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class MaintenanceStatsVO {
    private long totalRecords;
    private BigDecimal totalCost;
    private Map<String, Long> byType;
    private Map<String, BigDecimal> costByType;
    private List<TrendPoint> frequencyTrend;
    private long currentPeriodTotal;
    private long prevPeriodTotal;
}
