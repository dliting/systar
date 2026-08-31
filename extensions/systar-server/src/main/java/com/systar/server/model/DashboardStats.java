package com.systar.server.model;

import lombok.Data;

import java.util.Map;

/**
 * Aggregated statistics for the monitoring dashboard.
 */
@Data
public class DashboardStats {

    private int totalDevices;
    private int onlineDevices;
    private int totalProbes;
    private int totalAlarms;
    private int pendingAlarms;
    private Map<String, Integer> assetsByState;
}
