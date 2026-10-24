package com.systar.monitor.asset;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.type.MonitorType;
import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorTest {

    private ProbeType type;
    private Probe probe;

    @BeforeEach
    void setUp() {
        type = new ProbeType("tempProbe");
        type.setDetectInterval(TimeSpan.ofMinutes(5));
        type.setSavingInterval(TimeSpan.ofMinutes(10));
        probe = new Probe();
        probe.init(type, 1, "probe1");
    }

    // ---- SpEL transform ----

    @Test
    @DisplayName("applyTransform returns raw value when no transform configured")
    void noTransformReturnsRaw() {
        assertThat(probe.applyTransform(42)).isEqualTo(42);
    }

    @Test
    @DisplayName("applyTransform applies SpEL expression")
    void applyTransformWithExpression() {
        type.setTransform("#value * 2");
        probe = new Probe();
        probe.init(type, 1, "probe1");

        assertThat(probe.applyTransform(21)).isEqualTo(42);
    }

    @Test
    @DisplayName("applyTransform returns raw on bad expression")
    void applyTransformBadExprReturnsRaw() {
        type.setTransform("#value * "); // invalid SpEL
        probe = new Probe();
        probe.init(type, 1, "probe1");

        // Expression compilation fails -> stored as metadata error, transformExpr is null
        assertThat(probe.applyTransform(21)).isEqualTo(21);
        assertThat(probe.<String>getMetadata("transformExprError")).isNotNull();
    }

    @Test
    @DisplayName("applyTransform returns raw on evaluation error")
    void applyTransformEvalErrorReturnsRaw() {
        type.setTransform("#value.nonexistentMethod()");
        probe = new Probe();
        probe.init(type, 1, "probe1");

        // Expression compiles but fails at runtime
        assertThat(probe.applyTransform(21)).isEqualTo(21);
        assertThat(probe.<String>getMetadata("transformError")).isNotNull();
    }

    // ---- SpEL warn condition ----

    @Test
    @DisplayName("evaluateWarnCondition returns false when no warn configured")
    void noWarnReturnsFalse() {
        assertThat(probe.evaluateWarnCondition(100)).isFalse();
    }

    @Test
    @DisplayName("evaluateWarnCondition triggers for matching condition")
    void warnConditionTriggers() {
        type.setWarnCondition("#value > 50");
        probe = new Probe();
        probe.init(type, 1, "probe1");

        assertThat(probe.evaluateWarnCondition(60)).isTrue();
        assertThat(probe.evaluateWarnCondition(30)).isFalse();
    }

    @Test
    @DisplayName("evaluateWarnCondition returns false on bad expression")
    void warnConditionBadExpr() {
        type.setWarnCondition("invalid {] spel");
        probe = new Probe();
        probe.init(type, 1, "probe1");

        assertThat(probe.evaluateWarnCondition(60)).isFalse();
        assertThat(probe.<String>getMetadata("warnExprError")).isNotNull();
    }

    // ---- timing checks ----

    @Test
    @DisplayName("shouldDetect returns true when interval elapsed")
    void shouldDetectTrue() {
        probe.setLastDetectTimeMs(0);
        long now = TimeSpan.ofMinutes(5).toMillis() + 1;
        assertThat(probe.shouldDetect(now)).isTrue();
    }

    @Test
    @DisplayName("shouldDetect returns false when interval not elapsed")
    void shouldDetectFalse() {
        long now = 1000;
        probe.setLastDetectTimeMs(now);
        assertThat(probe.shouldDetect(now + 100)).isFalse();
    }

    @Test
    @DisplayName("shouldSave returns true when interval elapsed")
    void shouldSaveTrue() {
        probe.setLastSavingTimeMs(0);
        long now = TimeSpan.ofMinutes(10).toMillis() + 1;
        assertThat(probe.shouldSave(now)).isTrue();
    }

    @Test
    @DisplayName("shouldSave returns false when interval not elapsed")
    void shouldSaveFalse() {
        long now = 1000;
        probe.setLastSavingTimeMs(now);
        assertThat(probe.shouldSave(now + 100)).isFalse();
    }

    // ---- setSource ----

    @Test
    @DisplayName("setSource assigns monitor to service and sets mode")
    void setSource() {
        MonitorService service = mock(MonitorService.class);
        when(service.getMode()).thenReturn(MonitorMode.ACTIVE);

        probe.setSource(service);

        verify(service).addMonitor(probe);
        assertThat(probe.getMode()).isEqualTo(MonitorMode.ACTIVE);
    }

    @Test
    @DisplayName("setSource removes from old service when switching")
    void setSourceSwitch() {
        MonitorService oldService = mock(MonitorService.class);
        when(oldService.getMode()).thenReturn(MonitorMode.ACTIVE);
        MonitorService newService = mock(MonitorService.class);
        when(newService.getMode()).thenReturn(MonitorMode.PASSIVE);

        probe.setSource(oldService);
        probe.setSource(newService);

        verify(oldService).removeMonitor(probe);
        verify(newService).addMonitor(probe);
    }

    @Test
    @DisplayName("setSource no-op when same source")
    void setSourceSame() {
        MonitorService service = mock(MonitorService.class);
        when(service.getMode()).thenReturn(MonitorMode.ACTIVE);

        probe.setSource(service);
        probe.setSource(service);

        verify(service, times(1)).addMonitor(probe);
        verify(service, never()).removeMonitor(any());
    }

    // ---- interval enforcement ----

    @Test
    @DisplayName("setDetectInterval enforces minimum of 1 second")
    void setDetectIntervalMin() {
        probe.setDetectInterval(TimeSpan.parse("0"));
        // TimeSpan of 0 is less than MIN_INTERVAL (1s), should be clamped
        assertThat(probe.getDetectInterval()).isEqualTo(MonitorType.MIN_INTERVAL);
    }

    @Test
    @DisplayName("setSavingInterval enforces minimum of 1 second")
    void setSavingIntervalMin() {
        probe.setSavingInterval(TimeSpan.parse("0"));
        assertThat(probe.getSavingInterval()).isEqualTo(MonitorType.MIN_INTERVAL);
    }

    @Test
    @DisplayName("setDetectInterval accepts valid interval")
    void setDetectIntervalValid() {
        TimeSpan interval = TimeSpan.ofSeconds(30);
        probe.setDetectInterval(interval);
        assertThat(probe.getDetectInterval()).isEqualTo(interval);
    }

    @Test
    @DisplayName("setDetectInterval accepts null")
    void setDetectIntervalNull() {
        probe.setDetectInterval(null);
        assertThat(probe.getDetectInterval()).isNull();
    }

    // ---- visitor dispatch ----

    @Test
    @DisplayName("Probe dispatches visitor correctly")
    void probeVisitorDispatch() {
        AssetVisitor<String> visitor = mock(AssetVisitor.class);
        when(visitor.visit(any(Probe.class))).thenReturn("probeVisited");
        assertThat(probe.accept(visitor)).isEqualTo("probeVisited");
        verify(visitor).visit(probe);
    }

    // ---- detect default no-op ----

    @Test
    @DisplayName("Probe default detect is no-op (does not throw)")
    void probeDefaultDetect() throws Exception {
        com.systar.monitor.result.IMonitorResult result =
                mock(com.systar.monitor.result.IMonitorResult.class);
        assertThatNoException().isThrownBy(() -> probe.detect(result));
    }

    // ---- IPassiveMonitor ----

    @Test
    @DisplayName("Probe makeRegisterKey returns type source")
    void makeRegisterKey() {
        type.setSource("sensor.temp.1");
        assertThat(probe.makeRegisterKey()).isEqualTo("sensor.temp.1");
    }

    @Test
    @DisplayName("Probe makeRegisterKey returns null when type is null")
    void makeRegisterKeyNullType() {
        Probe p = new Probe();
        // type not set (id=0, no init)
        assertThat(p.makeRegisterKey()).isNull();
    }

    // ---- value and runtime description ----

    @Test
    @DisplayName("value getter/setter works")
    void valueGetterSetter() {
        probe.setValue(42.5);
        assertThat(probe.getValue()).isEqualTo(42.5);
    }

    @Test
    @DisplayName("runtimeDesc getter/setter works")
    void runtimeDescGetterSetter() {
        probe.setRuntimeDesc("error msg");
        assertThat(probe.getRuntimeDesc()).isEqualTo("error msg");
    }

    // ---- mode ----

    @Test
    @DisplayName("mode defaults to ACTIVE and can be changed")
    void modeDefault() {
        assertThat(probe.getMode()).isEqualTo(MonitorMode.ACTIVE);
        probe.setMode(MonitorMode.PASSIVE);
        assertThat(probe.getMode()).isEqualTo(MonitorMode.PASSIVE);
    }

    // ---- detectTimeoutMs ----

    @Test
    @DisplayName("detectTimeoutMs defaults to 30 seconds")
    void detectTimeoutDefault() {
        assertThat(probe.getDetectTimeoutMs()).isEqualTo(Monitor.DEFAULT_DETECT_TIMEOUT_MS);
        assertThat(Monitor.DEFAULT_DETECT_TIMEOUT_MS).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("detectTimeoutMs can be set and retrieved")
    void detectTimeoutSetterGetter() {
        probe.setDetectTimeoutMs(60_000L);
        assertThat(probe.getDetectTimeoutMs()).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("detectTimeoutMs enforces minimum interval")
    void detectTimeoutMinimum() {
        probe.setDetectTimeoutMs(100L);
        assertThat(probe.getDetectTimeoutMs()).isEqualTo(MonitorType.MIN_INTERVAL.toMillis());
    }

    // ---- detecting flag atomicity ----

    @Test
    @DisplayName("detecting flag defaults to false")
    void detectingDefaultsFalse() {
        assertThat(probe.isDetecting()).isFalse();
    }

    @Test
    @DisplayName("detecting flag can be set and read")
    void detectingSetAndGet() {
        probe.setDetecting(true);
        assertThat(probe.isDetecting()).isTrue();
        probe.setDetecting(false);
        assertThat(probe.isDetecting()).isFalse();
    }

    @Test
    @DisplayName("trySetDetecting succeeds when flag is false")
    void trySetDetectingSuccess() {
        assertThat(probe.trySetDetecting()).isTrue();
        assertThat(probe.isDetecting()).isTrue();
    }

    @Test
    @DisplayName("trySetDetecting fails when flag is already true")
    void trySetDetectingFailure() {
        probe.setDetecting(true);
        assertThat(probe.trySetDetecting()).isFalse();
        assertThat(probe.isDetecting()).isTrue();
    }
}
