package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus control that reads state and writes register/coil values.
 * <p>
 * The read address is resolved from instance-level metadata (e.g. InRegisterAddr
 * attribute) at detect time, not from the type-level Source field, which is a
 * service type reference (e.g. "ModbusMaster").
 * <p>
 * The register type and data type are derived from the type's DataType:
 * BOOLEAN → coil/bool, INT → holding/int, FLOAT → holding/float.
 * <p>
 * The execute command format:
 * <pre>
 *   register:40001:value:100      - Write 100 to holding register (FC06)
 *   register:0:value:200           - Write 200 to holding register (0-based)
 *   coil:1:bool:true              - Write ON to coil (FC05)
 *   coil:1:bool:false             - Write OFF to coil (FC05)
 * </pre>
 */
public class ModbusControl extends Control {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusControl.class);

    private static final String CMD_REGISTER = "register";
    private static final String CMD_COIL     = "coil";

    /** Metadata key for the input (read) register address. */
    private static final String ATTR_IN_REGISTER_ADDR = "InRegisterAddr";

    private SourceDescriptor cachedDescriptor;

    SourceDescriptor getCachedDescriptor() {
        return cachedDescriptor;
    }

    public ModbusControl() {
        super();
    }

    @Override
    public void init(com.systar.monitor.asset.type.ControlType type, int id, String name) {
        super.init(type, id, name);
        // type.getSource() is a service reference name (e.g. "ModbusMaster"), not a
        // Modbus address. Address resolution happens at detect time from metadata.
    }

    // ======================== detect (read current state) ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        SourceDescriptor desc = resolveDescriptor();
        if (desc == null) {
            result.setError("Modbus control source is not configured");
            return;
        }

        ModbusService service = getService();
        ModbusConnection conn = null;
        try {
            conn = service.getModbusConnection();

            Object value = ModbusValueReader.readValue(conn, desc);

            result.setValue(value);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("Modbus control detect failed for {} [desc={}]", getName(), desc, e);
            result.setError("Modbus read failed: " + e.getMessage());
            if (conn != null) {
                conn.close();
            }
            conn = null;
        } finally {
            if (conn != null) {
                service.releaseConnection(conn);
            }
        }
    }

    // ======================== execute (write command) ========================

    @Override
    public void execute(String command) throws Exception {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Modbus control command is empty");
        }

        ModbusService service = getService();
        ModbusConnection conn = null;
        try {
            conn = service.getModbusConnection();
            executeCommand(conn, command);
        } catch (Exception e) {
            LOG.warn("Modbus control execute failed for {} [command={}]", getName(), command, e);
            if (conn != null) {
                conn.close();
            }
            conn = null;
            throw e;
        } finally {
            if (conn != null) {
                service.releaseConnection(conn);
            }
        }
    }

    // ======================== command parsing ========================

    /**
     * Parses and executes a command.
     * <p>
     * Register command: {@code register:address:value:number}
     * Coil command: {@code coil:address:bool:true|false}
     */
    private void executeCommand(ModbusConnection conn, String command) throws Exception {
        String[] parts = command.split(":");
        if (parts.length < 1) {
            throw new IllegalArgumentException("Invalid Modbus command: " + command);
        }

        String cmdType = parts[0].trim().toLowerCase();

        switch (cmdType) {
            case CMD_REGISTER -> executeWriteRegister(conn, parts);
            case CMD_COIL     -> executeWriteCoil(conn, parts);
            default -> throw new IllegalArgumentException(
                    "Unknown Modbus command type: " + cmdType);
        }
    }

    private void executeWriteRegister(ModbusConnection conn, String[] parts) throws Exception {
        // register:address:value:number
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "Register write command format: register:address:value:number");
        }

        int rawAddress = Integer.parseInt(parts[1].trim());
        // "value" literal is parts[2], the actual number is parts[3]
        int value = Integer.parseInt(parts[3].trim());

        int address = ModbusAddressParser.convertAddress(SourceDescriptor.TYPE_HOLDING, rawAddress);

        conn.writeSingleRegister(address, value);
        LOG.info("Modbus write register: address={}, value={}", address, value);
    }

    private void executeWriteCoil(ModbusConnection conn, String[] parts) throws Exception {
        // coil:address:bool:true|false
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "Coil write command format: coil:address:bool:true|false");
        }

        int rawAddress = Integer.parseInt(parts[1].trim());
        boolean value   = Boolean.parseBoolean(parts[3].trim());

        int address = ModbusAddressParser.convertAddress(SourceDescriptor.TYPE_COIL, rawAddress);

        conn.writeCoil(address, value);
        LOG.info("Modbus write coil: address={}, value={}", address, value);
    }

    // ======================== helpers ========================

    /**
     * Resolves the source descriptor from instance metadata and type DataType.
     * <p>
     * The register type and data type string are derived from the type's DataType:
     * BOOLEAN → coil/bool, INT → holding/int, FLOAT → holding/float.
     * The address is taken from the InRegisterAddr metadata attribute (0-based offset).
     * <p>
     * Caches the descriptor after first successful resolution.
     */
    private SourceDescriptor resolveDescriptor() {
        if (cachedDescriptor != null) {
            return cachedDescriptor;
        }

        com.systar.monitor.asset.type.ControlType type = getType();
        if (type == null) {
            return null;
        }

        DataType dataType = type.getDataType();
        if (dataType == null) {
            LOG.warn("ModbusControl '{}' has no DataType — cannot resolve register type", getName());
            return null;
        }

        Integer addr = getMetadata(ATTR_IN_REGISTER_ADDR);
        if (addr == null) {
            LOG.debug("ModbusControl '{}' has no InRegisterAddr metadata — not bound yet", getName());
            return null;
        }

        SourceDescriptor desc = SourceDescriptor.fromDataType(dataType, addr);
        if (desc == null) {
            LOG.warn("ModbusControl '{}' has unsupported DataType: {}", getName(), dataType);
            return null;
        }

        cachedDescriptor = desc;
        return cachedDescriptor;
    }

    private ModbusService getService() {
        MonitorService source = getSource();
        if (source instanceof ModbusService ms) {
            return ms;
        }
        throw new IllegalStateException(
                "ModbusControl '" + getName() + "' is not attached to a ModbusService. "
                        + "Actual source: " + source);
    }
}
