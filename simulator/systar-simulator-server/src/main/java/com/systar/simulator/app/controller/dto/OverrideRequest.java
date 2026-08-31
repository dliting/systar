package com.systar.simulator.app.controller.dto;

/**
 * Request body for applying a static value override to a data point.
 */
public class OverrideRequest {

    private Object value;
    private int    durationSeconds;

    public Object getValue()               { return value; }
    public void   setValue(Object value)    { this.value = value; }
    public int    getDurationSeconds()     { return durationSeconds; }
    public void   setDurationSeconds(int v) { this.durationSeconds = v; }
}
