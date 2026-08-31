package com.systar.monitor.schedule;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CompletionHandlerTest {

    private MonitorScheduler scheduler;
    private CompletionHandler handler;

    @BeforeEach
    void setUp() {
        scheduler = mock(MonitorScheduler.class);
        handler = new CompletionHandler(scheduler);
    }

    @Test
    @DisplayName("onComplete reschedules task with positive delay")
    void onCompleteReschedules() {
        ProbeType type = new ProbeType("pt");
        type.setDetectInterval(TimeSpan.ofMinutes(5));
        Probe probe = new Probe();
        probe.init(type, 1, "probe1");

        ResultDispatcher dispatcher = mock(ResultDispatcher.class);
        DetectTask task = new DetectTask(probe, dispatcher);

        handler.onComplete(task);

        verify(scheduler).reschedule(eq(task), anyLong());
    }

    @Test
    @DisplayName("onComplete reschedules task with positive interval from default")
    void onCompleteReschedulesWithDefault() {
        ProbeType type = new ProbeType("pt");
        // detectInterval defaults to DEFAULT_DETECT_INTERVAL (10 min) via MonitorType
        Probe probe = new Probe();
        probe.init(type, 1, "probe1");

        ResultDispatcher dispatcher = mock(ResultDispatcher.class);
        DetectTask task = new DetectTask(probe, dispatcher);

        handler.onComplete(task);

        verify(scheduler).reschedule(eq(task), anyLong());
    }

    @Test
    @DisplayName("onComplete handles exception from getNextDelayMs gracefully")
    void onCompleteHandlesException() {
        Probe probe = mock(Probe.class);
        when(probe.getState()).thenThrow(new RuntimeException("unexpected"));

        ResultDispatcher dispatcher = mock(ResultDispatcher.class);
        DetectTask task = new DetectTask(probe, dispatcher);

        // Should not throw
        org.assertj.core.api.Assertions.assertThatCode(() -> handler.onComplete(task)).doesNotThrowAnyException();
    }
}
