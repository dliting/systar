package com.systar.monitor.linkage;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class BooleanMarkerTriggerStrategyTest {

    private BooleanMarkerTriggerStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BooleanMarkerTriggerStrategy();
    }

    private Probe createProbe(String unit, AssetState state) {
        ProbeType type = new ProbeType("test-probe");
        type.setUnit(unit);
        Probe probe = new Probe() {
            @Override
            public void detect(com.systar.monitor.result.IMonitorResult result) {
            }
        };
        probe.init(type, 1, "test-probe");
        probe.setState(state);
        return probe;
    }

    private MonitorResult resultFor(Probe probe, Object value) {
        return new MonitorResult(probe, value);
    }

    @Nested
    @DisplayName("shouldTrigger")
    class ShouldTrigger {

        @Test
        @DisplayName("NORMAL state + unit with pipe returns true")
        void normalWithPipe() {
            Probe probe = createProbe("off|on", AssetState.NORMAL);
            assertThat(strategy.shouldTrigger(probe, resultFor(probe, "on"))).isTrue();
        }

        @Test
        @DisplayName("NORMAL state + unit without pipe returns false")
        void normalWithoutPipe() {
            Probe probe = createProbe("C", AssetState.NORMAL);
            assertThat(strategy.shouldTrigger(probe, resultFor(probe, 25.0))).isFalse();
        }

        @Test
        @DisplayName("WARNING state + unit with pipe returns false")
        void warningWithPipe() {
            Probe probe = createProbe("off|on", AssetState.WARNING);
            assertThat(strategy.shouldTrigger(probe, resultFor(probe, "on"))).isFalse();
        }

        @Test
        @DisplayName("ERROR state returns false")
        void errorState() {
            Probe probe = createProbe("off|on", AssetState.ERROR);
            assertThat(strategy.shouldTrigger(probe, resultFor(probe, "on"))).isFalse();
        }

        @Test
        @DisplayName("OFFLINE state returns false")
        void offlineState() {
            Probe probe = createProbe("off|on", AssetState.OFFLINE);
            assertThat(strategy.shouldTrigger(probe, resultFor(probe, "on"))).isFalse();
        }

        @Test
        @DisplayName("null unit returns false")
        void nullUnit() {
            Probe probe = createProbe(null, AssetState.NORMAL);
            assertThat(strategy.shouldTrigger(probe, resultFor(probe, "on"))).isFalse();
        }

        @Test
        @DisplayName("null type returns false")
        void nullType() {
            Probe probe = new Probe() {
                @Override
                public void detect(com.systar.monitor.result.IMonitorResult result) {
                }
            };
            probe.setState(AssetState.NORMAL);

            assertThat(strategy.shouldTrigger(probe, resultFor(probe, "on"))).isFalse();
        }
    }
}
