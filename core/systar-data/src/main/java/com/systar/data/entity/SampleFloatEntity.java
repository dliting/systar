package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Float sample entity corresponding to the t_sample_float table.
 * <p>
 * Stores float monitoring values over time.
 */
@Data
@TableName("t_sample_float")
public class SampleFloatEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Monitor id (reference to t_probe.id or t_control.id). */
    @TableField("monitor")
    private Integer monitorId;

    /** Measured float value. */
    @TableField("`value`")
    private Float value;

    /** Sample timestamp. */
    @TableField("moment")
    private LocalDateTime sampleTime;
}
