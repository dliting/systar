package com.systar.simulator.model;

import java.util.List;
import java.util.Map;

/**
 * Intermediate representation parsed from YAML.
 * Used by ProfileParser to create SimulatedDevice instances.
 */
public class DeviceProfile {
    private String  id;
    private String  name;
    private String  protocol;
    private boolean enabled = true;
    private Map<String, Object> endpoint;
    private List<Map<String, Object>> dataPoints;

    public String getId()                             { return id; }
    public void setId(String id)                      { this.id = id; }
    public String getName()                           { return name; }
    public void setName(String name)                  { this.name = name; }
    public String getProtocol()                       { return protocol; }
    public void setProtocol(String protocol)          { this.protocol = protocol; }
    public boolean isEnabled()                        { return enabled; }
    public void setEnabled(boolean enabled)           { this.enabled = enabled; }
    public Map<String, Object> getEndpoint()          { return endpoint; }
    public void setEndpoint(Map<String, Object> e)    { this.endpoint = e; }
    public List<Map<String, Object>> getDataPoints()  { return dataPoints; }
    public void setDataPoints(List<Map<String, Object>> dps) { this.dataPoints = dps; }
}
