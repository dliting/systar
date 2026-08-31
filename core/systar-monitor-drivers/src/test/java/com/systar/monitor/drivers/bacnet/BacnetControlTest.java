package com.systar.monitor.drivers.bacnet;

import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.drivers.bacnet.BacnetService.BacnetConnection;
import com.systar.monitor.result.IMonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.systar.monitor.asset.MonitorConnection;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class BacnetControlTest {

    private BacnetService service;
    private BacnetConnection conn;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(BacnetService.class);
        conn = mock(BacnetConnection.class);
        when(service.getConnection()).thenReturn(conn);
    }

    private BacnetControl createControl() {
        BacnetControl ctrl = new BacnetControl();
        ctrl.init(new ControlType("BACnetBoolControl"), 1, "testCtrl");
        ctrl.setSource(service);
        ctrl.setObjectType(3);       // binaryInput
        ctrl.setInstanceNumber(1);
        ctrl.setPropertyIdentifier(85); // presentValue
        return ctrl;
    }

    @Test
    @DisplayName("detect reads value from BACnet connection")
    void detectReadsValue() throws Exception {
        when(conn.read(anyInt(), anyInt(), anyInt())).thenReturn(1);

        BacnetControl ctrl = createControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setValue(1);
        verify(result).setSampleTime(anyLong());
    }

    @Test
    @DisplayName("detect sets error when read returns null")
    void detectErrorOnNull() throws Exception {
        when(conn.read(anyInt(), anyInt(), anyInt())).thenReturn(null);

        BacnetControl ctrl = createControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setError(anyString());
    }

    @Test
    @DisplayName("execute writes value to BACnet connection")
    void executeWritesValue() throws Exception {
        BacnetControl ctrl = createControl();
        ctrl.execute("42.5");

        verify(conn).write(eq(3), eq(1), eq(85), any());
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("execute releases connection on error")
    void executeReleasesOnError() throws Exception {
        doThrow(new RuntimeException("write failed"))
                .when(conn).write(anyInt(), anyInt(), anyInt(), any());

        BacnetControl ctrl = createControl();
        assertThatThrownBy(() -> ctrl.execute("true"))
                .isInstanceOf(Exception.class);
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("detect releases connection on error")
    void detectReleasesOnError() throws Exception {
        when(conn.read(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("read failed"));

        BacnetControl ctrl = createControl();
        IMonitorResult result = mock(IMonitorResult.class);
        ctrl.detect(result);

        verify(result).setError(contains("read failed"));
        verify(service).releaseConnection(conn);
    }

    @Test
    @DisplayName("throws when source is not BacnetService")
    void wrongSourceThrows() {
        BacnetControl ctrl = new BacnetControl();
        ctrl.init(new ControlType("Test"), 1, "test");
        ctrl.setSource(mock(com.systar.monitor.asset.MonitorService.class));

        IMonitorResult result = mock(IMonitorResult.class);
        assertThatThrownBy(() -> ctrl.detect(result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BacnetControl must belong to a BacnetService");
    }
}
