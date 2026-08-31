package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Persistent execution log for scheduled control tasks.
 * Mirrors {@link com.systar.monitor.control.ScheduledTaskLog} POJO fields.
 */
@Data
@TableName("t_scheduled_task_log")
public class ScheduledTaskLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** References {@link ScheduledTaskEntity#getId()}. */
    private Integer taskId;

    /** Snapshot of the task name at execution time. */
    private String taskName;

    /** Snapshot of the control asset id at execution time. */
    private Integer controlId;

    /** Snapshot of the command string that was sent. */
    private String command;

    /** Epoch-millis timestamp when the command was executed. */
    private Long executeTime;

    /** Whether the command completed without error. */
    private Boolean success;

    /** Error message if {@link #success} is false, otherwise null. */
    private String errorMessage;
}
