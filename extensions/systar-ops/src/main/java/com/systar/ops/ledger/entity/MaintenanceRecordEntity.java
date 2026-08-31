package com.systar.ops.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_maintenance_record")
public class MaintenanceRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer deviceId;
    private String type;
    private String title;
    private String description;
    private Long performerId;
    private Long creatorId;
    private LocalDateTime performedAt;
    private BigDecimal cost;
    private String result;
    private LocalDate nextMaintenanceDate;
    private Long workOrderId;
    private Long inspectionTaskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
