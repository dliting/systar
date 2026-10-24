package com.systar.server.listener;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.monitor.result.ResultDispatcher;
import com.systar.monitor.server.MonitorEngine;
import com.systar.server.event.AssetChangedEvent;
import com.systar.server.event.AssetChangedEvent.Action;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetCrudListenerTest {

    private MonitorEngine engine;
    private ObjectProvider<MonitorEngine> engineProvider;
    private VirtualProbeEngine virtualProbeEngine;
    private AssetStore assetStore;
    private AssetCrudListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        engine = mock(MonitorEngine.class);
        engineProvider = mock(ObjectProvider.class);
        when(engineProvider.getIfAvailable()).thenReturn(engine);
        ResultDispatcher dispatcher = mock(ResultDispatcher.class);
        assetStore = new AssetStore();
        virtualProbeEngine = new VirtualProbeEngine(assetStore, dispatcher);
        listener = new AssetCrudListener(engineProvider, virtualProbeEngine, assetStore);
    }

    @Nested
    @DisplayName("CREATED event")
    class Created {

        @Test
        @DisplayName("adds asset via MonitorEngine")
        void addsAsset() {
            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 10, "p1");

            listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.PROBE, probe));
            verify(engine).addAsset(probe);
        }

        @Test
        @DisplayName("registers VirtualProbe in engine on create")
        void registersVirtualProbe() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");

            listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.PROBE, vp));

            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);
            verify(engine).addAsset(vp);
        }

        @Test
        @DisplayName("does not register non-VirtualProbe in engine on create")
        void noRegisterForRegularProbe() {
            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 10, "p1");

            listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.PROBE, probe));

            assertThat(virtualProbeEngine.getDependencyIndex()).isEmpty();
        }

        @Test
        @DisplayName("handles null asset gracefully")
        void nullAsset() {
            assertThatNoException().isThrownBy(() ->
                    listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.DEVICE, null)));
            verifyNoInteractions(engine);
        }
    }

    @Nested
    @DisplayName("UPDATED event")
    class Updated {

        @Test
        @DisplayName("updates asset via MonitorEngine")
        void updatesAsset() {
            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 10, "p1");

            listener.onAssetChanged(new AssetChangedEvent(Action.UPDATED, 10, AssetKind.PROBE, probe));
            verify(engine).updateAsset(probe);
        }

        @Test
        @DisplayName("re-registers VirtualProbe in engine on update")
        void reRegistersVirtualProbe() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");
            virtualProbeEngine.register(vp);
            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);

            // Update with new VirtualProbe instance (changed expression)
            VirtualProbe updatedVp = createVirtualProbe(10, "vp1", "#probe[102].value + 1", "102");

            listener.onAssetChanged(new AssetChangedEvent(Action.UPDATED, 10, AssetKind.PROBE, updatedVp));

            // Old dependency (101) should be removed, new (102) should be added
            assertThat(virtualProbeEngine.getDependencyIndex()).doesNotContainKey(101);
            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(102);
            verify(engine).updateAsset(updatedVp);
        }

        @Test
        @DisplayName("handles null asset gracefully on update")
        void nullAsset() {
            assertThatNoException().isThrownBy(() ->
                    listener.onAssetChanged(new AssetChangedEvent(Action.UPDATED, 10, AssetKind.PROBE, null)));
            verifyNoInteractions(engine);
        }

        @Test
        @DisplayName("unregisters VP from engine when updated to non-VP type")
        void unregistersOnTypeConversion() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");
            virtualProbeEngine.register(vp);
            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);

            Probe regularProbe = new Probe();
            regularProbe.init(new ProbeType("pt"), 10, "p1");

            listener.onAssetChanged(new AssetChangedEvent(Action.UPDATED, 10, AssetKind.PROBE, regularProbe));

            assertThat(virtualProbeEngine.getDependencyIndex()).doesNotContainKey(101);
            verify(engine).updateAsset(regularProbe);
        }
    }

    @Nested
    @DisplayName("DELETED event")
    class Deleted {

        @Test
        @DisplayName("removes asset via MonitorEngine")
        void removesAsset() {
            listener.onAssetChanged(new AssetChangedEvent(Action.DELETED, 10, AssetKind.PROBE, null));
            verify(engine).removeAsset(10);
        }

        @Test
        @DisplayName("unregisters VirtualProbe from engine on PROBE delete")
        void unregistersVirtualProbe() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");
            virtualProbeEngine.register(vp);
            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);

            listener.onAssetChanged(new AssetChangedEvent(Action.DELETED, 10, AssetKind.PROBE, null));

            assertThat(virtualProbeEngine.getDependencyIndex()).doesNotContainKey(101);
            verify(engine).removeAsset(10);
        }

        @Test
        @DisplayName("does not unregister for non-PROBE delete")
        void noUnregisterForNonProbe() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");
            virtualProbeEngine.register(vp);

            listener.onAssetChanged(new AssetChangedEvent(Action.DELETED, 20, AssetKind.DEVICE, null));

            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);
        }
    }

    @Nested
    @DisplayName("STARTED event")
    class Started {

        @Test
        @DisplayName("starts monitor via MonitorEngine")
        void startsMonitor() {
            listener.onAssetChanged(new AssetChangedEvent(Action.STARTED, 10, AssetKind.PROBE, null));
            verify(engine).startMonitor(10);
        }
    }

    @Nested
    @DisplayName("STOPPED event")
    class Stopped {

        @Test
        @DisplayName("stops monitor via MonitorEngine")
        void stopsMonitor() {
            listener.onAssetChanged(new AssetChangedEvent(Action.STOPPED, 10, AssetKind.PROBE, null));
            verify(engine).stopMonitor(10);
        }
    }

    @Nested
    @DisplayName("DISABLED event")
    class Disabled {

        @Test
        @DisplayName("stops child monitors but preserves child enabled state")
        void disablesWithCascade() {
            // Set up in-memory store with device → sub-device → probe
            Device parent = new Device();
            parent.init(new DeviceType("dt"), 1, "floor1");
            assetStore.addAsset(parent);

            Device device = new Device();
            device.init(new DeviceType("dt2"), 2, "dev1");
            device.setParentId(1);
            assetStore.addAsset(device);

            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 3, "sensor1");
            probe.setParentId(2);
            assetStore.addAsset(probe);

            // The real assetStore already holds the tree — findAsset resolves from it.
            listener.onAssetChanged(new AssetChangedEvent(Action.DISABLED, 1, AssetKind.DEVICE, null));

            // Parent device itself is disabled
            assertThat(parent.isEnabled()).isFalse();

            // Children keep their enabled state (parent disable is runtime-only)
            assertThat(device.isEnabled()).isTrue();
            assertThat(probe.isEnabled()).isTrue();

            // Probe should be stopped (unscheduled)
            verify(engine).stopMonitor(3);
        }

        @Test
        @DisplayName("handles missing asset gracefully")
        void missingAsset() {
            assertThatNoException().isThrownBy(() ->
                    listener.onAssetChanged(new AssetChangedEvent(Action.DISABLED, 999, AssetKind.PROBE, null)));
        }
    }

    @Nested
    @DisplayName("ENABLED event")
    class Enabled {

        @Test
        @DisplayName("enables asset and starts monitors")
        void enablesAndStarts() {
            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 10, "p1");
            probe.setEnabled(true);
            assetStore.addAsset(probe);

            listener.onAssetChanged(new AssetChangedEvent(Action.ENABLED, 10, AssetKind.PROBE, null));

            verify(engine).startMonitor(10);
        }

        @Test
        @DisplayName("smart cascade skips disabled children")
        void smartCascadeSkipsDisabled() {
            Device parent = new Device();
            parent.init(new DeviceType("dt"), 1, "floor1");
            assetStore.addAsset(parent);

            Probe probe1 = new Probe();
            probe1.init(new ProbeType("pt"), 10, "enabled-probe");
            probe1.setEnabled(true);
            probe1.setParentId(1);
            assetStore.addAsset(probe1);

            Probe probe2 = new Probe();
            probe2.init(new ProbeType("pt"), 11, "disabled-probe");
            probe2.setEnabled(false);
            probe2.setParentId(1);
            assetStore.addAsset(probe2);

            listener.onAssetChanged(new AssetChangedEvent(Action.ENABLED, 1, AssetKind.DEVICE, null));

            // Only enabled probe should be started
            verify(engine).startMonitor(10);
            verify(engine, never()).startMonitor(11);
        }
    }

    @Nested
    @DisplayName("Engine absent")
    class EngineAbsent {

        @Test
        @DisplayName("CRUD events sync the store directly; runtime events WARN-skip without error")
        @SuppressWarnings("unchecked")
        void skipsGracefully() {
            Device device = new Device();
            device.init(new DeviceType("dt"), 10, "d1");
            AssetCrudListener noEngine =
                    new AssetCrudListener(mock(ObjectProvider.class), virtualProbeEngine, assetStore);

            noEngine.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.DEVICE, device));
            assertThat(assetStore.findAsset(10)).isNotNull();   // store mode: CRUD applies without engine

            assertThatNoException().isThrownBy(() ->
                    noEngine.onAssetChanged(new AssetChangedEvent(Action.STARTED, 10, AssetKind.DEVICE, null)));
            verifyNoInteractions(engine);
        }
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
