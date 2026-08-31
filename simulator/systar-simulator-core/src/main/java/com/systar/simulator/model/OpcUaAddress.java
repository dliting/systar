package com.systar.simulator.model;

public class OpcUaAddress implements DataPointAddress {
    private int     namespaceIndex;
    private String  identifier;
    private boolean integerId;

    public OpcUaAddress() {}

    public OpcUaAddress(int namespaceIndex, String identifier, boolean integerId) {
        this.namespaceIndex = namespaceIndex;
        this.identifier     = identifier;
        this.integerId      = integerId;
    }

    public int getNamespaceIndex()              { return namespaceIndex; }
    public void setNamespaceIndex(int v)        { this.namespaceIndex = v; }
    public String getIdentifier()               { return identifier; }
    public void setIdentifier(String v)         { this.identifier = v; }
    public boolean isIntegerId()                { return integerId; }
    public void setIntegerId(boolean v)         { this.integerId = v; }
}
