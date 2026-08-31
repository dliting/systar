package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Scheduled task entity for the t_scheduled_task table.
 * <p>
 * Defines a cron-based command to be sent to a {@link com.systar.monitor.asset.Control}
 * at the times specified by {@link #cronExpression}.
 */
@Data
@TableName("t_scheduled_task")
public class ScheduledTaskEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Task name (required). */
    private String name;

    /** Id of the {@link com.systar.monitor.asset.Control} asset to execute. */
    private Integer controlId;

    /** Command string to pass to {@code Control.execute(String)}. */
    private String command;

    /** Spring-style cron expression (6 fields: second minute hour day-of-month month day-of-week). */
    private String cronExpression;

    /** Whether this task is currently enabled. */
    private Boolean enabled;

    /** Optional free-text description. */
    private String description;
}
