package com.systar.monitor.drivers.opcua;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active service for OPC UA client communication using Eclipse Milo.
 * <p>
 * Manages connections to OPC UA servers for reading node values.
 */
public class OpcUaService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(OpcUaService.class);

    private String endpointUrl;
    private String securityPolicy = "None";
    private String username;
    private String password;

    public OpcUaService() {
    }

    @Override
    public void start() throws Exception {
        LOG.info("OpcUaService started for {}", endpointUrl);
    }

    @Override
    public void stop() {
        LOG.info("OpcUaService stopped");
    }

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new OpcUaConnection(this);
    }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getSecurityPolicy() { return securityPolicy; }
    public void setSecurityPolicy(String securityPolicy) { this.securityPolicy = securityPolicy; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // ======================== inner connection class ========================

    public static class OpcUaConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(OpcUaConnection.class);

        private final OpcUaService service;
        private OpcUaClient client;

        public OpcUaConnection(OpcUaService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            if (service.getUsername() != null && !service.getUsername().isBlank()) {
                client = OpcUaClient.create(service.getEndpointUrl(),
                        endpoints -> endpoints.stream().findFirst(),
                        configBuilder -> configBuilder
                                .setIdentityProvider(new UsernameProvider(
                                        service.getUsername(), service.getPassword()))
                                .build());
            } else {
                client = OpcUaClient.create(service.getEndpointUrl());
            }

            client.connect().get();
            CONN_LOG.debug("OPC UA client connected to {}", service.getEndpointUrl());
        }

        @Override
        public boolean isConnected() {
            return client != null;
        }

        @Override
        public void close() {
            if (client != null) {
                try {
                    client.disconnect().get();
                } catch (Exception e) {
                    CONN_LOG.warn("Error disconnecting OPC UA client", e);
                }
                client = null;
            }
        }

        /**
         * Reads a value from the given OPC UA NodeId.
         *
         * @param nodeId the node to read
         * @return the DataValue from the server
         */
        public DataValue readValue(NodeId nodeId) throws Exception {
            if (client == null) {
                throw new IllegalStateException("OPC UA connection is not open");
            }
            return client.readValue(0, TimestampsToReturn.Both, nodeId).get();
        }

        /**
         * Writes a value to the given OPC UA NodeId.
         *
         * @param nodeId the node to write
         * @param value  the value to write (wrapped in Variant/DataValue)
         */
        public void writeValue(NodeId nodeId, Object value) throws Exception {
            if (client == null) {
                throw new IllegalStateException("OPC UA connection is not open");
            }
            DataValue dv = new DataValue(new Variant(value));
            WriteValue wv = new WriteValue(
                    nodeId, AttributeId.Value.uid(), null, dv);
            client.write(java.util.List.of(wv)).get();
        }
    }
}
