package com.systar.simulator.model;

public class ModbusTcpEndpoint extends ProtocolEndpoint {
    private int port;
    private int unitId = 1;

    public int getPort()                { return port; }
    public void setPort(int port)       { this.port = port; }
    public int getUnitId()              { return unitId; }
    public void setUnitId(int unitId)   { this.unitId = unitId; }
}
