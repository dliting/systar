package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Error message log entity corresponding to the t_error_message_log table.
 * <p>
 * Records all alarm/error/warning events with details about the
 * monitor state at the time of the event.
 */
@Data
@TableName("t_error_message_log")
public class ErrorMessageLogEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Reference to t_alarm_rule.id. */
    private Integer alarmRuleId;

    /** Monitor (asset) id. */
    @TableField("asset_id")
    private Integer monitorId;

    /** Monitor name for display. */
    private String monitorName;

    /** Error message or warning condition. */
    @TableField("error_message")
    private String error;

    /** Current value of the monitor at the time of the event. */
    @TableField("`value`")
    private String value;

    /** State: 1=error, 2=warning. */
    private Integer state;

    /** Event rank (severity level). */
    @TableField("warn_id")
    private Integer eventRankId;

    /** Time when the error/warning occurred. */
    @TableField("time")
    private LocalDateTime logTime;

    /** Time when the error/warning was resolved. */
    private LocalDateTime endTime;
}
