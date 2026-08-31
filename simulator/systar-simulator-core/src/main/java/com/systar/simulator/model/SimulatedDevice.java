package com.systar.simulator.model;

import java.util.ArrayList;
import java.util.List;

public class SimulatedDevice {
    private String             id;
    private String             name;
    private ProtocolType       protocol;
    private ProtocolEndpoint   endpoint;
    private List<DataPoint>    dataPoints = new ArrayList<>();
    private volatile DeviceStatus status = DeviceStatus.STOPPED;
    private boolean            enabled = true;
    private volatile FaultType activeFault;

    public SimulatedDevice() {}

    public String              getId()                        { return id; }
    public void                setId(String id)               { this.id = id; }
    public String              getName()                      { return name; }
    public void                setName(String name)           { this.name = name; }
    public ProtocolType        getProtocol()                  { return protocol; }
    public void                setProtocol(ProtocolType p)    { this.protocol = p; }
    public ProtocolEndpoint    getEndpoint()                  { return endpoint; }
    public void                setEndpoint(ProtocolEndpoint e){ this.endpoint = e; }
    public List<DataPoint>     getDataPoints()                { return dataPoints; }
    public void                setDataPoints(List<DataPoint> dps) { this.dataPoints = dps; }
    public DeviceStatus        getStatus()                    { return status; }
    public void                setStatus(DeviceStatus s)      { this.status = s; }
    public boolean             isEnabled()                    { return enabled; }
    public void                setEnabled(boolean enabled)    { this.enabled = enabled; }
    public FaultType           getActiveFault()               { return activeFault; }
    public void                setActiveFault(FaultType f)    { this.activeFault = f; }
}
