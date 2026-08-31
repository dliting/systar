package com.systar.simulator.model;

public class OpcUaEndpoint extends ProtocolEndpoint {
    private int    port;
    private String securityPolicy = "None";
    private String serverName     = "systar-simulator";

    public int getPort()                                { return port; }
    public void setPort(int port)                       { this.port = port; }
    public String getSecurityPolicy()                   { return securityPolicy; }
    public void setSecurityPolicy(String v)             { this.securityPolicy = v; }
    public String getServerName()                       { return serverName; }
    public void setServerName(String v)                 { this.serverName = v; }
}
