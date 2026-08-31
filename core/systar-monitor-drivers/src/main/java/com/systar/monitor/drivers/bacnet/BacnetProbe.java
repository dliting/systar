package com.systar.monitor.drivers.bacnet;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.bacnet.BacnetService.BacnetConnection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BACnet probe that reads object properties via BACnet/IP.
 * <p>
 * Source format: {@code objectType:instanceNumber:propertyIdentifier}
 * E.g. {@code analogInput:0:presentValue}, {@code analogInput:1:description}.
 */
public class BacnetProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(BacnetProbe.class);

    // BACnet object type identifiers (ASHRAE 135)
    static final int OBJ_ANALOG_INPUT = 0;
    static final int OBJ_ANALOG_OUTPUT = 1;
    static final int OBJ_ANALOG_VALUE = 2;
    static final int OBJ_BINARY_INPUT = 3;
    static final int OBJ_BINARY_OUTPUT = 4;
    static final int OBJ_BINARY_VALUE = 5;
    static final int OBJ_MULTI_STATE_INPUT = 13;
    static final int OBJ_MULTI_STATE_OUTPUT = 14;
    static final int OBJ_MULTI_STATE_VALUE = 19;

    // BACnet property identifiers (ASHRAE 135)
    static final int PROP_PRESENT_VALUE = 85;
    static final int PROP_DESCRIPTION = 28;
    static final int PROP_UNITS = 117;
    static final int PROP_OBJECT_NAME = 77;
    static final int PROP_STATUS_FLAGS = 111;

    /** BACnet object type name (e.g. "analogInput"). */
    private String objectTypeName;
    /** BACnet object type identifier. */
    private int objectType;
    /** BACnet object instance number. */
    private int instanceNumber;
    /** BACnet property identifier. */
    private int propertyIdentifier;

    public BacnetProbe() {
    }

    @Override
    public void init(ProbeType type, int id, String name) {
        super.init(type, id, name);
        if (type != null && type.getSource() != null) {
            parseSource(type.getSource());
        }
    }

    // ======================== source parsing ========================

    private void parseSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("BACnet source must not be empty");
        }
        String[] parts = source.trim().split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "BACnet source format: objectType:instance:property, got: " + source);
        }
        this.objectTypeName = parts[0].trim();
        this.objectType = resolveObjectType(objectTypeName);
        this.instanceNumber = Integer.parseInt(parts[1].trim());
        this.propertyIdentifier = resolveProperty(parts[2].trim());
    }

    private int resolveObjectType(String name) {
        return switch (name) {
            case "analogInput" -> OBJ_ANALOG_INPUT;
            case "analogOutput" -> OBJ_ANALOG_OUTPUT;
            case "analogValue" -> OBJ_ANALOG_VALUE;
            case "binaryInput" -> OBJ_BINARY_INPUT;
            case "binaryOutput" -> OBJ_BINARY_OUTPUT;
            case "binaryValue" -> OBJ_BINARY_VALUE;
            case "multiStateInput" -> OBJ_MULTI_STATE_INPUT;
            case "multiStateOutput" -> OBJ_MULTI_STATE_OUTPUT;
            case "multiStateValue" -> OBJ_MULTI_STATE_VALUE;
            default -> throw new IllegalArgumentException(
                    "Unknown BACnet object type: " + name);
        };
    }

    private int resolveProperty(String name) {
        return switch (name) {
            case "presentValue" -> PROP_PRESENT_VALUE;
            case "description" -> PROP_DESCRIPTION;
            case "units" -> PROP_UNITS;
            case "objectName" -> PROP_OBJECT_NAME;
            case "statusFlags" -> PROP_STATUS_FLAGS;
            default -> throw new IllegalArgumentException(
                    "Unknown BACnet property: " + name);
        };
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (objectTypeName == null) {
            ProbeType type = getType();
            String source = type != null ? type.getSource() : null;
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof BacnetService bacnetService)) {
            throw new IllegalStateException("BacnetProbe must belong to a BacnetService");
        }

        BacnetConnection conn = null;
        try {
            conn = (BacnetConnection) bacnetService.getConnection();
            Object value = conn.read(objectType, instanceNumber, propertyIdentifier);
            if (value == null) {
                result.setError("No data for BACnet " + objectTypeName
                        + ":" + instanceNumber + ":" + propertyIdentifier);
            }
            result.setValue(value);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("BACnet read failed for {}:{}:{} at {}:{}",
                    objectTypeName, instanceNumber, propertyIdentifier,
                    bacnetService.getRemoteHost(), bacnetService.getRemotePort(), e);
            result.setError("BACnet read failed: " + e.getMessage());
            if (conn != null) { conn.close(); }
        } finally {
            if (conn != null) bacnetService.releaseConnection(conn);
        }
    }

    // ======================== getters / setters ========================

    public String getObjectTypeName() { return objectTypeName; }
    public int getObjectType() { return objectType; }
    public int getInstanceNumber() { return instanceNumber; }
    public int getPropertyIdentifier() { return propertyIdentifier; }
}
