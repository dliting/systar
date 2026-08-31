package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_alarm_silence_window")
public class AlarmSilenceWindowEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Silence window name. */
    private String name;

    /** Target device ID. NULL means all devices. */
    private Integer deviceId;

    /** Target monitor ID. NULL means all monitors. */
    private Integer monitorId;

    /** Silence start time. */
    private LocalDateTime startTime;

    /** Silence end time. */
    private LocalDateTime endTime;

    /** Reason for silence. */
    private String reason;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
