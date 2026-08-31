package com.systar.monitor.drivers.bacnet;

import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.drivers.bacnet.BacnetService.BacnetConnection;
import com.systar.monitor.result.IMonitorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BACnet Control supporting read-back (detect) and write (execute).
 * <p>
 * Uses the ControlType's DataType to determine the correct BACnet encoding:
 * BOOLEAN → UnsignedInteger(0/1), FLOAT/INT → Real.
 * Address fields are populated via reflective property binding from metadata.
 */
public class BacnetControl extends Control {

    private static final Logger LOG = LoggerFactory.getLogger(BacnetControl.class);

    protected int objectType;
    protected int instanceNumber;
    protected int propertyIdentifier = BacnetProbe.PROP_PRESENT_VALUE;

    @Override
    public void detect(IMonitorResult result) throws Exception {
        BacnetService svc = getService();
        BacnetConnection conn = null;
        try {
            conn = (BacnetConnection) svc.getConnection();
            Object raw = conn.read(objectType, instanceNumber, propertyIdentifier);
            if (raw != null) {
                result.setValue(raw);
            } else {
                result.setError("BACnet read returned null");
            }
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("BACnet detect failed for {}: {}", getName(), e.getMessage());
            result.setError(e.getMessage());
            if (conn != null) conn.close();
        } finally {
            if (conn != null) svc.releaseConnection(conn);
        }
    }

    @Override
    public void execute(String command) throws Exception {
        BacnetService svc = getService();
        BacnetConnection conn = null;
        try {
            conn = (BacnetConnection) svc.getConnection();
            Encodable value = toEncodable(command);
            conn.write(objectType, instanceNumber, propertyIdentifier, value);
        } catch (Exception e) {
            LOG.warn("BACnet execute failed for {}: {}", getName(), e.getMessage());
            if (conn != null) conn.close();
            throw e;
        } finally {
            if (conn != null) svc.releaseConnection(conn);
        }
    }

    protected Encodable toEncodable(String value) {
        DataType dt = resolveDataType();
        if (dt == DataType.BOOLEAN) {
            return new UnsignedInteger(Boolean.parseBoolean(value) ? 1 : 0);
        }
        return new Real(Float.parseFloat(value));
    }

    protected DataType resolveDataType() {
        return getType() != null && getType().getDataType() != null
                ? getType().getDataType() : DataType.FLOAT;
    }

    private BacnetService getService() {
        if (!(getSource() instanceof BacnetService svc)) {
            throw new IllegalStateException("BacnetControl must belong to a BacnetService");
        }
        return svc;
    }

    // ======================== getters / setters ========================

    public int getObjectType()                   { return objectType; }
    public void setObjectType(int objectType)    { this.objectType = objectType; }

    public int getInstanceNumber()                      { return instanceNumber; }
    public void setInstanceNumber(int instanceNumber)    { this.instanceNumber = instanceNumber; }

    public int getPropertyIdentifier()                          { return propertyIdentifier; }
    public void setPropertyIdentifier(int propertyIdentifier)    { this.propertyIdentifier = propertyIdentifier; }
}
