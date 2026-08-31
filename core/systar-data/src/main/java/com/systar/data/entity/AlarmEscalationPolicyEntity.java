package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_alarm_escalation_policy")
public class AlarmEscalationPolicyEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Policy name. */
    private String name;

    /** Original alarm level: 1=WARNING, 2=ERROR, 3=FATAL. */
    private Integer fromLevel;

    /** Escalated level. */
    private Integer toLevel;

    /** Timeout in minutes before escalation. */
    private Integer timeoutMinutes;

    /** Notification type: site_notice, sms, phone. */
    private String notifyType;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
