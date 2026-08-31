package com.systar.server.controller.vo;

import lombok.Data;

@Data
public class ProbeValueVO {
    private int id;
    private String name;
    private String caption;
    private Object value;
    private String state;
    private long lastDetectTime;
}
