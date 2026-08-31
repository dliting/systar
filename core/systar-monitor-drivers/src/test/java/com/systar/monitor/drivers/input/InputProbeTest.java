package com.systar.monitor.drivers.input;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetVisitor;
import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.Space;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class InputProbeTest {

    private InputProbe probe;

    @BeforeEach
    void setUp() {
        probe = new InputProbe();
    }

    private void initProbe() {
        ProbeType type = new ProbeType("input-probe");
        probe.init(type, 1, "input-probe");
    }

    // ======================== detect (no-op) ========================

    @Nested
    @DisplayName("detect - passive no-op")
    class DetectNoOp {

        @Test
        @DisplayName("detect does not throw and does not set value")
        void detectIsNoOp() throws Exception {
            initProbe();
            MonitorResult result = new MonitorResult(probe, (Object) null);

            probe.detect(result);

            // Value should remain null since detect is a no-op for passive probes
            assertThat(result.getValue()).isNull();
        }
    }

    // ======================== manualInput ========================

    @Nested
    @DisplayName("manualInput - pushes data through dispatcher")
    class ManualInput {

        @Test
        @DisplayName("manualInput dispatches a result with the given value")
        void manualInputDispatchesResult() {
            initProbe();
            ResultDispatcher dispatcher = mock(ResultDispatcher.class);

            probe.manualInput(42.5, dispatcher);

            verify(dispatcher).dispatch(any(MonitorResult.class));
        }

        @Test
        @DisplayName("manualInput with null dispatcher is a no-op")
        void manualInputNullDispatcher() {
            initProbe();

            assertThatCode(() -> probe.manualInput(42.5, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("manualInput dispatches correct value")
        void manualInputCorrectValue() {
            initProbe();
            ResultDispatcher dispatcher = mock(ResultDispatcher.class);

            probe.manualInput("hello", dispatcher);

            verify(dispatcher).dispatch(argThat(result ->
                    result.getValue() != null && result.getValue().equals("hello")));
        }

        @Test
        @DisplayName("manualInput dispatches result associated with this probe")
        void manualInputAssociatedMonitor() {
            initProbe();
            ResultDispatcher dispatcher = mock(ResultDispatcher.class);

            probe.manualInput(100, dispatcher);

            verify(dispatcher).dispatch(argThat(result ->
                    result.getMonitor() == probe));
        }

        @Test
        @DisplayName("manualInput with null value still dispatches")
        void manualInputNullValue() {
            initProbe();
            ResultDispatcher dispatcher = mock(ResultDispatcher.class);

            probe.manualInput(null, dispatcher);

            verify(dispatcher).dispatch(any(MonitorResult.class));
        }
    }

    // ======================== kind and visitor ========================

    @Test
    @DisplayName("InputProbe is of kind PROBE")
    void probeKindIsProbe() {
        assertThat(probe.getKind()).isEqualTo(AssetKind.PROBE);
    }

    @Test
    @DisplayName("InputProbe accepts visitor with correct visit method")
    void probeAcceptsVisitor() {
        AssetVisitor<String> visitor = new AssetVisitor<>() {
            @Override public String visit(Space space) { return "space"; }
            @Override public String visit(Device device) { return "device"; }
            @Override public String visit(com.systar.monitor.asset.Probe probe) { return "probe"; }
            @Override public String visit(com.systar.monitor.asset.VirtualProbe vp) { return "virtualProbe"; }
            @Override public String visit(com.systar.monitor.asset.Control control) { return "control"; }
            @Override public String visit(com.systar.monitor.asset.MonitorService service) { return "service"; }
        };

        assertThat(probe.accept(visitor)).isEqualTo("probe");
    }
}
