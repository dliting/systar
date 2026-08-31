package com.systar.simulator.app.protocol;

import com.systar.monitor.drivers.opcua.OpcUaService;
import com.systar.simulator.generator.FixedGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.OpcUaAddress;
import com.systar.simulator.model.OpcUaEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test that uses Systar's actual {@link OpcUaService}
 * client to verify the simulator's OPC UA server works correctly.
 * <p>
 * The OPC UA namespace index is dynamically assigned by the Milo server,
 * so this test discovers it by browsing the server's namespace table after
 * the server starts.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class OpcUaRoundTripTest {

    private static final String SIMULATOR_NAMESPACE_URI = "urn:systar:simulator:namespace";
    private static final String NODE_ID_OUTDOOR_TEMP   = "OutdoorTemperature";
    private static final String NODE_ID_PRESSURE        = "Pressure";
    private static final String NODE_ID_FAN_STATUS       = "FanStatus";

    private OpcUaProtocolServer server;
    private OpcUaService service;
    private OpcUaService.OpcUaConnection conn;
    private int port;
    private int nsIndex;

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        SimulatedDevice device = new SimulatedDevice();
        device.setId("test-opcua");
        device.setName("Test OPC UA Device");
        device.setProtocol(ProtocolType.OPC_UA);

        OpcUaEndpoint endpoint = new OpcUaEndpoint();
        endpoint.setPort(port);
        endpoint.setServerName("test-simulator");
        device.setEndpoint(endpoint);

        DataPoint tempPoint = new DataPoint();
        tempPoint.setId("outdoor-temp");
        tempPoint.setName(NODE_ID_OUTDOOR_TEMP);
        tempPoint.setAddress(new OpcUaAddress(2, NODE_ID_OUTDOOR_TEMP, false));
        tempPoint.setGenerator(new FixedGenerator(25.0));
        tempPoint.setCurrentValue(25.0);
        device.getDataPoints().add(tempPoint);

        DataPoint pressurePoint = new DataPoint();
        pressurePoint.setId("pressure");
        pressurePoint.setName(NODE_ID_PRESSURE);
        pressurePoint.setAddress(new OpcUaAddress(2, NODE_ID_PRESSURE, false));
        pressurePoint.setGenerator(new FixedGenerator(1013));
        pressurePoint.setCurrentValue(1013);
        device.getDataPoints().add(pressurePoint);

        DataPoint fanPoint = new DataPoint();
        fanPoint.setId("fan-status");
        fanPoint.setName(NODE_ID_FAN_STATUS);
        fanPoint.setAddress(new OpcUaAddress(2, NODE_ID_FAN_STATUS, false));
        fanPoint.setGenerator(new FixedGenerator(true));
        fanPoint.setCurrentValue(true);
        device.getDataPoints().add(fanPoint);

        server = new OpcUaProtocolServer();
        server.start(device);
        server.updateValue("test-opcua", tempPoint.getAddress(), 25.0);
        server.updateValue("test-opcua", pressurePoint.getAddress(), 1013);
        server.updateValue("test-opcua", fanPoint.getAddress(), true);

        // Create the OpcUaService client and connect
        service = new OpcUaService();
        service.setEndpointUrl("opc.tcp://localhost:" + port + "/test-simulator");
        service.setSecurityPolicy("None");
        service.start();

        conn = (OpcUaService.OpcUaConnection) service.createConnection();
        conn.open();

        // Discover the namespace index assigned by the server
        nsIndex = discoverNamespaceIndex(conn);
        assertThat(nsIndex).isGreaterThan(0);
    }

    @Test
    @DisplayName("read OPC UA node returns correct double value")
    void readNodeDoubleValue() throws Exception {
        NodeId nodeId = new NodeId(nsIndex, NODE_ID_OUTDOOR_TEMP);
        DataValue dv = conn.readValue(nodeId);
        assertThat(dv.getStatusCode().isGood()).isTrue();
        assertThat(dv.getValue()).isNotNull();
        assertThat(dv.getValue().getValue()).isInstanceOf(Number.class);
        assertThat(((Number) dv.getValue().getValue()).doubleValue())
                .isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("read OPC UA node returns correct integer value")
    void readNodeIntValue() throws Exception {
        NodeId nodeId = new NodeId(nsIndex, NODE_ID_PRESSURE);
        DataValue dv = conn.readValue(nodeId);
        assertThat(dv.getStatusCode().isGood()).isTrue();
        assertThat(dv.getValue()).isNotNull();
        assertThat(dv.getValue().getValue()).isInstanceOf(Number.class);
        assertThat(((Number) dv.getValue().getValue()).intValue()).isEqualTo(1013);
    }

    @Test
    @DisplayName("read OPC UA node returns correct boolean value")
    void readNodeBoolValue() throws Exception {
        NodeId nodeId = new NodeId(nsIndex, NODE_ID_FAN_STATUS);
        DataValue dv = conn.readValue(nodeId);
        assertThat(dv.getStatusCode().isGood()).isTrue();
        assertThat(dv.getValue()).isNotNull();
        assertThat(dv.getValue().getValue()).isInstanceOf(Boolean.class);
        assertThat(dv.getValue().getValue()).isEqualTo(true);
    }

    @Test
    @DisplayName("updateValue on simulator is reflected in client read")
    void updateValueReflectsInClient() throws Exception {
        // Update the temperature to a new value
        server.updateValue("test-opcua",
                new OpcUaAddress(2, NODE_ID_OUTDOOR_TEMP, false), 30.5);

        // Read from client and verify
        NodeId nodeId = new NodeId(nsIndex, NODE_ID_OUTDOOR_TEMP);
        DataValue dv = conn.readValue(nodeId);
        assertThat(dv.getStatusCode().isGood()).isTrue();
        assertThat(((Number) dv.getValue().getValue()).doubleValue())
                .isCloseTo(30.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("read non-existent node returns bad status")
    void readNonExistentNode() throws Exception {
        NodeId nodeId = new NodeId(nsIndex, "NonExistentNode");
        DataValue dv = conn.readValue(nodeId);
        // Non-existent nodes should return a bad status code
        assertThat(dv.getStatusCode().isGood()).isFalse();
    }

    @AfterEach
    void tearDown() {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
        if (service != null) {
            try { service.stop(); } catch (Exception ignored) {}
        }
        if (server != null) {
            server.close();
        }
    }

    // ======================== Helpers ========================

    /**
     * Discovers the namespace index for the simulator namespace URI
     * by reading the server's namespace table.
     */
    private int discoverNamespaceIndex(OpcUaService.OpcUaConnection conn) throws Exception {
        // Access the underlying Milo OpcUaClient to read the namespace table.
        java.lang.reflect.Field clientField = OpcUaService.OpcUaConnection.class
                .getDeclaredField("client");
        clientField.setAccessible(true);
        OpcUaClient client = (OpcUaClient) clientField.get(conn);

        // readNamespaceTable() queries the server for the authoritative namespace table.
        NamespaceTable nsTable = client.readNamespaceTable();
        return nsTable.getIndex(SIMULATOR_NAMESPACE_URI).intValue();
    }
}
