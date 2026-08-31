package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * System setting entity corresponding to the t_system_setting table.
 * <p>
 * Stores key-value configuration pairs for the monitoring system.
 */
@Data
@TableName("t_system_setting")
public class SystemSettingEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Setting key. */
    private String configKey;

    /** Setting value. */
    @TableField("`value`")
    private String value;

    /** Description of this setting. */
    private String description;
}
