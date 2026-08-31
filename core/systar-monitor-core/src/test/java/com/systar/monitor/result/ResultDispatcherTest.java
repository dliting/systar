package com.systar.monitor.result;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ResultDispatcherTest {

    private ResultDispatcher dispatcher;
    private ApplicationEventPublisher publisher;
    private Probe monitor;
    private ProbeType type;

    @BeforeEach
    void setUp() {
        dispatcher = new ResultDispatcher();
        publisher = mock(ApplicationEventPublisher.class);
        dispatcher.setApplicationEventPublisher(publisher);

        type = new ProbeType("pt");
        monitor = new Probe();
        monitor.init(type, 1, "probe1");
    }

    @Test
    @DisplayName("dispatch normal value: transform, change detect, no warn -> NORMAL state")
    void dispatchNormalValue() {
        MonitorResult result = new MonitorResult(monitor, 42);

        dispatcher.dispatch(result);

        assertThat(result.getValue()).isEqualTo(42);
        assertThat(result.isChanged()).isTrue(); // first value, old was null
        assertThat(result.getStatus()).isEqualTo(AssetState.NORMAL);
        assertThat(monitor.getValue()).isEqualTo(42);
        assertThat(monitor.getState()).isEqualTo(AssetState.NORMAL);
        assertThat(monitor.getRuntimeDesc()).isNull();
        verify(publisher).publishEvent(any(MonitorResultEvent.class));
    }

    @Test
    @DisplayName("dispatch with warning condition sets WARNING state")
    void dispatchWithWarning() {
        type.setWarnCondition("#value > 50");
        monitor = new Probe();
        monitor.init(type, 1, "probe1");

        MonitorResult result = new MonitorResult(monitor, 80);
        dispatcher.dispatch(result);

        assertThat(result.getStatus()).isEqualTo(AssetState.WARNING);
        assertThat(monitor.getState()).isEqualTo(AssetState.WARNING);
    }

    @Test
    @DisplayName("dispatch with transform applies SpEL transform")
    void dispatchWithTransform() {
        type.setTransform("#value * 10");
        monitor = new Probe();
        monitor.init(type, 1, "probe1");

        MonitorResult result = new MonitorResult(monitor, 5);
        dispatcher.dispatch(result);

        assertThat(result.getValue()).isEqualTo(50);
        assertThat(monitor.getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("dispatch error result sets ERROR state")
    void dispatchError() {
        MonitorResult result = new MonitorResult(monitor, "sensor offline");

        dispatcher.dispatch(result);

        assertThat(result.hasError()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AssetState.ERROR);
        assertThat(monitor.getState()).isEqualTo(AssetState.ERROR);
        assertThat(monitor.getRuntimeDesc()).isEqualTo("sensor offline");
        assertThat(result.isChanged()).isTrue();
    }

    @Test
    @DisplayName("dispatch repeated same error marks changed only when desc changes")
    void dispatchRepeatedError() {
        // First error
        MonitorResult result1 = new MonitorResult(monitor, "err1");
        dispatcher.dispatch(result1);
        assertThat(result1.isChanged()).isTrue();

        // Same error state, different message
        MonitorResult result2 = new MonitorResult(monitor, "err2");
        dispatcher.dispatch(result2);
        assertThat(result2.isChanged()).isTrue(); // different description

        // Same error state, same message
        MonitorResult result3 = new MonitorResult(monitor, "err2");
        dispatcher.dispatch(result3);
        assertThat(result3.isChanged()).isFalse(); // same description
    }

    @Test
    @DisplayName("dispatch blanks error -> 'unknown error'")
    void dispatchBlankError() {
        MonitorResult result = new MonitorResult(monitor, "   ");
        // String constructor sets error

        dispatcher.dispatch(result);

        assertThat(result.getError()).isEqualTo("unknown error");
    }

    @Test
    @DisplayName("dispatch null result does nothing")
    void dispatchNull() {
        dispatcher.dispatch(null);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("dispatch result with null monitor does nothing")
    void dispatchNullMonitor() {
        MonitorResult result = new MonitorResult(null, 42);
        // Will NPE or be guarded
        assertThatNoException().isThrownBy(() -> dispatcher.dispatch(result));
    }

    @Test
    @DisplayName("dispatch change detection: same value does not mark changed")
    void dispatchSameValueNotChanged() {
        monitor.setValue(42);

        MonitorResult result = new MonitorResult(monitor, 42);
        dispatcher.dispatch(result);

        assertThat(result.isChanged()).isFalse();
    }

    @Test
    @DisplayName("dispatch updates lastDetectTime")
    void dispatchUpdatesDetectTime() {
        long before = monitor.getLastDetectTimeMs();

        MonitorResult result = new MonitorResult(monitor, 42);
        result.setSampleTime(before + 1000);
        dispatcher.dispatch(result);

        assertThat(monitor.getLastDetectTimeMs()).isEqualTo(before + 1000);
    }

    @Test
    @DisplayName("dispatch does not overwrite detect time with older sample")
    void dispatchDoesNotOverwriteOlderSample() {
        monitor.setLastDetectTimeMs(5000);

        MonitorResult result = new MonitorResult(monitor, 42);
        result.setSampleTime(3000);
        dispatcher.dispatch(result);

        assertThat(monitor.getLastDetectTimeMs()).isEqualTo(5000);
    }
}
