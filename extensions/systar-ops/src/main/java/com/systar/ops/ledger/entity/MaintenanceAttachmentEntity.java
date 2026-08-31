package com.systar.ops.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_maintenance_attachment")
public class MaintenanceAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long maintenanceId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
