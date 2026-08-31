package com.systar.ops.statistics.model;

import java.time.LocalDateTime;

/**
 * A single aggregated trend bar with avg/max/min data.
 */
public record TrendBarVO(
        LocalDateTime time,
        double avg,
        double max,
        double min,
        long sampleCount) {
}
