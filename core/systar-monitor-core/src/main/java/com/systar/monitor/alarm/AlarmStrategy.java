package com.systar.monitor.alarm;

/**
 * Alarm strategy enumeration.
 * <p>
 * Determines how repeated alarms for the same monitor are handled:
 * <ul>
 *   <li>{@link #ONLY_ONCE}  -- fire once per alarm cycle; suppress duplicates
 *         until the monitor returns to NORMAL.</li>
 *   <li>{@link #CONTINUOUS} -- fire on every detection cycle while the
 *         condition holds.</li>
 *   <li>{@link #SELECTIVE}  -- fire once per alarm cycle; a new cycle can
 *         only start after the monitor has recovered to NORMAL.</li>
 * </ul>
 */
public enum AlarmStrategy {

    /** Fire once; suppress until the monitor recovers to NORMAL. */
    ONLY_ONCE,

    /** Fire on every detection cycle while the alarm condition holds. */
    CONTINUOUS,

    /**
     * Fire once per alarm cycle.
     * The monitor must first return to NORMAL before a new alarm can be raised.
     */
    SELECTIVE
}
