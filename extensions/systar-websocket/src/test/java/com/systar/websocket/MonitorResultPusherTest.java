package com.systar.websocket;

import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorResultPusherTest {

    private MonitorWebSocketHandler handler;
    private MonitorResultPusher pusher;

    @BeforeEach
    void setUp() {
        handler = mock(MonitorWebSocketHandler.class);
        pusher = new MonitorResultPusher(handler);
    }

    @Test
    @DisplayName("onMonitorResult delegates to handler broadcastMonitorResult")
    void onMonitorResultDelegates() {
        Probe probe = new Probe();
        probe.init(new ProbeType("test"), 1, "test");
        MonitorResult result = new MonitorResult(probe, 42.0);
        MonitorResultEvent event = new MonitorResultEvent(this, result);

        pusher.onMonitorResult(event);

        verify(handler).broadcastMonitorResult(result);
    }

    @Test
    @DisplayName("onMonitorResult handles exception from handler gracefully")
    void onMonitorResultHandlesException() {
        Probe probe = new Probe();
        probe.init(new ProbeType("test"), 1, "test");
        MonitorResult result = new MonitorResult(probe, 42.0);
        MonitorResultEvent event = new MonitorResultEvent(this, result);

        doThrow(new RuntimeException("broadcast failed")).when(handler).broadcastMonitorResult(any());

        // Should not throw
        pusher.onMonitorResult(event);

        verify(handler).broadcastMonitorResult(result);
    }
}
