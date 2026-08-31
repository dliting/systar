package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Linkage log entity corresponding to the t_linkage_log table.
 * <p>
 * Records the history of linkage rule executions.
 */
@Data
@TableName("t_linkage_log")
public class LinkageLogEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** The linkage rule id that triggered this event. */
    @TableField("rule_id")
    private Integer ruleId;

    /** The cause monitor (asset) id. */
    private Integer causeMonitorId;

    /** The effect monitor (asset) id. */
    private Integer effectMonitorId;

    /** When the linkage was triggered. */
    @TableField("time")
    private LocalDateTime triggerTime;

    /** The command that was sent to the effect target. */
    private String effectCommand;

    /** Whether the linkage execution succeeded. */
    private Boolean success;
}
