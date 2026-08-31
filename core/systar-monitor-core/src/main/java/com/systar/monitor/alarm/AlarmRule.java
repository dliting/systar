package com.systar.monitor.alarm;

import lombok.Data;

/**
 * Configuration for an alarm rule bound to a specific monitor.
 * <p>
 * Each rule associates a monitor with an alarm strategy, an event-rank
 * (severity tier), a message template, and an enabled flag.
 */
@Data
public class AlarmRule {

    /** Primary key. */
    private int id;

    /** The monitor this rule applies to. */
    private int monitorId;

    /** Alarm strategy governing repeat behaviour. */
    private AlarmStrategy strategy;

    /** Event rank / severity tier (foreign key to an external rank table). */
    private int eventRankId;

    /** Message template; may contain placeholders like {@code ${value}}. */
    private String messageTemplate;

    /** Whether this rule is active. */
    private boolean enabled = true;

    /** Dedup time window in seconds. 0 means no dedup limit. */
    private int dedupWindowSeconds;
}
