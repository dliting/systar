package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;

/**
 * Complete response for a trend chart query.
 */
@Data
public class TrendResponseVO {
    private String granularity;
    private List<TrendBarVO> dataPoints;
    /** Only populated when granularity is INTRADAY. */
    private List<IntradayPointVO> intradayPoints;
    private List<Double> avg5;
    private List<Double> avg10;
    private List<Double> avg20;
    private TrendSummaryVO summary;
}
