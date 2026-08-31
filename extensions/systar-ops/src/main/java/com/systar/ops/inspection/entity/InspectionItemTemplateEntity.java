package com.systar.ops.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_inspection_item_template")
public class InspectionItemTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String itemName;
    private String itemType;
    private String expectedValue;
    private Integer sortOrder;
}
