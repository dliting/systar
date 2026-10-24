package com.systar.monitor.drivers.opcua;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.opcua.OpcUaService.OpcUaConnection;
import com.systar.monitor.result.IMonitorResult;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OPC UA probe that reads data from an OPC UA server node.
 * <p>
 * Source format: {@code ns=<namespace>;s=<identifier>} or {@code ns=<namespace>;i=<nodeId>}
 */
public class OpcUaProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(OpcUaProbe.class);

    private int namespaceIndex;
    private String identifier;
    private boolean integerId;

    public OpcUaProbe() {
    }

    @Override
    public void init(ProbeType type, int id, String name) {
        super.init(type, id, name);
        // type.getSource() is a service reference name (e.g. "OpcUaService"), not a
        // NodeId. NodeId resolution happens at detect time from metadata "NodeId".
    }

    // ======================== metadata NodeId ========================

    /**
     * Resolves NodeId from instance-level metadata key "NodeId", falling back
     * to the type-level source if no metadata is present. Called after all
     * attributes are loaded and properties are bound.
     */
    public void parseMetadataNodeId() {
        Object metaNodeId = getMetadata("NodeId");
        if (metaNodeId != null && !metaNodeId.toString().isBlank()) {
            parseSource(metaNodeId.toString());
            return;
        }
        ProbeType type = getType();
        String typeSource = type != null ? type.getSource() : null;
        if (typeSource != null && !typeSource.isBlank()) {
            parseSource(typeSource);
        }
    }

    // ======================== source parsing ========================

    private void parseSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("OPC UA source must not be empty");
        }
        String[] parts = source.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0].trim().toLowerCase()) {
                case "ns" -> this.namespaceIndex = Integer.parseInt(kv[1].trim());
                case "s"  -> { this.identifier = kv[1].trim(); this.integerId = false; }
                case "i"  -> { this.identifier = kv[1].trim(); this.integerId = true; }
            }
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(
                    "OPC UA source must contain s=<stringId> or i=<intId>: " + source);
        }
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (identifier == null) {
            parseMetadataNodeId();
        }
        if (identifier == null) {
            result.setError("OPC UA NodeId not configured for probe " + getName());
            return;
        }

        MonitorService svc = getSource();
        if (!(svc instanceof OpcUaService opcUaService)) {
            throw new IllegalStateException("OpcUaProbe must belong to an OpcUaService");
        }

        OpcUaConnection conn = null;
        try {
            conn = (OpcUaConnection) opcUaService.getConnection();
            NodeId nodeId = integerId
                    ? new NodeId(namespaceIndex, Integer.parseInt(identifier))
                    : new NodeId(namespaceIndex, identifier);

            DataValue dataValue = conn.readValue(nodeId);
            if (dataValue != null && dataValue.getValue() != null
                    && dataValue.getStatusCode().isGood()) {
                result.setValue(dataValue.getValue().getValue());
            } else {
                result.setError("Invalid OPC UA value for ns="
                        + namespaceIndex + " id=" + identifier);
            }
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("OPC UA read failed for ns={} id={} at {}",
                    namespaceIndex, identifier, opcUaService.getEndpointUrl(), e);
            result.setError("OPC UA read failed: " + e.getMessage());
            if (conn != null) { conn.close(); }
        } finally {
            if (conn != null) opcUaService.releaseConnection(conn);
        }
    }

    // ======================== getters ========================

    public int getNamespaceIndex() { return namespaceIndex; }
    public String getIdentifier() { return identifier; }
    public boolean isIntegerId() { return integerId; }
}
