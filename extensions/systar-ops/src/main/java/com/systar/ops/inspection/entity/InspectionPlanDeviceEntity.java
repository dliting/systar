package com.systar.ops.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_inspection_plan_device")
public class InspectionPlanDeviceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Integer deviceId;
}
