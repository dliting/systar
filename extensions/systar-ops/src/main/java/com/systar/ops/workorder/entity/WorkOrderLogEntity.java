package com.systar.ops.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_work_order_log")
public class WorkOrderLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private Long operatorId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
