package com.systar.ops.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_work_order_attachment")
public class WorkOrderAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
