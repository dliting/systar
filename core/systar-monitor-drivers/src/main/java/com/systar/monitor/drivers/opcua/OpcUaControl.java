package com.systar.monitor.drivers.opcua;

import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.drivers.opcua.OpcUaService.OpcUaConnection;
import com.systar.monitor.result.IMonitorResult;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OPC UA Control supporting read-back (detect) and write (execute).
 * <p>
 * Uses Eclipse Milo for OPC UA client communication.
 * Uses the ControlType's DataType to determine the correct value encoding:
 * BOOLEAN → Boolean, FLOAT → Float.
 * The nodeIdStr field is populated via reflective property binding from metadata.
 */
public class OpcUaControl extends Control {

    private static final Logger LOG = LoggerFactory.getLogger(OpcUaControl.class);

    protected String nodeIdStr;

    @Override
    public void detect(IMonitorResult result) throws Exception {
        OpcUaService svc = getService();
        OpcUaConnection conn = null;
        try {
            conn = (OpcUaConnection) svc.getConnection();
            NodeId nid = parseNodeId(nodeIdStr);
            DataValue dv = conn.readValue(nid);
            if (dv != null && dv.getStatusCode() != null
                    && dv.getStatusCode().isGood()
                    && dv.getValue() != null && dv.getValue().isNotNull()) {
                result.setValue(dv.getValue().getValue());
            } else {
                result.setError("OPC UA read returned null or bad status for " + nodeIdStr);
            }
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("OPC UA detect failed for {}: {}", getName(), e.getMessage());
            result.setError(e.getMessage());
            if (conn != null) conn.close();
        } finally {
            if (conn != null) svc.releaseConnection(conn);
        }
    }

    @Override
    public void execute(String command) throws Exception {
        OpcUaService svc = getService();
        OpcUaConnection conn = null;
        try {
            conn = (OpcUaConnection) svc.getConnection();
            NodeId nid = parseNodeId(nodeIdStr);
            Object value = toValue(command);
            conn.writeValue(nid, value);
        } catch (Exception e) {
            LOG.warn("OPC UA execute failed for {}: {}", getName(), e.getMessage());
            if (conn != null) conn.close();
            throw e;
        } finally {
            if (conn != null) svc.releaseConnection(conn);
        }
    }

    protected Object toValue(String command) {
        DataType dt = resolveDataType();
        if (dt == DataType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(command) && !"false".equalsIgnoreCase(command)) {
                throw new IllegalArgumentException(
                        "Boolean control expects 'true'/'false', got: " + command);
            }
            return Boolean.parseBoolean(command);
        }
        return Float.parseFloat(command);
    }

    protected DataType resolveDataType() {
        return getType() != null && getType().getDataType() != null
                ? getType().getDataType() : DataType.FLOAT;
    }

    private OpcUaService getService() {
        if (!(getSource() instanceof OpcUaService svc)) {
            throw new IllegalStateException("OpcUaControl must belong to an OpcUaService");
        }
        return svc;
    }

    /**
     * Parses a NodeId string. Supports formats:
     * <ul>
     *   <li>{@code ns=2;s=MyNode} — string identifier</li>
     *   <li>{@code ns=2;i=1001} — numeric identifier</li>
     *   <li>{@code i=2253} — numeric in namespace 0</li>
     * </ul>
     */
    static NodeId parseNodeId(String nodeIdStr) {
        if (nodeIdStr == null || nodeIdStr.isBlank()) {
            throw new IllegalArgumentException("NodeId must not be empty");
        }
        int ns = 0;
        String idPart = nodeIdStr;
        if (nodeIdStr.startsWith("ns=")) {
            int semi = nodeIdStr.indexOf(';');
            if (semi < 0) {
                throw new IllegalArgumentException("Invalid NodeId format: " + nodeIdStr);
            }
            ns = Integer.parseInt(nodeIdStr.substring(3, semi));
            idPart = nodeIdStr.substring(semi + 1);
        }
        if (idPart.startsWith("s=")) {
            return new NodeId(ns, idPart.substring(2));
        } else if (idPart.startsWith("i=")) {
            return new NodeId(ns, Integer.parseInt(idPart.substring(2)));
        }
        return new NodeId(ns, idPart);
    }

    // ======================== getters / setters ========================

    public String getNodeIdStr()                      { return nodeIdStr; }
    public void setNodeIdStr(String nodeIdStr)        { this.nodeIdStr = nodeIdStr; }
}
