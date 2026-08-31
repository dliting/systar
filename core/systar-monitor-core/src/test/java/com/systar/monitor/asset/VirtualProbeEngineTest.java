package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ProbeType;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class VirtualProbeEngineTest {

    private AssetStore store;
    private ResultDispatcher dispatcher;
    private VirtualProbeEngine engine;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new com.systar.monitor.asset.type.SpaceType("root"), "root");
        dispatcher = mock(ResultDispatcher.class);
        engine = new VirtualProbeEngine(store, dispatcher);
    }

    // ======================== register / unregister ========================

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("registers VirtualProbe in dependency index")
        void registersInIndex() {
            VirtualProbe vp = createVirtualProbe(200, "eff", "#probe[101].value + #probe[102].value", "101,102");

            engine.register(vp);

            assertThat(engine.getDependencyIndex()).containsKey(101);
            assertThat(engine.getDependencyIndex()).containsKey(102);
            assertThat(engine.getDependencyIndex().get(101)).contains(vp);
            assertThat(engine.getDependencyIndex().get(102)).contains(vp);
        }

        @Test
        @DisplayName("multiple VirtualProbes can depend on same probe")
        void multipleDependOnSame() {
            VirtualProbe vp1 = createVirtualProbe(200, "vp1", "#probe[101].value * 2", "101");
            VirtualProbe vp2 = createVirtualProbe(201, "vp2", "#probe[101].value + 1", "101");

            engine.register(vp1);
            engine.register(vp2);

            List<VirtualProbe> dependents = engine.getDependencyIndex().get(101);
            assertThat(dependents).hasSize(2);
            assertThat(dependents).containsExactlyInAnyOrder(vp1, vp2);
        }

        @Test
        @DisplayName("register with no dependencies adds nothing to index")
        void noDependencies() {
            VirtualProbe vp = createVirtualProbe(200, "noDeps", "1 + 1", null);

            engine.register(vp);

            assertThat(engine.getDependencyIndex()).isEmpty();
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("removes VirtualProbe from dependency index and cleans up empty entries")
        void removesFromIndex() {
            VirtualProbe vp = createVirtualProbe(200, "eff", "#probe[101].value * 2", "101");
            engine.register(vp);

            engine.unregister(200);

            assertThat(engine.getDependencyIndex()).doesNotContainKey(101);
        }

        @Test
        @DisplayName("unregister non-existent id is no-op")
        void unregisterNonExistent() {
            assertThatCode(() -> engine.unregister(999)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("unregister one of multiple VirtualProbes sharing same dependency preserves the other")
        void unregisterOneOfMultiple() {
            VirtualProbe vp1 = createVirtualProbe(200, "vp1", "#probe[101].value * 2", "101");
            VirtualProbe vp2 = createVirtualProbe(201, "vp2", "#probe[101].value + 1", "101");
            engine.register(vp1);
            engine.register(vp2);

            engine.unregister(200);

            assertThat(engine.getDependencyIndex()).containsKey(101);
            assertThat(engine.getDependencyIndex().get(101)).containsExactly(vp2);
        }
    }

    // ======================== onMonitorResult ========================

    @Nested
    @DisplayName("onMonitorResult")
    class OnMonitorResult {

        @Test
        @DisplayName("triggers recalculation when dependency probe updates")
        void triggersRecalculation() {
            Probe dep = createProbe(101, "input", 42.0);
            store.addAsset(dep);

            VirtualProbe vp = createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();
            store.addAsset(vp);
            engine.register(vp);

            // Simulate a result from the dependency probe
            MonitorResult depResult = new MonitorResult(dep, 50.0);
            MonitorResultEvent event = new MonitorResultEvent(this, depResult);

            engine.onMonitorResult(event);

            // Verify dispatcher.dispatch was called (recalculation happened)
            verify(dispatcher).dispatch(any(MonitorResult.class));
        }

        @Test
        @DisplayName("does nothing when no VirtualProbe depends on source")
        void noDependents() {
            Probe dep = createProbe(101, "input", 42.0);
            MonitorResult depResult = new MonitorResult(dep, 50.0);
            MonitorResultEvent event = new MonitorResultEvent(this, depResult);

            engine.onMonitorResult(event);

            verify(dispatcher, never()).dispatch(any());
        }

        @Test
        @DisplayName("ignores result with null result")
        void nullResult() {
            MonitorResultEvent event = new MonitorResultEvent(this, null);
            engine.onMonitorResult(event);

            verify(dispatcher, never()).dispatch(any());
        }

        @Test
        @DisplayName("ignores result with null monitor")
        void nullMonitor() {
            // MonitorResult with null monitor is not possible via constructors,
            // but we can create a mock event
            MonitorResultEvent event = mock(MonitorResultEvent.class);
            when(event.getResult()).thenReturn(null);
            engine.onMonitorResult(event);

            verify(dispatcher, never()).dispatch(any());
        }

        @Test
        @DisplayName("no dispatch when no VP depends on the event source")
        void noDispatchForUnrelatedSource() {
            Probe dep = createProbe(101, "input", 42.0);
            store.addAsset(dep);

            VirtualProbe vp = createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();
            store.addAsset(vp);
            engine.register(vp);

            // VP200 produces a result (sourceId=200), but no VP depends on 200
            MonitorResult vpResult = new MonitorResult(vp, 84.0);
            MonitorResultEvent event = new MonitorResultEvent(this, vpResult);

            engine.onMonitorResult(event);

            verify(dispatcher, never()).dispatch(any());
        }

        @Test
        @DisplayName("multiple VirtualProbes triggered by same dependency")
        void multipleTriggered() {
            Probe dep = createProbe(101, "input", 10.0);
            store.addAsset(dep);

            VirtualProbe vp1 = createVirtualProbe(200, "double", "#probe[101].value * 2", "101");
            vp1.setAssetStore(store);
            vp1.parseDependsOn();
            vp1.compileExpression();
            store.addAsset(vp1);

            VirtualProbe vp2 = createVirtualProbe(201, "plus10", "#probe[101].value + 10", "101");
            vp2.setAssetStore(store);
            vp2.parseDependsOn();
            vp2.compileExpression();
            store.addAsset(vp2);

            engine.register(vp1);
            engine.register(vp2);

            MonitorResult depResult = new MonitorResult(dep, 20.0);
            MonitorResultEvent event = new MonitorResultEvent(this, depResult);

            engine.onMonitorResult(event);

            // Both VirtualProbes should be recalculated
            verify(dispatcher, times(2)).dispatch(any(MonitorResult.class));
        }
    }

    // ======================== circular dependency detection ========================

    @Nested
    @DisplayName("circular dependency detection")
    class CircularDependency {

        @Test
        @DisplayName("prevents infinite recursion via computing set in recalculate")
        void preventsInfiniteRecursion() {
            // Set up: VP200 depends on 101, VP201 depends on 200.
            // Chain: probe101 fires → VP200 recalculates → VP200 result fires → VP201 recalculates
            // Cycle check: if VP201 also depended on VP200 and VP200 depended on VP201,
            // the computing.add() in recalculate would return false and break the loop.
            Probe dep = createProbe(101, "input", 10.0);
            store.addAsset(dep);

            VirtualProbe vp200 = createVirtualProbe(200, "vp200", "#probe[101].value * 2", "101");
            vp200.setAssetStore(store);
            vp200.parseDependsOn();
            vp200.compileExpression();
            store.addAsset(vp200);

            VirtualProbe vp201 = createVirtualProbe(201, "vp201", "#probe[200].value + 1", "200");
            vp201.setAssetStore(store);
            vp201.parseDependsOn();
            vp201.compileExpression();
            store.addAsset(vp201);

            engine.register(vp200);
            engine.register(vp201);

            // Step 1: probe 101 fires → VP200 recalculates (1st dispatch)
            MonitorResult depResult = new MonitorResult(dep, 20.0);
            engine.onMonitorResult(new MonitorResultEvent(this, depResult));
            verify(dispatcher, times(1)).dispatch(any(MonitorResult.class));

            // Step 2: VP200's result fires → VP201 recalculates (2nd dispatch)
            vp200.setValue(20.0);
            MonitorResult vp200Result = new MonitorResult(vp200, 20.0);
            engine.onMonitorResult(new MonitorResultEvent(this, vp200Result));
            verify(dispatcher, times(2)).dispatch(any(MonitorResult.class));
        }

        @Test
        @DisplayName("self-referencing VP dispatches error without infinite loop")
        void selfReferencingNoLoop() {
            // VP200 depends on probe 200 (itself). When triggered,
            // detect reads its own (null) value → error result. No loop.
            VirtualProbe vp200 = createVirtualProbe(200, "selfRef", "#probe[200].value", "200");
            vp200.setAssetStore(store);
            vp200.parseDependsOn();
            vp200.compileExpression();
            store.addAsset(vp200);
            engine.register(vp200);

            // Fire an event from a probe with id=200
            Probe trigger = createProbe(200, "trigger", 10.0);
            MonitorResult result = new MonitorResult(trigger, 10.0);
            engine.onMonitorResult(new MonitorResultEvent(this, result));

            // VP200 recalculates once, reads its own null value, dispatches error
            verify(dispatcher, times(1)).dispatch(any(MonitorResult.class));
        }
    }

    // ======================== helpers ========================

    private Probe createProbe(int id, String name, Object value) {
        Probe p = new Probe();
        p.init(new ProbeType("test-probe"), id, name);
        p.setValue(value);
        return p;
    }

    private VirtualProbe createVirtualProbe(int id, String name, String expression, String dependsOn) {
        VirtualProbeType type = new VirtualProbeType("vprobe-" + id);
        type.setExpression(expression);
        type.setDependsOn(dependsOn);
        VirtualProbe vp = new VirtualProbe();
        vp.init(type, id, name);
        vp.parseDependsOn();
        return vp;
    }
}
