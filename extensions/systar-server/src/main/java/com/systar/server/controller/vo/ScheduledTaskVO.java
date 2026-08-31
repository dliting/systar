package com.systar.server.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduledTaskVO {
    private int           id;
    private String        name;
    private int           controlId;
    private String        command;
    private String        cronExpression;
    private boolean       enabled;
    /** Display name of the target asset (resolved from AssetStore). */
    private String        targetName;
    private String        description;
    private LocalDateTime nextFireTime;
}
