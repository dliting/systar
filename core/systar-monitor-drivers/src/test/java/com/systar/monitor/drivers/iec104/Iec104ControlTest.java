package com.systar.monitor.drivers.iec104;

import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.drivers.iec104.Iec104Service.Iec104Connection;
import com.systar.monitor.result.IMonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class Iec104ControlTest {

    private Iec104Service service;
    private Iec104Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(Iec104Service.class);
        conn    = mock(Iec104Connection.class);
        when(service.getConnection()).thenReturn(conn);
    }

    private Iec104Control createBoolControl() {
        Iec104Control ctrl = new Iec104Control();
        ControlType type = new ControlType("IEC104BoolControl");
        type.setDataType(DataType.BOOLEAN);
        ctrl.init(type, 1, "testCtrl");
        ctrl.setSource(service);
        ctrl.setAddress(1001);
        ctrl.setCommonAddr(1);
        return ctrl;
    }

    private Iec104Control createFloatControl() {
        Iec104Control ctrl = new Iec104Control();
        ControlType type = new ControlType("IEC104FloatControl");
        type.setDataType(DataType.FLOAT);
        ctrl.init(type, 2, "testFloatCtrl");
        ctrl.setSource(service);
        ctrl.setAddress(2001);
        ctrl.setCommonAddr(2);
        return ctrl;
    }

    @Test
    @DisplayName("detect reads cached value from IEC 104 connection")
    void detectReadsValue() throws Exception {
        when(conn.read(1001)).thenReturn(true);

        Iec104Control ctrl = createBoolControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setValue(true);
        verify(result).setSampleTime(anyLong());
    }

    @Test
    @DisplayName("detect sets error when read returns null")
    void detectErrorOnNull() throws Exception {
        when(conn.read(1001)).thenReturn(null);

        Iec104Control ctrl = createBoolControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setError(contains("null"));
    }

    @Test
    @DisplayName("execute writes boolean value via single command")
    void executeWritesBoolean() throws Exception {
        Iec104Control ctrl = createBoolControl();
        ctrl.execute("true");

        verify(conn).write(eq(1), eq(1001), eq(true));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("execute writes float value via set-point command")
    void executeWritesFloat() throws Exception {
        Iec104Control ctrl = createFloatControl();
        ctrl.execute("42.5");

        verify(conn).write(eq(2), eq(2001), eq(42.5f));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("execute releases connection on error")
    void executeReleasesOnError() throws Exception {
        doThrow(new RuntimeException("write failed"))
                .when(conn).write(anyInt(), anyInt(), any());

        Iec104Control ctrl = createBoolControl();
        assertThatThrownBy(() -> ctrl.execute("true"))
                .isInstanceOf(Exception.class);
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("detect releases connection on error")
    void detectReleasesOnError() throws Exception {
        when(conn.read(anyInt()))
                .thenThrow(new RuntimeException("read failed"));

        Iec104Control ctrl = createBoolControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setError(contains("read failed"));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("throws when source is not Iec104Service")
    void wrongSourceThrows() {
        Iec104Control ctrl = new Iec104Control();
        ctrl.init(new ControlType("Test"), 1, "test");
        ctrl.setSource(mock(com.systar.monitor.asset.MonitorService.class));

        IMonitorResult result = mock(IMonitorResult.class);
        assertThatThrownBy(() -> ctrl.detect(result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Iec104Control must belong to an Iec104Service");
    }
}
