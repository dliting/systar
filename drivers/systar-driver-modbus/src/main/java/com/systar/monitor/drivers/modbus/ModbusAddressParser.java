package com.systar.monitor.drivers.modbus;

/**
 * Parses Modbus source strings into {@link SourceDescriptor}.
 * Shared by {@link ModbusProbe} and {@link ModbusControl}.
 */
public final class ModbusAddressParser {

    private ModbusAddressParser() {}

    /**
     * Parses the source string into a descriptor.
     * Format: {@code type:address:dataType}
     * Example: {@code holding:40001:float}
     */
    public static SourceDescriptor parse(String source) {
        String[] parts = source.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Invalid Modbus source format: '" + source + "'. Expected 'type:address:dataType'");
        }

        String registerType = parts[0].trim().toLowerCase();
        int rawAddress = Integer.parseInt(parts[1].trim());
        String dataType = parts[2].trim().toLowerCase();

        int address = convertAddress(registerType, rawAddress);
        return new SourceDescriptor(registerType, address, dataType);
    }

    /**
     * Converts traditional Modbus address notation to 0-based addressing.
     * Holding: 4xxxx subtract 40001. Input: 3xxxx subtract 30001.
     * Coil: 0xxxx subtract 1. Discrete: 1xxxx subtract 10001.
     * Values below the base are assumed to already be 0-based.
     */
    public static int convertAddress(String registerType, int rawAddress) {
        return switch (registerType) {
            case SourceDescriptor.TYPE_HOLDING -> rawAddress >= 40001 ? rawAddress - 40001 : rawAddress;
            case SourceDescriptor.TYPE_INPUT -> rawAddress >= 30001 ? rawAddress - 30001 : rawAddress;
            case SourceDescriptor.TYPE_COIL -> rawAddress >= 1 ? rawAddress - 1 : rawAddress;
            case SourceDescriptor.TYPE_DISCRETE -> rawAddress >= 10001 ? rawAddress - 10001 : rawAddress;
            default -> rawAddress;
        };
    }
}
