package com.systar.monitor.schedule;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DetectTaskTest {

    private Probe monitor;
    private ResultDispatcher dispatcher;
    private ProbeType type;

    @BeforeEach
    void setUp() {
        type = new ProbeType("pt");
        type.setDetectInterval(TimeSpan.ofMinutes(5));
        monitor = new Probe();
        monitor.init(type, 1, "probe1");
        dispatcher = mock(ResultDispatcher.class);
    }

    @Test
    @DisplayName("Constructor rejects null monitor")
    void nullMonitor() {
        assertThatThrownBy(() -> new DetectTask(null, dispatcher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monitor");
    }

    @Test
    @DisplayName("Constructor rejects null dispatcher")
    void nullDispatcher() {
        assertThatThrownBy(() -> new DetectTask(monitor, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultDispatcher");
    }

    @Test
    @DisplayName("run dispatches result after detect")
    void runDispatches() {
        DetectTask task = new DetectTask(monitor, dispatcher);
        task.run();

        verify(dispatcher).dispatch(any(MonitorResult.class));
        assertThat(task.getLastResult()).isNotNull();
    }

    @Test
    @DisplayName("run creates error result on detect exception")
    void runHandlesException() {
        Probe failingProbe = new Probe() {
            @Override
            public void detect(com.systar.monitor.result.IMonitorResult result) throws Exception {
                throw new RuntimeException("sensor broken");
            }
        };
        failingProbe.init(type, 1, "failing");

        DetectTask task = new DetectTask(failingProbe, dispatcher);
        task.run();

        verify(dispatcher).dispatch(any(MonitorResult.class));
        MonitorResult last = task.getLastResult();
        assertThat(last).isNotNull();
        assertThat(last.hasError()).isTrue();
        assertThat(last.getError()).isEqualTo("sensor broken");
    }

    @Test
    @DisplayName("run handles exception with null message")
    void runHandlesExceptionNullMessage() {
        Probe failingProbe = new Probe() {
            @Override
            public void detect(com.systar.monitor.result.IMonitorResult result) throws Exception {
                throw new RuntimeException();
            }
        };
        failingProbe.init(type, 1, "failing");

        DetectTask task = new DetectTask(failingProbe, dispatcher);
        task.run();

        MonitorResult last = task.getLastResult();
        assertThat(last.hasError()).isTrue();
        assertThat(last.getError()).isEqualTo("java.lang.RuntimeException");
    }

    @Test
    @DisplayName("getMonitor returns the monitor")
    void getMonitor() {
        DetectTask task = new DetectTask(monitor, dispatcher);
        assertThat(task.getMonitor()).isSameAs(monitor);
    }

    @Test
    @DisplayName("getNextDelayMs returns interval for non-ERROR state")
    void nextDelayNormal() {
        DetectTask task = new DetectTask(monitor, dispatcher);
        long expectedMs = TimeSpan.ofMinutes(5).toMillis();
        assertThat(task.getNextDelayMs()).isEqualTo(expectedMs);
    }

    @Test
    @DisplayName("getNextDelayMs returns max of RETRY_INTERVAL_MS and interval for ERROR state")
    void nextDelayError() {
        monitor.setState(AssetState.ERROR);
        DetectTask task = new DetectTask(monitor, dispatcher);

        long expected = Math.max(DetectTask.RETRY_INTERVAL_MS, TimeSpan.ofMinutes(5).toMillis());
        assertThat(task.getNextDelayMs()).isEqualTo(expected);
    }

    @Test
    @DisplayName("getNextDelayMs returns RETRY_INTERVAL_MS when interval is smaller")
    void nextDelayErrorRetryWins() {
        type.setDetectInterval(TimeSpan.ofSeconds(30));
        monitor = new Probe();
        monitor.init(type, 1, "p");
        monitor.setState(AssetState.ERROR);

        DetectTask task = new DetectTask(monitor, dispatcher);
        assertThat(task.getNextDelayMs()).isEqualTo(DetectTask.RETRY_INTERVAL_MS);
    }

    @Test
    @DisplayName("getDetectIntervalMs returns configured interval")
    void detectIntervalMs() {
        DetectTask task = new DetectTask(monitor, dispatcher);
        assertThat(task.getDetectIntervalMs()).isEqualTo(TimeSpan.ofMinutes(5).toMillis());
    }

    // ======================== timeout enforcement ========================

    @Test
    @DisplayName("run dispatches error result on detect timeout")
    void runHandlesTimeout() {
        Probe hangingProbe = new Probe() {
            @Override
            public void detect(com.systar.monitor.result.IMonitorResult result) throws Exception {
                Thread.sleep(60_000); // far exceeds default 30s timeout
            }
        };
        hangingProbe.init(type, 1, "hanging");
        hangingProbe.setDetectTimeoutMs(500); // clamped to MIN_INTERVAL (1000ms) by setter

        DetectTask task = new DetectTask(hangingProbe, dispatcher);
        task.run();

        verify(dispatcher).dispatch(any(MonitorResult.class));
        MonitorResult last = task.getLastResult();
        assertThat(last).isNotNull();
        assertThat(last.hasError()).isTrue();
        assertThat(last.getError()).contains("检测超时");
    }

    @Test
    @DisplayName("run clears detecting flag after timeout for manual task")
    void runClearsDetectingAfterTimeout() {
        Probe hangingProbe = new Probe() {
            @Override
            public void detect(com.systar.monitor.result.IMonitorResult result) throws Exception {
                Thread.sleep(60_000);
            }
        };
        hangingProbe.init(type, 1, "hanging");
        hangingProbe.setDetectTimeoutMs(500);
        hangingProbe.setDetecting(true);

        DetectTask task = new DetectTask(hangingProbe, dispatcher, true /*manual*/);
        task.run();

        assertThat(hangingProbe.isDetecting()).isFalse();
    }

    @Test
    @DisplayName("run completes normally when detect finishes within timeout")
    void runSucceedsWithinTimeout() {
        Probe quickProbe = new Probe() {
            @Override
            public void detect(com.systar.monitor.result.IMonitorResult result) throws Exception {
                Thread.sleep(100); // well within 30s default
            }
        };
        quickProbe.init(type, 1, "quick");

        DetectTask task = new DetectTask(quickProbe, dispatcher);
        task.run();

        verify(dispatcher).dispatch(any(MonitorResult.class));
        assertThat(task.getLastResult()).isNotNull();
        assertThat(task.getLastResult().hasError()).isFalse();
    }

    @Test
    @DisplayName("shutdownExecutor does not throw")
    void shutdownExecutor() {
        assertThatCode(() -> DetectTask.shutdownExecutor())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("run dispatches error result when main thread is interrupted during future.get")
    void runHandlesMainThreadInterruption() {
        // Simulate a main-thread interruption during future.get()
        Probe probe = createProbe(1, "probe1");
        Thread.currentThread().interrupt();

        DetectTask task = new DetectTask(probe, dispatcher, true /*manual*/);
        task.run();

        // The interrupted flag should be preserved
        assertThat(Thread.interrupted()).isTrue();
        // Result should still be dispatched with an error
        verify(dispatcher).dispatch(any(MonitorResult.class));
        MonitorResult last = task.getLastResult();
        assertThat(last).isNotNull();
        assertThat(last.hasError()).isTrue();
        // Manual task should clear detecting flag even on interruption
        assertThat(probe.isDetecting()).isFalse();
    }

    private Probe createProbe(int id, String name) {
        Probe p = new Probe();
        ProbeType t = new ProbeType("pt");
        t.setDetectInterval(TimeSpan.ofMinutes(5));
        p.init(t, id, name);
        return p;
    }
}
