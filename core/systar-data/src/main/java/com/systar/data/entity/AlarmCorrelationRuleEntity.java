package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_alarm_correlation_rule")
public class AlarmCorrelationRuleEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Rule name. */
    private String name;

    /** Target device ID. NULL means all devices. */
    private Integer deviceId;

    /** Correlation time window in seconds. */
    private Integer windowSeconds;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
