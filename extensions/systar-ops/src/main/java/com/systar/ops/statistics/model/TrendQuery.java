package com.systar.ops.statistics.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Validated query parameters for trend chart endpoints.
 */
public record TrendQuery(
        int monitorId,
        String monitorKind,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String granularity) {

    private static final Set<String> VALID_GRANULARITIES =
            Set.of("INTRADAY", "HOUR", "DAY", "WEEK", "MONTH");

    public TrendQuery {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (monitorKind != null && !monitorKind.equals("PROBE") && !monitorKind.equals("CONTROL")) {
            throw new IllegalArgumentException(
                    "Invalid monitorKind: " + monitorKind + ". Must be PROBE or CONTROL");
        }
        if (granularity == null) {
            granularity = "HOUR";
        } else {
            granularity = granularity.toUpperCase();
            if (!VALID_GRANULARITIES.contains(granularity)) {
                throw new IllegalArgumentException(
                        "Invalid granularity: " + granularity
                                + ". Must be INTRADAY, HOUR, DAY, WEEK, or MONTH");
            }
        }
    }
}
