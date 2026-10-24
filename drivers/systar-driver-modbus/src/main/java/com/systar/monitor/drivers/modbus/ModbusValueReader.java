package com.systar.monitor.drivers.modbus;

/**
 * Reads values from a Modbus connection based on a {@link SourceDescriptor}.
 * Shared by {@link ModbusProbe} and {@link ModbusControl}.
 */
public final class ModbusValueReader {

    private ModbusValueReader() {}

    /**
     * Reads a value from the connection according to the descriptor's
     * register type and data type.
     */
    public static Object readValue(ModbusConnection conn, SourceDescriptor desc) throws Exception {
        return switch (desc.registerType()) {
            case SourceDescriptor.TYPE_HOLDING -> readHoldingValue(conn, desc);
            case SourceDescriptor.TYPE_INPUT -> readInputValue(conn, desc);
            case SourceDescriptor.TYPE_COIL -> {
                boolean[] coils = conn.readCoils(desc.address(), 1);
                yield coils[0];
            }
            case SourceDescriptor.TYPE_DISCRETE -> {
                boolean[] inputs = conn.readDiscreteInputs(desc.address(), 1);
                yield inputs[0];
            }
            default -> throw new IllegalArgumentException(
                    "Unknown register type: " + desc.registerType());
        };
    }

    private static Object readHoldingValue(ModbusConnection conn, SourceDescriptor desc) throws Exception {
        return switch (desc.dataType()) {
            case SourceDescriptor.DATA_FLOAT -> {
                int[] regs = conn.readHoldingRegisters(desc.address(), 2);
                yield Float.intBitsToFloat((regs[0] << 16) | (regs[1] & 0xFFFF));
            }
            case SourceDescriptor.DATA_INT -> {
                int[] regs = conn.readHoldingRegisters(desc.address(), 1);
                yield regs[0] & 0xFFFF;
            }
            case SourceDescriptor.DATA_SHORT -> {
                int[] regs = conn.readHoldingRegisters(desc.address(), 1);
                yield (short) regs[0];
            }
            case SourceDescriptor.DATA_LONG -> {
                int[] regs = conn.readHoldingRegisters(desc.address(), 2);
                yield ((long) regs[0] << 16) | (regs[1] & 0xFFFFL);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported data type for holding register: " + desc.dataType());
        };
    }

    private static Object readInputValue(ModbusConnection conn, SourceDescriptor desc) throws Exception {
        return switch (desc.dataType()) {
            case SourceDescriptor.DATA_FLOAT -> {
                int[] regs = conn.readInputRegisters(desc.address(), 2);
                yield Float.intBitsToFloat((regs[0] << 16) | (regs[1] & 0xFFFF));
            }
            case SourceDescriptor.DATA_INT -> {
                int[] regs = conn.readInputRegisters(desc.address(), 1);
                yield regs[0] & 0xFFFF;
            }
            case SourceDescriptor.DATA_SHORT -> {
                int[] regs = conn.readInputRegisters(desc.address(), 1);
                yield (short) regs[0];
            }
            case SourceDescriptor.DATA_LONG -> {
                int[] regs = conn.readInputRegisters(desc.address(), 2);
                yield ((long) regs[0] << 16) | (regs[1] & 0xFFFFL);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported data type for input register: " + desc.dataType());
        };
    }
}
