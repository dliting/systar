package com.systar.ops.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_inspection_result")
public class InspectionResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer deviceId;
    private Long templateId;
    private String itemName;
    private String expectedValue;
    private String checkResult;
    private String actualValue;
    private String remark;
    private LocalDateTime createdAt;
}
