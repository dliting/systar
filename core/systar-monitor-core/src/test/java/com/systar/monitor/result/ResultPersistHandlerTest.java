package com.systar.monitor.result;

import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ResultPersistHandler}.
 * <p>
 * Uses reflection to invoke the private {@code persistOne} method directly,
 * avoiding the need to start the background consumer thread.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ResultPersistHandlerTest {

    private SampleRepository sampleRepository;
    private ResultPersistHandler handler;

    @BeforeEach
    void setUp() {
        sampleRepository = mock(SampleRepository.class);
        handler = new ResultPersistHandler(sampleRepository);
    }

    // ======================== persistOne routing tests ========================

    @Test
    @DisplayName("persistOne routes FLOAT value to saveFloat")
    void persistFloatValue() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 10, "temp-sensor");
        monitor.setValue(23.5);

        MonitorResult result = new MonitorResult(monitor, 23.5);
        result.setSampleTime(1000L);

        invokePersistOne(result);

        verify(sampleRepository).saveFloat(10, 23.5f, 1000L);
        verifyNoMoreInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistOne routes INT value to saveInt")
    void persistIntValue() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 20, "counter");
        monitor.setValue(42);

        MonitorResult result = new MonitorResult(monitor, 42);
        result.setSampleTime(2000L);

        invokePersistOne(result);

        verify(sampleRepository).saveInt(20, 42, 2000L);
        verifyNoMoreInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistOne routes Long value to saveInt")
    void persistLongValue() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 21, "long-counter");
        monitor.setValue(100L);

        MonitorResult result = new MonitorResult(monitor, 100L);
        result.setSampleTime(2001L);

        invokePersistOne(result);

        verify(sampleRepository).saveInt(21, 100, 2001L);
        verifyNoMoreInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistOne routes BOOLEAN value to saveBoolean")
    void persistBooleanValue() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 30, "alarm-bit");
        monitor.setValue(true);

        MonitorResult result = new MonitorResult(monitor, true);
        result.setSampleTime(3000L);

        invokePersistOne(result);

        verify(sampleRepository).saveBoolean(30, true, 3000L);
        verifyNoMoreInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistOne routes error result to saveException")
    void persistErrorResult() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 40, "faulty-sensor");
        monitor.setValue("sensor offline");

        MonitorResult result = new MonitorResult(monitor, "sensor offline");
        result.setSampleTime(4000L);

        invokePersistOne(result);

        verify(sampleRepository).saveException(40, "sensor offline", 4000L);
        verifyNoMoreInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistOne updates lastSavingTimeMs after persist")
    void updatesLastSavingTime() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 10, "temp-sensor");
        monitor.setValue(25.0);
        monitor.setLastSavingTimeMs(0);

        MonitorResult result = new MonitorResult(monitor, 25.0);
        invokePersistOne(result);

        assertThat(monitor.getLastSavingTimeMs()).isGreaterThan(0);
    }

    // ======================== persist with null / non-matching value ========================

    @Test
    @DisplayName("persistFloat with non-Number value does not call repository")
    void persistFloatNonNumberValue() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 10, "temp-sensor");
        monitor.setValue(3.14);

        MonitorResult result = new MonitorResult(monitor, "not-a-number");
        result.setSampleTime(5000L);

        invokePersistOne(result);

        verifyNoInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistBoolean with non-Boolean value does not call repository")
    void persistBooleanNonBooleanValue() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 30, "alarm-bit");
        monitor.setValue(true);

        MonitorResult result = new MonitorResult(monitor, "yes");
        result.setSampleTime(6000L);

        invokePersistOne(result);

        verifyNoInteractions(sampleRepository);
    }

    @Test
    @DisplayName("persistString with no error does not call repository")
    void persistStringNoError() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 50, "text-sensor");
        monitor.setValue("hello");

        // Use Object constructor to avoid String constructor setting error field
        MonitorResult result = new MonitorResult(monitor, (Object) "hello");
        result.setSampleTime(7000L);

        invokePersistOne(result);

        // persistString only calls repository for error results
        verifyNoInteractions(sampleRepository);
    }

    // ======================== dataType metadata override ========================

    @Test
    @DisplayName("persistOne uses dataType metadata when set")
    void persistWithDataTypeMetadata() throws Exception {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 60, "override-sensor");
        monitor.setMetadata("dataType", DataType.FLOAT);
        monitor.setValue(99.9);

        MonitorResult result = new MonitorResult(monitor, 99.9);
        result.setSampleTime(8000L);

        invokePersistOne(result);

        verify(sampleRepository).saveFloat(60, 99.9f, 8000L);
        verifyNoMoreInteractions(sampleRepository);
    }

    // ======================== onMonitorResultEvent tests ========================

    @Test
    @DisplayName("onMonitorResultEvent with null result does nothing")
    void eventWithNullResult() {
        MonitorResultEvent event = new MonitorResultEvent(this, null);

        handler.onMonitorResultEvent(event);

        verifyNoInteractions(sampleRepository);
    }

    @Test
    @DisplayName("onMonitorResultEvent enqueues result when shouldPersist is true")
    void eventEnqueuesWhenShouldPersist() {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 10, "temp-sensor");
        monitor.setLastSavingTimeMs(0); // ensures shouldSave returns true

        MonitorResult result = new MonitorResult(monitor, 42);
        MonitorResultEvent event = new MonitorResultEvent(this, result);

        handler.onMonitorResultEvent(event);

        // Verify via the queue by draining
        assertThat(handler.getQueueSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("onMonitorResultEvent skips result when throttle active")
    void eventSkipsWhenThrottled() {
        ProbeType type = new ProbeType("pt");
        Probe monitor = new Probe();
        monitor.init(type, 10, "temp-sensor");
        monitor.setLastSavingTimeMs(System.currentTimeMillis()); // recent save -> throttle
        monitor.setValue(42);

        MonitorResult result = new MonitorResult(monitor, 42);
        result.setChanged(false);
        MonitorResultEvent event = new MonitorResultEvent(this, result);

        handler.onMonitorResultEvent(event);

        assertThat(handler.getQueueSize()).isEqualTo(0);
    }

    // ======================== constructor validation ========================

    @Test
    @DisplayName("constructor rejects null sampleRepository")
    void constructorRejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ResultPersistHandler(null));
    }

    // ======================== helper methods ========================

    /**
     * Invokes the private {@code persistOne} method via reflection.
     */
    private void invokePersistOne(MonitorResult result) throws Exception {
        Method method = ResultPersistHandler.class.getDeclaredMethod(
                "persistOne", MonitorResult.class);
        method.setAccessible(true);
        method.invoke(handler, result);
    }
}
