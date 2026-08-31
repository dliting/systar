package com.systar.server.controller.vo;

import lombok.Data;

@Data
public class ScheduledTaskLogVO {
    private long    id;
    private int     taskId;
    private String  taskName;
    private int     controlId;
    private String  command;
    private long    executeTime;
    private boolean success;
    private String  errorMessage;
}
