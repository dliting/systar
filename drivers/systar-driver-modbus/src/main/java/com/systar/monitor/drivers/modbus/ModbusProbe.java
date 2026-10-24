package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus probe that reads a register or coil value.
 * <p>
 * The register type and data type are derived from the type's DataType:
 * BOOLEAN → coil/bool, INT → holding/int, FLOAT → holding/float.
 * The address is taken from the RegisterAddr metadata attribute (0-based offset).
 * <p>
 * The type-level Source field is a service reference name (e.g. "ModbusMaster"),
 * not a Modbus address. Address resolution happens lazily at detect time.
 */
public class ModbusProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusProbe.class);

    /** Metadata key for the register address (0-based offset). */
    private static final String ATTR_REGISTER_ADDR = "RegisterAddr";

    private SourceDescriptor cachedDescriptor;

    public ModbusProbe() {
        super();
    }

    @Override
    public void init(com.systar.monitor.asset.type.ProbeType type, int id, String name) {
        super.init(type, id, name);
        // type.getSource() is a service reference name (e.g. "ModbusMaster"), not a
        // Modbus address. Address resolution happens at detect time from metadata.
    }

    SourceDescriptor getCachedDescriptor() {
        return cachedDescriptor;
    }

    @Override
    public void detect(IMonitorResult result) throws Exception {
        SourceDescriptor desc = resolveDescriptor();
        if (desc == null) {
            result.setError("Modbus probe source is not configured");
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
            LOG.warn("Modbus probe detect failed for {} [desc={}]", getName(), desc, e);
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

    // ======================== helpers ========================

    /**
     * Resolves the source descriptor from instance metadata and type DataType.
     * <p>
     * The register type and data type string are derived from the type's DataType:
     * BOOLEAN → coil/bool, INT → holding/int, FLOAT → holding/float.
     * The address is taken from the RegisterAddr metadata attribute (0-based offset).
     * <p>
     * Caches the descriptor after first successful resolution.
     */
    private SourceDescriptor resolveDescriptor() {
        if (cachedDescriptor != null) {
            return cachedDescriptor;
        }

        com.systar.monitor.asset.type.ProbeType type = getType();
        if (type == null) {
            return null;
        }

        DataType dataType = type.getDataType();
        if (dataType == null) {
            LOG.warn("ModbusProbe '{}' has no DataType — cannot resolve register type", getName());
            return null;
        }

        Integer addr = getMetadata(ATTR_REGISTER_ADDR);
        if (addr == null) {
            LOG.debug("ModbusProbe '{}' has no RegisterAddr metadata — not bound yet", getName());
            return null;
        }

        SourceDescriptor desc = SourceDescriptor.fromDataType(dataType, addr);
        if (desc == null) {
            LOG.warn("ModbusProbe '{}' has unsupported DataType: {}", getName(), dataType);
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
                "ModbusProbe '" + getName() + "' is not attached to a ModbusService. "
                        + "Actual source: " + source);
    }
}
