package com.systar.ops.statistics.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Validated query parameters for statistics endpoints.
 */
public record StatisticsQuery(
        LocalDate startDate,
        LocalDate endDate,
        Integer deviceId,
        Integer spaceId,
        String granularity) {

    private static final long MAX_RANGE_DAYS = 365;
    private static final Set<String> VALID_GRANULARITIES =
            Set.of("HOUR", "DAY", "WEEK", "MONTH");

    public StatisticsQuery {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        if (granularity != null && !VALID_GRANULARITIES.contains(granularity.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Invalid granularity: " + granularity + ". Must be HOUR, DAY, WEEK, or MONTH");
        }
        if (granularity == null) {
            granularity = "DAY";
        } else {
            granularity = granularity.toUpperCase();
        }
    }

    public LocalDate getPrevPeriodStart() {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        return startDate.minusDays(days);
    }
}
