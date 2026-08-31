package com.systar.ops.statistics.model;

/**
 * Summary statistics for a trend query result.
 */
public record TrendSummaryVO(
        double currentValue,
        double periodMax,
        double periodMin,
        long totalSamples) {
}
