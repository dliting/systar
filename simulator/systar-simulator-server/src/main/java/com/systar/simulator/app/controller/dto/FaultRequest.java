package com.systar.simulator.app.controller.dto;

import com.systar.simulator.model.FaultType;

/**
 * Request body for injecting a fault condition into a device.
 */
public class FaultRequest {

    private FaultType type;
    private int       durationSeconds;

    public FaultType getType()              { return type; }
    public void      setType(FaultType type) { this.type = type; }
    public int       getDurationSeconds()    { return durationSeconds; }
    public void      setDurationSeconds(int v) { this.durationSeconds = v; }
}
