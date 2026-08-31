package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.systar.monitor.alarm.AlarmStrategy;
import lombok.Data;

/**
 * Alarm rule entity corresponding to the t_alarm_rule table.
 * <p>
 * Defines how alarms are triggered for a specific monitor.
 */
@Data
@TableName("t_alarm_rule")
public class AlarmRuleEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Monitor (asset) id this rule applies to. */
    @TableField("asset_id")
    private Integer monitorId;

    /** Alarm strategy: ONLY_ONCE, CONTINUOUS, or SELECTIVE. */
    @TableField("rule")
    private AlarmStrategy strategy;

    /** Alarm notification method (1=sound, 2=email, 4=UI, etc.). */
    private Integer way;

    /** Event rank (severity level) id. */
    @TableField("warn_id")
    private Integer eventRankId;

    /** Message template for alarm notifications. */
    private String messageTemplate;

    /** Whether this rule is enabled. */
    private Integer enabled;

    /** Start from the N-th consecutive error before alarming. */
    private Integer start;
}
