package com.systar.simulator.model;

public abstract class ProtocolEndpoint {
    private String host = "0.0.0.0";

    public String getHost()             { return host; }
    public void setHost(String host)    { this.host = host; }
}
