package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Exception sample entity corresponding to the t_sample_exception table.
 * <p>
 * Stores error descriptions when a monitor fails to read data.
 */
@Data
@TableName("t_sample_exception")
public class SampleExceptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Monitor id (reference to t_probe.id or t_control.id). */
    @TableField("monitor")
    private Integer monitorId;

    /** Error description. */
    @TableField("`value`")
    private String error;

    /** Sample timestamp. */
    @TableField("moment")
    private LocalDateTime sampleTime;
}
