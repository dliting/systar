package com.systar.monitor.drivers.opcua;

import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.drivers.opcua.OpcUaService.OpcUaConnection;
import com.systar.monitor.result.IMonitorResult;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class OpcUaControlTest {

    private OpcUaService service;
    private OpcUaConnection conn;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(OpcUaService.class);
        conn    = mock(OpcUaConnection.class);
        when(service.getConnection()).thenReturn(conn);
    }

    private OpcUaControl createControl() {
        return createControl(DataType.BOOLEAN);
    }

    private OpcUaControl createControl(DataType dt) {
        OpcUaControl ctrl = new OpcUaControl();
        ControlType type = new ControlType("OpcUaControl");
        type.setDataType(dt);
        ctrl.init(type, 1, "testCtrl");
        ctrl.setSource(service);
        ctrl.setNodeIdStr("ns=2;s=MyNode");
        return ctrl;
    }

    @Test
    @DisplayName("detect reads value from OPC UA connection")
    void detectReadsValue() throws Exception {
        DataValue dv = new DataValue(new Variant(true));
        when(conn.readValue(any(NodeId.class))).thenReturn(dv);

        OpcUaControl ctrl = createControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setValue(true);
        verify(result).setSampleTime(anyLong());
    }

    @Test
    @DisplayName("detect sets error when DataValue is null")
    void detectErrorOnNull() throws Exception {
        when(conn.readValue(any(NodeId.class))).thenReturn(null);

        OpcUaControl ctrl = createControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setError(contains("null"));
    }

    @Test
    @DisplayName("execute writes float value to OPC UA connection")
    void executeWritesFloat() throws Exception {
        OpcUaControl ctrl = createControl(DataType.FLOAT);
        ctrl.execute("42.5");

        verify(conn).writeValue(any(NodeId.class), eq(42.5f));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("execute writes boolean value to OPC UA connection")
    void executeWritesBoolean() throws Exception {
        OpcUaControl ctrl = createControl();
        ctrl.execute("true");

        verify(conn).writeValue(any(NodeId.class), eq(true));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("execute releases connection on error")
    void executeReleasesOnError() throws Exception {
        doThrow(new RuntimeException("write failed"))
                .when(conn).writeValue(any(NodeId.class), any());

        OpcUaControl ctrl = createControl();
        assertThatThrownBy(() -> ctrl.execute("true"))
                .isInstanceOf(Exception.class);
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("detect releases connection on error")
    void detectReleasesOnError() throws Exception {
        when(conn.readValue(any(NodeId.class)))
                .thenThrow(new RuntimeException("read failed"));

        OpcUaControl ctrl = createControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setError(contains("read failed"));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("throws when source is not OpcUaService")
    void wrongSourceThrows() {
        OpcUaControl ctrl = new OpcUaControl();
        ControlType type = new ControlType("Test");
        type.setDataType(DataType.BOOLEAN);
        ctrl.init(type, 1, "test");
        ctrl.setSource(mock(com.systar.monitor.asset.MonitorService.class));
        ctrl.setNodeIdStr("ns=2;s=Test");

        IMonitorResult result = mock(IMonitorResult.class);
        assertThatThrownBy(() -> ctrl.detect(result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpcUaControl must belong to an OpcUaService");
    }

    @Test
    @DisplayName("parseNodeId handles string identifier with namespace")
    void parseNodeIdString() {
        NodeId nid = OpcUaControl.parseNodeId("ns=2;s=MyNode");
        assertThat(nid.getNamespaceIndex().intValue()).isEqualTo(2);
        assertThat(nid.getIdentifier()).isEqualTo("MyNode");
    }

    @Test
    @DisplayName("parseNodeId handles numeric identifier with namespace")
    void parseNodeIdNumeric() {
        NodeId nid = OpcUaControl.parseNodeId("ns=2;i=1001");
        assertThat(nid.getNamespaceIndex().intValue()).isEqualTo(2);
        assertThat(nid.getIdentifier()).isEqualTo(org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(1001));
    }

    @Test
    @DisplayName("parseNodeId rejects empty string")
    void parseNodeIdEmpty() {
        assertThatThrownBy(() -> OpcUaControl.parseNodeId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
