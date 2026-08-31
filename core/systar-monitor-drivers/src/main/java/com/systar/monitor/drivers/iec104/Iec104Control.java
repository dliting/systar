package com.systar.monitor.drivers.iec104;

import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.drivers.iec104.Iec104Service.Iec104Connection;
import com.systar.monitor.result.IMonitorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IEC 104 Control supporting read-back (detect) and write (execute).
 * <p>
 * Uses the ControlType's DataType to determine the correct IEC 104 command:
 * BOOLEAN → singleCommand, FLOAT → setShortFloatCommand.
 * Address fields are populated via reflective property binding from metadata.
 */
public class Iec104Control extends Control {

    private static final Logger LOG = LoggerFactory.getLogger(Iec104Control.class);

    protected int address;
    protected int commonAddr;

    @Override
    public void detect(IMonitorResult result) throws Exception {
        Iec104Service svc = getService();
        Iec104Connection conn = null;
        try {
            conn = (Iec104Connection) svc.getConnection();
            Object value = conn.read(address);
            if (value != null) {
                result.setValue(value);
            } else {
                result.setError("IEC 104 read returned null for address " + address);
            }
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("IEC 104 detect failed for {}: {}", getName(), e.getMessage());
            result.setError(e.getMessage());
            if (conn != null) conn.close();
        } finally {
            if (conn != null) svc.releaseConnection(conn);
        }
    }

    @Override
    public void execute(String command) throws Exception {
        Iec104Service svc = getService();
        Iec104Connection conn = null;
        try {
            conn = (Iec104Connection) svc.getConnection();
            DataType dt = resolveDataType();
            Object value = (dt == DataType.BOOLEAN)
                    ? Boolean.parseBoolean(command)
                    : Float.parseFloat(command);
            conn.write(commonAddr, address, value);
        } catch (Exception e) {
            LOG.warn("IEC 104 execute failed for {}: {}", getName(), e.getMessage());
            if (conn != null) conn.close();
            throw e;
        } finally {
            if (conn != null) svc.releaseConnection(conn);
        }
    }

    protected DataType resolveDataType() {
        return getType() != null && getType().getDataType() != null
                ? getType().getDataType() : DataType.FLOAT;
    }

    private Iec104Service getService() {
        if (!(getSource() instanceof Iec104Service svc)) {
            throw new IllegalStateException("Iec104Control must belong to an Iec104Service");
        }
        return svc;
    }

    // ======================== getters / setters ========================

    public int getAddress()                   { return address; }
    public void setAddress(int address)       { this.address = address; }

    public int getCommonAddr()                      { return commonAddr; }
    public void setCommonAddr(int commonAddr)       { this.commonAddr = commonAddr; }
}
