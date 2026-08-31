package com.systar.monitor.integration;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.SpaceType;
import com.systar.monitor.asset.type.VirtualProbeType;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the full VirtualProbe lifecycle:
 * registration → dependency update triggers → recalculation → chain propagation.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class VirtualProbeLifecycleIT {

    private AssetStore store;
    private ResultDispatcher dispatcher;
    private VirtualProbeEngine engine;
    private List<MonitorResult> collectedResults;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new SpaceType("root"), "root");
        collectedResults = new CopyOnWriteArrayList<>();
        dispatcher = new ResultDispatcher();
        dispatcher.setApplicationEventPublisher(event -> {
            if (event instanceof MonitorResultEvent mre && mre.getResult() != null) {
                collectedResults.add(mre.getResult());
            }
        });
        engine = new VirtualProbeEngine(store, dispatcher);
    }

    private Probe createSourceProbe(int id, String name, Object value) {
        Probe p = new Probe();
        p.init(new ProbeType("src"), id, name);
        p.setValue(value);
        store.addAsset(p);
        return p;
    }

    private VirtualProbe createVirtualProbe(int id, String name, String expression, String dependsOn) {
        VirtualProbeType type = new VirtualProbeType("vp-" + id);
        type.setExpression(expression);
        type.setDependsOn(dependsOn);
        VirtualProbe vp = new VirtualProbe();
        vp.init(type, id, name);
        vp.setAssetStore(store);
        vp.parseDependsOn();
        vp.compileExpression();
        store.addAsset(vp);
        engine.register(vp);
        return vp;
    }

    private void fireProbeResult(int probeId, Object value) {
        Asset<?> asset = store.findAsset(probeId);
        assertThat(asset).isInstanceOf(Probe.class);
        Probe probe = (Probe) asset;
        probe.setValue(value);
        MonitorResult result = new MonitorResult(probe, value);
        engine.onMonitorResult(new MonitorResultEvent(this, result));
    }

    // ======================== basic recalculation ========================

    @Nested
    @DisplayName("Basic VP recalculation")
    class BasicRecalculation {

        @Test
        @DisplayName("VP recalculates when dependency probe updates")
        void recalcOnDependencyUpdate() {
            createSourceProbe(101, "temp_in", 25.0);
            createVirtualProbe(200, "doubled", "#probe[101].value * 2", "101");

            fireProbeResult(101, 30.0);

            assertThat(collectedResults).hasSize(1);
            MonitorResult vpResult = collectedResults.get(0);
            assertThat(vpResult.getMonitor().getId()).isEqualTo(200);
            assertThat(vpResult.getValue()).isEqualTo(60.0);
        }

        @Test
        @DisplayName("VP with multiple dependencies recalculates correctly")
        void multiDependency() {
            createSourceProbe(101, "input", 10.0);
            createSourceProbe(102, "output", 8.0);
            createVirtualProbe(200, "efficiency", "#probe[101].value / #probe[102].value * 100", "101,102");

            fireProbeResult(101, 20.0);

            assertThat(collectedResults).hasSize(1);
            assertThat(collectedResults.get(0).getValue()).isEqualTo(250.0);
        }
    }

    // ======================== chain propagation ========================

    @Nested
    @DisplayName("Chain propagation")
    class ChainPropagation {

        @Test
        @DisplayName("VP chain: probe→VP200→VP201 propagates correctly via two triggers")
        void chainPropagation() {
            createSourceProbe(101, "source", 10.0);
            VirtualProbe vp200 = createVirtualProbe(200, "doubled", "#probe[101].value * 2", "101");
            createVirtualProbe(201, "plus10", "#probe[200].value + 10", "200");

            // Step 1: source probe fires → VP200 recalculates (5*2=10)
            fireProbeResult(101, 5.0);
            assertThat(collectedResults).hasSize(1);
            assertThat(collectedResults.get(0).getMonitor().getId()).isEqualTo(200);
            assertThat(collectedResults.get(0).getValue()).isEqualTo(10.0);
            assertThat(vp200.getValue()).isEqualTo(10.0);

            // Step 2: VP200's result fires → VP201 recalculates (10+10=20)
            fireProbeResult(200, 10.0);
            assertThat(collectedResults).hasSize(2);

            MonitorResult vp201Result = collectedResults.stream()
                    .filter(r -> r.getMonitor().getId() == 201).findFirst().orElse(null);
            assertThat(vp201Result).isNotNull();
            assertThat(vp201Result.getValue()).isEqualTo(20.0);
        }
    }

    // ======================== lifecycle (register/unregister) ========================

    @Nested
    @DisplayName("VP lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("unregistered VP no longer triggers on dependency update")
        void unregisterStopsTriggering() {
            createSourceProbe(101, "source", 10.0);
            createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");

            fireProbeResult(101, 20.0);
            assertThat(collectedResults).hasSize(1);

            engine.unregister(200);
            collectedResults.clear();

            fireProbeResult(101, 30.0);
            assertThat(collectedResults).isEmpty();
        }

        @Test
        @DisplayName("re-registered VP triggers again after unregister+register")
        void reRegisterRestoresTriggering() {
            createSourceProbe(101, "source", 10.0);
            VirtualProbe vp = createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");

            engine.unregister(200);
            engine.register(vp);
            collectedResults.clear();

            fireProbeResult(101, 25.0);
            assertThat(collectedResults).hasSize(1);
            assertThat(collectedResults.get(0).getValue()).isEqualTo(50.0);
        }
    }

    // ======================== error handling ========================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("VP dispatches error when dependency probe has null value")
        void errorOnNullDependency() {
            Probe dep = new Probe();
            dep.init(new ProbeType("src"), 101, "source");
            store.addAsset(dep);

            createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");

            // Fire a result for probe 101 — VP will trigger but dep.getValue() is null
            dep.setValue(null);
            MonitorResult depResult = new MonitorResult(dep, null);
            engine.onMonitorResult(new MonitorResultEvent(this, depResult));

            // VP should have been triggered and dispatched with error
            assertThat(collectedResults).hasSize(1);
            assertThat(collectedResults.get(0).hasError()).isTrue();
        }
    }
}
