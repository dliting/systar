package com.systar.monitor.linkage;

/**
 * The type of trigger source for a linkage rule.
 */
public enum CauseType {

    /** Triggered when a monitor produces a matching value. */
    MONITOR,

    /** Triggered when an alarm fires for a monitored asset. */
    ALARM,

    /** Triggered when alarms are correlated into a group for the same device. */
    CORRELATION_GROUP
}
