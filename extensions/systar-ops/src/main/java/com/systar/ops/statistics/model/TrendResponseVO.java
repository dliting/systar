package com.systar.ops.statistics.model;

import lombok.Data;

import java.util.List;

/**
 * Complete response for a trend chart query (B3). One unified {@code points}
 * list serves every mode; {@code mode} tells the frontend how to render it:
 * RAW (raw samples, INTRADAY small windows), BUCKETED (downsampled INTRADAY,
 * Task 5) or OHLC (candlestick from stats/aggregate buckets). Moving averages
 * are computed client-side, so no avg5/10/20 series ship here.
 */
@Data
public class TrendResponseVO {
    /** Effective granularity after server-side AUTO resolution/clamping (D6). */
    private String granularity;
    /** One of {@code RAW}, {@code BUCKETED}, {@code OHLC} (TrendService.MODE_*). */
    private String mode;
    private List<TrendPointVO> points;
    /** Bucket width in seconds for BUCKETED/OHLC; null for RAW. */
    private Long bucketSeconds;
    private TrendSummaryVO summary;
}
