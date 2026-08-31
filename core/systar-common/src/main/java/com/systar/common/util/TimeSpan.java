package com.systar.common.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Time span utility backed by {@link java.time.Duration}.
 *
 * <p>Supports parsing strings like "10s", "5m", "2h", "1d".
 *
 * <p>Thread-safe (immutable).
 */
public final class TimeSpan implements Comparable<TimeSpan> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    private static final Map<Character, ChronoUnit> UNIT_MAP = Map.of(
            's', ChronoUnit.SECONDS,
            'm', ChronoUnit.MINUTES,
            'h', ChronoUnit.HOURS,
            'd', ChronoUnit.DAYS
    );

    private final Duration duration;

    private TimeSpan(Duration duration) {
        this.duration = duration;
    }

    // ========== Factory methods ==========

    public static TimeSpan ofSeconds(long seconds) {
        return new TimeSpan(Duration.ofSeconds(seconds));
    }

    public static TimeSpan ofMinutes(long minutes) {
        return new TimeSpan(Duration.ofMinutes(minutes));
    }

    public static TimeSpan ofHours(long hours) {
        return new TimeSpan(Duration.ofHours(hours));
    }

    public static TimeSpan ofDays(long days) {
        return new TimeSpan(Duration.ofDays(days));
    }

    /**
     * Parse a time span string.
     *
     * <p>Supported formats: "10s", "5m", "2h", "1d", "0".
     *
     * @param text time span string
     * @return TimeSpan instance
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    public static TimeSpan parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Time span string must not be null or blank");
        }

        String trimmed = text.trim();

        // Special case: plain "0" means zero duration
        if ("0".equals(trimmed)) {
            return new TimeSpan(Duration.ZERO);
        }

        var matcher = PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid time span format: '" + text + "'. Expected patterns like 10s, 5m, 2h, 1d");
        }

        long amount = Long.parseLong(matcher.group(1));
        char unitChar = matcher.group(2).charAt(0);
        ChronoUnit unit = UNIT_MAP.get(unitChar);

        return new TimeSpan(Duration.of(amount, unit));
    }

    // ========== Conversion methods ==========

    public Duration toDuration() {
        return duration;
    }

    public long toMillis() {
        return duration.toMillis();
    }

    public long toSeconds() {
        return duration.toSeconds();
    }

    public long toMinutes() {
        return duration.toMinutes();
    }

    public long toHours() {
        return duration.toHours();
    }

    public long toDays() {
        return duration.toDays();
    }

    // ========== Object / Comparable ==========

    @Override
    public String toString() {
        if (duration.isZero()) {
            return "0";
        }

        long seconds = duration.toSeconds();
        if (seconds % 86400 == 0) {
            return (seconds / 86400) + "d";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TimeSpan other)) {
            return false;
        }
        return this.duration.equals(other.duration);
    }

    @Override
    public int hashCode() {
        return duration.hashCode();
    }

    @Override
    public int compareTo(TimeSpan other) {
        return this.duration.compareTo(other.duration);
    }
}
