package com.systar.ops.statistics.model;

import java.time.LocalDateTime;

/**
 * Unified trend point (B3). avg/max/min always present when the bucket has
 * samples; open/close are null for legacy stats rows aggregated before the
 * open/close columns existed (D3: no backfill) — the frontend degrades the
 * candle to a flat cross at avg. count is the sample count behind the point.
 */
public record TrendPointVO(
        LocalDateTime t,
        Double avg,
        Double max,
        Double min,
        Double open,
        Double close,
        long count) {
}
