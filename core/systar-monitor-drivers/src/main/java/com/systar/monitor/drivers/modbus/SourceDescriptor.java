package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.type.DataType;

/**
 * Parsed descriptor of a Modbus source string.
 * Holds the register type, 0-based address, and data type.
 */
public record SourceDescriptor(
        String registerType,
        int address,
        String dataType) {

    // Register type constants shared across Modbus utilities
    public static final String TYPE_HOLDING = "holding";
    public static final String TYPE_INPUT   = "input";
    public static final String TYPE_COIL    = "coil";
    public static final String TYPE_DISCRETE = "discrete";

    // Data type constants for value reading
    public static final String DATA_FLOAT = "float";
    public static final String DATA_INT   = "int";
    public static final String DATA_SHORT = "short";
    public static final String DATA_LONG  = "long";
    public static final String DATA_BOOL  = "bool";

    /**
     * Derives the Modbus register type from a DataType.
     * BOOLEAN → coil; INT/FLOAT → holding; others → null.
     */
    public static String deriveRegisterType(DataType dataType) {
        if (dataType == null
                || dataType == DataType.STRING
                || dataType == DataType.TIMESPAN) {
            return null;
        }
        return dataType == DataType.BOOLEAN ? TYPE_COIL : TYPE_HOLDING;
    }

    /**
     * Derives the Modbus data-type string from a DataType.
     * BOOLEAN → bool, INT → int, FLOAT → float; others → null.
     */
    public static String deriveDataTypeStr(DataType dataType) {
        if (dataType == null) {
            return null;
        }
        return dataType == DataType.BOOLEAN ? DATA_BOOL
             : dataType == DataType.INT    ? DATA_INT
             : dataType == DataType.FLOAT  ? DATA_FLOAT : null;
    }

    /**
     * Creates a SourceDescriptor from DataType and 0-based address.
     * Returns null if DataType is unsupported for Modbus.
     */
    public static SourceDescriptor fromDataType(DataType dataType, int address) {
        String registerType = deriveRegisterType(dataType);
        String dataTypeStr  = deriveDataTypeStr(dataType);
        if (registerType == null || dataTypeStr == null) {
            return null;
        }
        return new SourceDescriptor(registerType, address, dataTypeStr);
    }
}
