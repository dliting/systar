package com.systar.ops.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_device_attribute")
public class DeviceAttributeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer deviceId;
    private String attrKey;
    private String attrValue;
    private String attrType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
