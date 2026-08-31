package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Integer sample entity corresponding to the t_sample_int table.
 * <p>
 * Stores integer monitoring values over time.
 */
@Data
@TableName("t_sample_int")
public class SampleIntEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Monitor id (reference to t_probe.id or t_control.id). */
    @TableField("monitor")
    private Integer monitorId;

    /** Measured integer value. */
    @TableField("`value`")
    private Integer value;

    /** Sample timestamp. */
    @TableField("moment")
    private LocalDateTime sampleTime;
}
