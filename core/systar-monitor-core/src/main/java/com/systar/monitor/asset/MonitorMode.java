package com.systar.monitor.asset;

/**
 * Enumeration of monitor operating modes.
 * <p>
 * ACTIVE: the scheduler polls monitors at a fixed interval.
 * PASSIVE: monitors receive data pushed from external sources.
 */
public enum MonitorMode {
    ACTIVE(0),
    PASSIVE(1);

    private final int code;

    MonitorMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MonitorMode fromCode(int code) {
        for (MonitorMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown MonitorMode code: " + code);
    }
}
