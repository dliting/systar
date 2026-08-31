package com.systar.simulator.model;

public class ModbusAddress implements DataPointAddress {
    public static final String TYPE_HOLDING  = "holding";
    public static final String TYPE_INPUT    = "input";
    public static final String TYPE_COIL     = "coil";
    public static final String TYPE_DISCRETE = "discrete";

    public static final String DATA_FLOAT = "float";
    public static final String DATA_INT   = "int";
    public static final String DATA_SHORT = "short";
    public static final String DATA_LONG  = "long";
    public static final String DATA_BOOL  = "bool";

    private String registerType;
    private int    offset;
    private String dataType;

    public ModbusAddress() {}

    public ModbusAddress(String registerType, int offset, String dataType) {
        this.registerType = registerType;
        this.offset       = offset;
        this.dataType     = dataType;
    }

    public int registerCount() {
        return switch (dataType) {
            case DATA_FLOAT, DATA_LONG -> 2;
            default -> 1;
        };
    }

    public String getRegisterType()           { return registerType; }
    public void setRegisterType(String v)     { this.registerType = v; }
    public int getOffset()                    { return offset; }
    public void setOffset(int v)              { this.offset = v; }
    public String getDataType()               { return dataType; }
    public void setDataType(String v)         { this.dataType = v; }
}
