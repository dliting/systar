package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Linkage rule cause entity corresponding to the t_linkage_rule_cause table.
 * <p>
 * Defines the trigger (cause) condition for a linkage rule:
 * when the specified asset's state changes to the given value,
 * the linkage rule fires.
 */
@Data
@TableName("t_linkage_rule_cause")
public class LinkageRuleCauseEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("rule_id")
    private Integer ruleId;

    /** The asset (monitor) that triggers the linkage. */
    @TableField("asset_id")
    private Integer causeMonitorId;

    /** The trigger value that fires the linkage. */
    @TableField("trigger_value")
    private String triggerValue;
}
