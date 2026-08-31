package com.systar.monitor.linkage;

/**
 * Event published when an alarm correlation group is formed.
 * Linkage rules with causeType = CORRELATION_GROUP can react to this.
 */
public class CorrelationGroupEvent {

    private final Object source;
    private final String correlationGroup;
    private final Integer deviceId;
    private final int monitorId;

    public CorrelationGroupEvent(Object source, String correlationGroup,
                                  Integer deviceId, int monitorId) {
        this.source           = source;
        this.correlationGroup = correlationGroup;
        this.deviceId         = deviceId;
        this.monitorId        = monitorId;
    }

    public Object getSource() {
        return source;
    }

    public String getCorrelationGroup() {
        return correlationGroup;
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public int getMonitorId() {
        return monitorId;
    }
}
