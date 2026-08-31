package com.systar.ops.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_inspection_task")
public class InspectionTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String taskNo;
    private String status;
    private Long assigneeId;
    private LocalDateTime scheduledTime;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String remark;
    private LocalDateTime createdAt;
}
