package com.systar.simulator.app.protocol;

import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.DataPointAddress;
import com.systar.simulator.model.OpcUaAddress;
import com.systar.simulator.model.OpcUaEndpoint;
import com.systar.simulator.model.SimulatedDevice;
import com.systar.simulator.protocol.ProtocolServer;
import com.systar.simulator.protocol.ServerStatus;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OpcUaProtocolServer implements ProtocolServer {

    private static final Logger LOG = LoggerFactory.getLogger(OpcUaProtocolServer.class);

    private static final String DEFAULT_APPLICATION_URI  = "urn:systar:simulator";
    private static final String DEFAULT_PRODUCT_URI      = "urn:systar:simulator:opcua-server";
    private static final int    CERTIFICATE_KEY_SIZE     = 2048;
    private static final String SIMULATOR_NAMESPACE_URI  = "urn:systar:simulator:namespace";
    private static final int    CERTIFICATE_VALIDITY_DAYS = 365;

    private final Map<Integer, OpcUaServer> servers          = new ConcurrentHashMap<>();
    private final Map<Integer, SimulatorNamespace> namespaces = new ConcurrentHashMap<>();
    private final Map<String, Integer> devicePorts           = new ConcurrentHashMap<>();
    private final Map<String, UaFolderNode> deviceFolders    = new ConcurrentHashMap<>();
    private final Map<String, List<UaVariableNode>> deviceNodes = new ConcurrentHashMap<>();
    private final Map<String, UaVariableNode> nodeIndex      = new ConcurrentHashMap<>();

    @Override
    public void start(SimulatedDevice device) throws Exception {
        OpcUaEndpoint endpoint = (OpcUaEndpoint) device.getEndpoint();
        int    port     = endpoint.getPort();
        String deviceId = device.getId();

        if (deviceFolders.containsKey(deviceId)) {
            throw new IllegalStateException("Device '" + deviceId + "' is already registered");
        }

        OpcUaServer server = servers.computeIfAbsent(port, p -> {
            try {
                return createServer(p, endpoint.getServerName());
            } catch (Exception e) {
                throw new RuntimeException("Failed to create OPC UA server on port " + p, e);
            }
        });

        SimulatorNamespace ns = namespaces.computeIfAbsent(port, p -> {
            SimulatorNamespace sns = new SimulatorNamespace(server, SIMULATOR_NAMESPACE_URI);
            sns.registerSelf(server);
            return sns;
        });

        int nsIndex = ns.getNamespaceIndex().intValue();

        UaFolderNode folderNode = new UaFolderNode(
                ns.context(),
                new NodeId(nsIndex, deviceId),
                new QualifiedName(nsIndex, device.getName()),
                LocalizedText.english(device.getName())
        );
        ns.addNode(folderNode);
        ns.addReference(new Reference(
                Identifiers.ObjectsFolder,
                Identifiers.Organizes,
                folderNode.getNodeId().expanded(),
                false
        ));

        List<UaVariableNode> variableNodes = new ArrayList<>();
        for (DataPoint dp : device.getDataPoints()) {
            if (!(dp.getAddress() instanceof OpcUaAddress opcuaAddr)) {
                LOG.warn("Skipping data point '{}' with non-OpcUaAddress", dp.getName());
                continue;
            }

            NodeId nodeId = toNodeId(opcuaAddr, nsIndex);
            UaVariableNode variableNode = new UaVariableNode.UaVariableNodeBuilder(ns.context())
                    .setNodeId(nodeId)
                    .setBrowseName(new QualifiedName(nsIndex, dp.getName()))
                    .setDisplayName(LocalizedText.english(dp.getName()))
                    .setDataType(resolveDataType(dp.getCurrentValue()))
                    .setTypeDefinition(Identifiers.BaseDataVariableType)
                    .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                    .build();

            Object value = (dp.getCurrentValue() != null) ? dp.getCurrentValue() : 0.0;
            variableNode.setValue(new DataValue(new Variant(value)));

            ns.addNode(variableNode);
            folderNode.addOrganizes(variableNode);

            variableNodes.add(variableNode);
            nodeIndex.put(nodeId.toParseableString(), variableNode);
        }

        deviceFolders.put(deviceId, folderNode);
        deviceNodes.put(deviceId, variableNodes);
        devicePorts.put(deviceId, port);

        LOG.info("OPC UA server started for device '{}' on port {}, {} variable nodes",
                deviceId, port, variableNodes.size());
    }

    @Override
    public void stop(String deviceId) {
        List<UaVariableNode> nodes = deviceNodes.remove(deviceId);
        UaFolderNode folder    = deviceFolders.remove(deviceId);
        Integer port            = devicePorts.remove(deviceId);

        if (nodes == null) {
            LOG.warn("Device '{}' not registered, ignoring stop", deviceId);
            return;
        }

        for (UaVariableNode node : nodes) {
            nodeIndex.remove(node.getNodeId().toParseableString());
        }

        if (port != null && !hasDevicesOnPort(port)) {
            shutdownServer(port);
        }

        LOG.info("OPC UA server stopped for device '{}'", deviceId);
    }

    @Override
    public void updateValue(String deviceId, DataPointAddress address, Object value) {
        if (!(address instanceof OpcUaAddress opcuaAddr)) {
            return;
        }

        Integer port = devicePorts.get(deviceId);
        if (port == null) return;
        SimulatorNamespace ns = namespaces.get(port);
        if (ns == null) return;
        int nsIndex = ns.getNamespaceIndex().intValue();

        NodeId nodeId   = toNodeId(opcuaAddr, nsIndex);
        String nodeKey  = nodeId.toParseableString();
        UaVariableNode node = nodeIndex.get(nodeKey);

        if (node == null) {
            LOG.warn("updateValue: node {} not found for device '{}'", nodeKey, deviceId);
            return;
        }

        node.setValue(new DataValue(new Variant(value)));
    }

    @Override
    public ServerStatus getStatus() {
        boolean anyRunning = !servers.isEmpty();
        int deviceCount   = deviceFolders.size();
        return new ServerStatus(anyRunning, deviceCount, 0);
    }

    @Override
    public void close() {
        for (Integer port : Set.copyOf(servers.keySet())) {
            shutdownServer(port);
        }
        servers.clear();
        namespaces.clear();
        deviceFolders.clear();
        deviceNodes.clear();
        devicePorts.clear();
        nodeIndex.clear();
    }

    // ======================== Milo server lifecycle ========================

    private OpcUaServer createServer(int port, String serverName) throws Exception {
        KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(CERTIFICATE_KEY_SIZE);
        SelfSignedCertificateBuilder certBuilder = new SelfSignedCertificateBuilder(keyPair);
        certBuilder.setCommonName(serverName);
        certBuilder.setOrganization("Systar");
        certBuilder.setApplicationUri(DEFAULT_APPLICATION_URI);
        certBuilder.addDnsName("localhost");
        certBuilder.addIpAddress("127.0.0.1");
        certBuilder.setValidityPeriod(Period.ofDays(CERTIFICATE_VALIDITY_DAYS));

        X509Certificate cert = certBuilder.build();
        DefaultCertificateManager certManager = new DefaultCertificateManager(keyPair, cert);

        EndpointConfiguration endpointConfig = EndpointConfiguration.newBuilder()
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .setBindAddress("0.0.0.0")
                .setBindPort(port)
                .setHostname("localhost")
                .setPath("/" + serverName)
                .setSecurityPolicy(SecurityPolicy.None)
                .setSecurityMode(MessageSecurityMode.None)
                .setCertificate(cert)
                .build();

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
                .setApplicationName(LocalizedText.english(serverName))
                .setApplicationUri(DEFAULT_APPLICATION_URI)
                .setProductUri(DEFAULT_PRODUCT_URI)
                .setCertificateManager(certManager)
                .setIdentityValidator(new AnonymousIdentityValidator())
                .setEndpoints(Set.of(endpointConfig))
                .build();

        OpcUaServer server = new OpcUaServer(serverConfig);
        server.startup().get();

        LOG.info("OPC UA Milo server started on port {}", port);
        return server;
    }

    private void shutdownServer(int port) {
        OpcUaServer server = servers.remove(port);
        namespaces.remove(port);
        if (server == null) return;
        try {
            server.shutdown().get();
            LOG.info("OPC UA Milo server shutdown on port {}", port);
        } catch (Exception e) {
            LOG.warn("Error shutting down OPC UA Milo server on port {}", port, e);
        }
    }

    // ======================== Helpers ========================

    private static NodeId toNodeId(OpcUaAddress addr, int nsIndex) {
        if (addr.isIntegerId()) {
            return new NodeId(nsIndex, Integer.parseInt(addr.getIdentifier()));
        }
        return new NodeId(nsIndex, addr.getIdentifier());
    }

    private static NodeId resolveDataType(Object value) {
        if (value instanceof Boolean) return Identifiers.Boolean;
        if (value instanceof Float)   return Identifiers.Float;
        if (value instanceof Double)  return Identifiers.Double;
        if (value instanceof Integer) return Identifiers.Int32;
        return Identifiers.Double;
    }

    private boolean hasDevicesOnPort(int port) {
        return devicePorts.containsValue(port);
    }

    // ======================== Namespace wrapper ========================

    private static class SimulatorNamespace extends ManagedNamespaceWithLifecycle {

        SimulatorNamespace(OpcUaServer server, String uri) {
            super(server, uri);
        }

        void registerSelf(OpcUaServer server) {
            server.getAddressSpaceManager().register(getNodeManager());
            startup();
        }

        UaNodeContext context()  { return getNodeContext(); }
        void addNode(UaFolderNode node)         { getNodeManager().addNode(node); }
        void addNode(UaVariableNode node)        { getNodeManager().addNode(node); }
        void addReference(Reference ref)          { getNodeManager().addReference(ref); }

        @Override
        public void onDataItemsCreated(List<org.eclipse.milo.opcua.sdk.server.api.DataItem> items) {}

        @Override
        public void onDataItemsModified(List<org.eclipse.milo.opcua.sdk.server.api.DataItem> items) {}

        @Override
        public void onDataItemsDeleted(List<org.eclipse.milo.opcua.sdk.server.api.DataItem> items) {}

        @Override
        public void onMonitoringModeChanged(List<org.eclipse.milo.opcua.sdk.server.api.MonitoredItem> items) {}
    }
}
