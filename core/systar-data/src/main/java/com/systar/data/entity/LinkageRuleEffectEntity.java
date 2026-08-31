package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Linkage rule effect entity corresponding to the t_linkage_rule_effect table.
 * <p>
 * Defines the action (effect) taken when a linkage rule fires:
 * the specified controller is set to the given value.
 */
@Data
@TableName("t_linkage_rule_effect")
public class LinkageRuleEffectEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Reference to the linkage rule this effect belongs to. */
    @TableField("rule_id")
    private Integer ruleId;

    /** The controller or device asset id to act upon. */
    @TableField("asset_id")
    private Integer effectMonitorId;

    /** The command to send to the target controller (-1 = toggle). */
    @TableField("command")
    private String effectCommand;
}
