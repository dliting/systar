package com.systar.server.dto;

import lombok.Data;

@Data
public class ScheduledTaskCreateRequest {
    private String  name;
    private int     controlId;
    private String  command;
    private String  cronExpression;
    private String  description;
    private Boolean enabled;
}
