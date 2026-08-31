package com.systar.monitor.control;

import lombok.Data;

/**
 * Configuration for a scheduled control task.
 * <p>
 * Defines a recurring command that will be sent to a {@link com.systar.monitor.asset.Control}
 * asset at times determined by a cron expression.
 */
@Data
public class ScheduledTask {

    /** Unique task identifier. */
    private int id;

    /** Human-readable task name. */
    private String name;

    /**
     * Id of the {@link com.systar.monitor.asset.Control} asset to execute.
     * The asset will be resolved at execution time via
     * {@link com.systar.monitor.asset.AssetStore#findAsset(int)}.
     */
    private int controlId;

    /**
     * The command string to pass to
     * {@link com.systar.monitor.asset.Control#execute(String)}.
     */
    private String command;

    /**
     * Cron expression that defines the schedule.
     * Supported formats follow Spring's {@code CronExpression} parser
     * (6 fields: second minute hour day-of-month month day-of-week).
     */
    private String cronExpression;

    /** Whether this task is currently enabled. */
    private boolean enabled = true;

    /** Optional free-text description. */
    private String description;
}
