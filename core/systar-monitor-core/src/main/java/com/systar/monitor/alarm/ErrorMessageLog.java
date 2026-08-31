package com.systar.monitor.alarm;

import com.systar.monitor.asset.AssetState;
import lombok.Data;

/**
 * Represents a single alarm message produced by the alarm engine.
 * <p>
 * Instances are created when an alarm fires and are queued for persistence
 * (handled by the data module in a later phase).
 */
@Data
public class ErrorMessageLog {

    /** Auto-generated primary key (set after persistence). */
    private long id;

    /** The monitor that triggered this alarm. */
    private int monitorId;

    /** Human-readable name of the monitor at alarm time. */
    private String monitorName;

    /** Error description or warning message text. */
    private String error;

    /** The sampled value that triggered the alarm. */
    private Object value;

    /** The asset state at the time of alarm. */
    private AssetState state;

    /** Event rank (severity level) id. */
    private int eventRankId;

    /** Timestamp (epoch millis) when the alarm was generated. */
    private long alarmTime;

    /**
     * Whether the alarm has been marked as recovered.
     * Set to {@code true} when the monitor returns to NORMAL after an alarm.
     */
    private boolean recovered;

    private String correlationGroup;

    private Integer rootCauseId;

    private boolean suppressed;

    private boolean silenced;

    private int escalationLevel;

    private Integer deviceId;}
