package com.systar.server.dto;

import lombok.Data;

@Data
public class ScheduledTaskUpdateRequest {
    private String  name;
    private Integer controlId;
    private String  command;
    private String  cronExpression;
    private String  description;
}
