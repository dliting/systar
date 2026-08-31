package com.systar.monitor.control;

import lombok.Data;

/**
 * Execution log entry for a {@link ScheduledTask}.
 * <p>
 * Records the outcome (success or failure) of a single control-command
 * execution triggered by the scheduler.
 */
@Data
public class ScheduledTaskLog {

    /** Auto-incremented log row id. */
    private long id;

    /** Id of the {@link ScheduledTask} that produced this log entry. */
    private int taskId;

    /** Snapshot of the task name at execution time. */
    private String taskName;

    /** Snapshot of the control asset id at execution time. */
    private int controlId;

    /** Snapshot of the command string that was sent. */
    private String command;

    /** Epoch-millis timestamp when the command was executed. */
    private long executeTime;

    /** Whether the command completed without error. */
    private boolean success;

    /** Error message if {@link #success} is false, otherwise null. */
    private String errorMessage;
}
