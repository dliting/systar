package com.systar.monitor.drivers.simulate;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SimulateProbeTest {

    private SimulateProbe probe;

    @BeforeEach
    void setUp() {
        probe = new SimulateProbe();
    }

    private void initProbeWithSource(String source) {
        ProbeType type = new ProbeType("test-probe");
        type.setSource(source);
        probe.init(type, 1, "test-probe");
    }

    private void initProbeWithoutSource() {
        ProbeType type = new ProbeType("test-probe");
        type.setSource(null);
        probe.init(type, 1, "test-probe");
    }

    private void initProbeWithBlankSource() {
        ProbeType type = new ProbeType("test-probe");
        type.setSource("   ");
        probe.init(type, 1, "test-probe");
    }

    // ======================== detect general ========================

    @Nested
    @DisplayName("detect - general behaviour")
    class DetectGeneral {

        @Test
        @DisplayName("detect sets a non-null value on the result")
        void detectSetsNonNullValue() throws Exception {
            initProbeWithoutSource();
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            assertThat(result.getValue()).isNotNull();
        }

        @Test
        @DisplayName("detect sets sample time to a reasonable timestamp")
        void detectSetsSampleTime() throws Exception {
            initProbeWithoutSource();
            MonitorResult result = new MonitorResult(probe);
            long before = System.currentTimeMillis();

            probe.detect(result);

            assertThat(result.getSampleTime()).isBetween(before - 1000, System.currentTimeMillis() + 1000);
        }

        @Test
        @DisplayName("detect with blank source produces a fallback random value")
        void detectWithBlankSource() throws Exception {
            initProbeWithBlankSource();
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            assertThat(result.getValue()).isInstanceOf(Double.class);
            double val = (Double) result.getValue();
            assertThat(val).isBetween(0.0, 100.0);
        }
    }

    // ======================== random mode ========================

    @Nested
    @DisplayName("detect - random mode")
    class RandomMode {

        @Test
        @DisplayName("random:min:max produces a value in range")
        void randomInRange() throws Exception {
            initProbeWithSource("random:10:30");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            assertThat(result.getValue()).isInstanceOf(Double.class);
            double val = (Double) result.getValue();
            assertThat(val).isBetween(10.0, 30.0);
        }

        @Test
        @DisplayName("random with single arg defaults max to 100")
        void randomSingleArg() throws Exception {
            initProbeWithSource("random:50");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            double val = (Double) result.getValue();
            assertThat(val).isBetween(50.0, 100.0);
        }

        @Test
        @DisplayName("random with no args defaults to [0, 100)")
        void randomNoArgs() throws Exception {
            initProbeWithSource("random");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            double val = (Double) result.getValue();
            assertThat(val).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("random produces different values on successive calls (statistically)")
        void randomProducesDifferentValues() throws Exception {
            initProbeWithSource("random:0:1000000");
            MonitorResult r1 = new MonitorResult(probe);
            MonitorResult r2 = new MonitorResult(probe);

            probe.detect(r1);
            probe.detect(r2);

            // Two random values in a wide range should differ
            assertThat(r1.getValue()).isNotEqualTo(r2.getValue());
        }
    }

    // ======================== fixed mode ========================

    @Nested
    @DisplayName("detect - fixed mode")
    class FixedMode {

        @Test
        @DisplayName("fixed:value returns the constant value")
        void fixedReturnsConstant() throws Exception {
            initProbeWithSource("fixed:42.5");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            assertThat(result.getValue()).isInstanceOf(Double.class);
            assertThat((Double) result.getValue()).isCloseTo(42.5, within(0.001));
        }

        @Test
        @DisplayName("fixed with no value defaults to 0")
        void fixedNoValue() throws Exception {
            initProbeWithSource("fixed");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            assertThat((Double) result.getValue()).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("fixed returns same value on multiple calls")
        void fixedIsStable() throws Exception {
            initProbeWithSource("fixed:99.9");
            MonitorResult r1 = new MonitorResult(probe);
            MonitorResult r2 = new MonitorResult(probe);

            probe.detect(r1);
            probe.detect(r2);

            assertThat(r1.getValue()).isEqualTo(r2.getValue());
        }
    }

    // ======================== increment mode ========================

    @Nested
    @DisplayName("detect - increment mode")
    class IncrementMode {

        @Test
        @DisplayName("increment:start:step increases by step each call")
        void incrementIncreasesByStep() throws Exception {
            initProbeWithSource("increment:0:5");
            MonitorResult r1 = new MonitorResult(probe);
            MonitorResult r2 = new MonitorResult(probe);
            MonitorResult r3 = new MonitorResult(probe);

            probe.detect(r1);
            probe.detect(r2);
            probe.detect(r3);

            double v1 = (Double) r1.getValue();
            double v2 = (Double) r2.getValue();
            double v3 = (Double) r3.getValue();

            assertThat(v2 - v1).isCloseTo(5.0, within(0.001));
            assertThat(v3 - v2).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("increment with only start uses default step 1")
        void incrementDefaultStep() throws Exception {
            initProbeWithSource("increment:100");
            MonitorResult r1 = new MonitorResult(probe);
            MonitorResult r2 = new MonitorResult(probe);

            probe.detect(r1);
            probe.detect(r2);

            double v1 = (Double) r1.getValue();
            double v2 = (Double) r2.getValue();

            assertThat(v2 - v1).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("increment with no args uses start=0 step=1")
        void incrementNoArgs() throws Exception {
            initProbeWithSource("increment");
            MonitorResult r1 = new MonitorResult(probe);
            MonitorResult r2 = new MonitorResult(probe);

            probe.detect(r1);
            probe.detect(r2);

            double v1 = (Double) r1.getValue();
            double v2 = (Double) r2.getValue();

            assertThat(v1).isCloseTo(1.0, within(0.001));
            assertThat(v2).isCloseTo(2.0, within(0.001));
        }
    }

    // ======================== sine mode ========================

    @Nested
    @DisplayName("detect - sine mode")
    class SineMode {

        @Test
        @DisplayName("sin:amplitude:offset produces a value within amplitude range")
        void sinWithinRange() throws Exception {
            initProbeWithSource("sin:10:5");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            double val = (Double) result.getValue();
            // sin: amplitude * sin(phase) + offset => range is [offset - amplitude, offset + amplitude]
            assertThat(val).isBetween(-5.0, 15.0);
        }

        @Test
        @DisplayName("sin with single arg uses default offset 0")
        void sinSingleArg() throws Exception {
            initProbeWithSource("sin:5");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            double val = (Double) result.getValue();
            assertThat(val).isBetween(-5.0, 5.0);
        }

        @Test
        @DisplayName("sin with no args uses amplitude=1 offset=0")
        void sinNoArgs() throws Exception {
            initProbeWithSource("sin");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            double val = (Double) result.getValue();
            assertThat(val).isBetween(-1.0, 1.0);
        }
    }

    // ======================== fallback ========================

    @Nested
    @DisplayName("detect - unknown mode fallback")
    class Fallback {

        @Test
        @DisplayName("unknown mode falls back to random [0, 100)")
        void unknownModeFallback() throws Exception {
            initProbeWithSource("unknown_pattern");
            MonitorResult result = new MonitorResult(probe);

            probe.detect(result);

            assertThat(result.getValue()).isInstanceOf(Double.class);
            double val = (Double) result.getValue();
            assertThat(val).isBetween(0.0, 100.0);
        }
    }

    // ======================== kind and visitor ========================

    @Test
    @DisplayName("SimulateProbe is of kind PROBE")
    void probeKindIsProbe() {
        assertThat(probe.getKind()).isEqualTo(com.systar.monitor.asset.AssetKind.PROBE);
    }

    @Test
    @DisplayName("SimulateProbe accepts visitor with correct visit method")
    void probeAcceptsVisitor() {
        com.systar.monitor.asset.AssetVisitor<String> visitor = new com.systar.monitor.asset.AssetVisitor<>() {
            @Override public String visit(com.systar.monitor.asset.type.Space space) { return "space"; }
            @Override public String visit(com.systar.monitor.asset.type.Device device) { return "device"; }
            @Override public String visit(com.systar.monitor.asset.Probe probe) { return "probe"; }
            @Override public String visit(com.systar.monitor.asset.VirtualProbe vp) { return "virtualProbe"; }
            @Override public String visit(com.systar.monitor.asset.Control control) { return "control"; }
            @Override public String visit(com.systar.monitor.asset.MonitorService service) { return "service"; }
        };

        assertThat(probe.accept(visitor)).isEqualTo("probe");
    }
}
