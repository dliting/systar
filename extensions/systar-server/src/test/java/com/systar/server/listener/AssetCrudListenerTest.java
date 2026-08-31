package com.systar.server.listener;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.monitor.result.ResultDispatcher;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.event.AssetChangedEvent;
import com.systar.server.event.AssetChangedEvent.Action;
import org.junit.jupiter.api.BeforeEach;
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

    private MonitorServer monitorServer;
    private VirtualProbeEngine virtualProbeEngine;
    private AssetStore assetStore;
    private AssetCrudListener listener;

    @BeforeEach
    void setUp() {
        monitorServer = mock(MonitorServer.class);
        ResultDispatcher dispatcher = mock(ResultDispatcher.class);
        assetStore = new AssetStore();
        assetStore.createRoot(new SpaceType("root"), "root");
        virtualProbeEngine = new VirtualProbeEngine(assetStore, dispatcher);
        listener = new AssetCrudListener(monitorServer, virtualProbeEngine, assetStore);
    }

    @Nested
    @DisplayName("CREATED event")
    class Created {

        @Test
        @DisplayName("adds asset via MonitorServer")
        void addsAsset() {
            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 10, "p1");

            listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.PROBE, probe));
            verify(monitorServer).addAsset(probe);
        }

        @Test
        @DisplayName("registers VirtualProbe in engine on create")
        void registersVirtualProbe() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");

            listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.PROBE, vp));

            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);
            verify(monitorServer).addAsset(vp);
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
                    listener.onAssetChanged(new AssetChangedEvent(Action.CREATED, 10, AssetKind.SPACE, null)));
            verifyNoInteractions(monitorServer);
        }
    }

    @Nested
    @DisplayName("UPDATED event")
    class Updated {

        @Test
        @DisplayName("updates asset via MonitorServer")
        void updatesAsset() {
            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 10, "p1");

            listener.onAssetChanged(new AssetChangedEvent(Action.UPDATED, 10, AssetKind.PROBE, probe));
            verify(monitorServer).updateAsset(probe);
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
            verify(monitorServer).updateAsset(updatedVp);
        }

        @Test
        @DisplayName("handles null asset gracefully on update")
        void nullAsset() {
            assertThatNoException().isThrownBy(() ->
                    listener.onAssetChanged(new AssetChangedEvent(Action.UPDATED, 10, AssetKind.PROBE, null)));
            verifyNoInteractions(monitorServer);
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
            verify(monitorServer).updateAsset(regularProbe);
        }
    }

    @Nested
    @DisplayName("DELETED event")
    class Deleted {

        @Test
        @DisplayName("removes asset via MonitorServer")
        void removesAsset() {
            listener.onAssetChanged(new AssetChangedEvent(Action.DELETED, 10, AssetKind.PROBE, null));
            verify(monitorServer).removeAsset(10);
        }

        @Test
        @DisplayName("unregisters VirtualProbe from engine on PROBE delete")
        void unregistersVirtualProbe() {
            VirtualProbe vp = createVirtualProbe(10, "vp1", "#probe[101].value * 2", "101");
            virtualProbeEngine.register(vp);
            assertThat(virtualProbeEngine.getDependencyIndex()).containsKey(101);

            listener.onAssetChanged(new AssetChangedEvent(Action.DELETED, 10, AssetKind.PROBE, null));

            assertThat(virtualProbeEngine.getDependencyIndex()).doesNotContainKey(101);
            verify(monitorServer).removeAsset(10);
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
        @DisplayName("starts monitor via MonitorServer")
        void startsMonitor() {
            listener.onAssetChanged(new AssetChangedEvent(Action.STARTED, 10, AssetKind.PROBE, null));
            verify(monitorServer).startMonitor(10);
        }
    }

    @Nested
    @DisplayName("STOPPED event")
    class Stopped {

        @Test
        @DisplayName("stops monitor via MonitorServer")
        void stopsMonitor() {
            listener.onAssetChanged(new AssetChangedEvent(Action.STOPPED, 10, AssetKind.PROBE, null));
            verify(monitorServer).stopMonitor(10);
        }
    }

    @Nested
    @DisplayName("DISABLED event")
    class Disabled {

        @Test
        @DisplayName("stops child monitors but preserves child enabled state")
        void disablesWithCascade() {
            // Set up in-memory store with space → device → probe
            Space space = new Space();
            space.init(new SpaceType("st"), 1, "floor1");
            assetStore.addAsset(space);

            Device device = new Device();
            device.init(new DeviceType("dt"), 2, "dev1");
            device.setParentId(1);
            assetStore.addAsset(device);

            Probe probe = new Probe();
            probe.init(new ProbeType("pt"), 3, "sensor1");
            probe.setParentId(2);
            assetStore.addAsset(probe);

            // Use doReturn/when to avoid Mockito wildcard capture issues
            doReturn(space).when(monitorServer).findAsset(1);

            listener.onAssetChanged(new AssetChangedEvent(Action.DISABLED, 1, AssetKind.SPACE, null));

            // Space itself is disabled
            assertThat(space.isEnabled()).isFalse();

            // Children keep their enabled state (parent disable is runtime-only)
            assertThat(device.isEnabled()).isTrue();
            assertThat(probe.isEnabled()).isTrue();

            // Probe should be stopped (unscheduled)
            verify(monitorServer).stopMonitor(3);
        }

        @Test
        @DisplayName("handles missing asset gracefully")
        void missingAsset() {
            doReturn(null).when(monitorServer).findAsset(999);
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

            doReturn(probe).when(monitorServer).findAsset(10);

            listener.onAssetChanged(new AssetChangedEvent(Action.ENABLED, 10, AssetKind.PROBE, null));

            verify(monitorServer).startMonitor(10);
        }

        @Test
        @DisplayName("smart cascade skips disabled children")
        void smartCascadeSkipsDisabled() {
            Space space = new Space();
            space.init(new SpaceType("st"), 1, "floor1");
            assetStore.addAsset(space);

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

            doReturn(space).when(monitorServer).findAsset(1);

            listener.onAssetChanged(new AssetChangedEvent(Action.ENABLED, 1, AssetKind.SPACE, null));

            // Only enabled probe should be started
            verify(monitorServer).startMonitor(10);
            verify(monitorServer, never()).startMonitor(11);
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
