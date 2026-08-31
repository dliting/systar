package com.systar.ops.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_inspection_plan")
public class InspectionPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String cronExpression;
    private Integer enabled;
    private Long defaultAssigneeId;
    private Integer autoCreateWorkorder;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
