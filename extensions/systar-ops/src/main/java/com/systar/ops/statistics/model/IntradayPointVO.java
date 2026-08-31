package com.systar.ops.statistics.model;

import java.time.LocalDateTime;

/**
 * A single raw data point used for intraday (line) granularity.
 */
public record IntradayPointVO(
        LocalDateTime time,
        double value) {
}
