package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Boolean sample entity corresponding to the t_sample_boolean table.
 * <p>
 * Stores boolean monitoring values over time (0=false, 1=true).
 */
@Data
@TableName("t_sample_boolean")
public class SampleBooleanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Monitor id (reference to t_probe.id or t_control.id). */
    @TableField("monitor")
    private Integer monitorId;

    /** Measured boolean value (stored as tinyint: 0=false, 1=true). */
    @TableField("`value`")
    private Boolean value;

    /** Sample timestamp. */
    @TableField("moment")
    private LocalDateTime sampleTime;
}
