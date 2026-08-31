package com.systar.ops.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_work_order")
public class WorkOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private String title;
    private String description;
    private String type;
    private String source;
    private Integer alarmMessageId;
    private Long inspectionTaskId;
    private Integer deviceId;
    private Integer spaceId;
    private Integer priority;
    private String status;
    private Long assigneeId;
    private Long creatorId;
    private Long closedBy;
    private LocalDateTime dueTime;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
