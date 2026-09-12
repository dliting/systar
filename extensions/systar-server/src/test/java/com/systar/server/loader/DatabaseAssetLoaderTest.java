package com.systar.server.loader;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.server.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DatabaseAssetLoaderTest {

    private AssetRepository repo;
    private DatabaseAssetLoader loader;
    private AssetStore store;

    @BeforeEach
    void setUp() {
        repo = mock(AssetRepository.class);
        store = new AssetStore();
        VirtualProbeEngine engine = mock(VirtualProbeEngine.class);
        loader = new DatabaseAssetLoader(repo, engine);

        when(repo.findAllDevices()).thenReturn(Collections.emptyList());
        when(repo.findAllServices(any())).thenReturn(Collections.emptyList());
        when(repo.findAllProbes(any())).thenReturn(Collections.emptyList());
        when(repo.findAllControls(any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Loads devices into asset store")
    void loadsDevices() {
        Device device = createTestDevice(1, "floor1", "First Floor");
        when(repo.findAllDevices()).thenReturn(List.of(device));

        loader.load(store);

        Asset<?> asset = store.findAsset(1);
        assertThat(asset).isNotNull();
        assertThat(asset.getName()).isEqualTo("floor1");
        assertThat(asset.getCaption()).isEqualTo("First Floor");
        assertThat(asset.getKind()).isEqualTo(AssetKind.DEVICE);
    }

    @Test
    @DisplayName("Loads probes with source service binding")
    void loadsProbesWithSource() {
        var svc = new com.systar.monitor.asset.ActiveService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public MonitorConnection createConnection() { return null; }
        };
        svc.init(new ServiceType("test-svc"), 100, "modbus-svc");

        Probe probe = new Probe();
        probe.init(new ProbeType("test-probe"), 200, "temp1");
        probe.setCaption("Temperature");
        probe.setSource(svc);

        when(repo.findAllServices(any())).thenReturn(List.of(svc));
        when(repo.findAllProbes(any())).thenReturn(List.of(probe));

        loader.load(store);

        Asset<?> asset = store.findAsset(200);
        assertThat(asset).isNotNull();
        assertThat(asset.getKind()).isEqualTo(AssetKind.PROBE);
        assertThat(asset.getName()).isEqualTo("temp1");
        assertThat(asset).isInstanceOf(Probe.class);
    }

    @Test
    @DisplayName("Empty database produces an empty store (anchor is not an asset)")
    void emptyDb() {
        loader.load(store);
        assertThat(store.getAssets()).isEmpty();
        assertThat(store.getRoot()).isNotNull();
    }

    @Test
    @DisplayName("Tree hierarchy: Device top-level, probe attached to device")
    void buildsTreeHierarchy() {
        Device device = createTestDevice(10, "deviceA", null);

        var svc = new com.systar.monitor.asset.ActiveService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public MonitorConnection createConnection() { return null; }
        };
        svc.init(new ServiceType("test-svc"), 100, "svc");

        Probe probe = new Probe();
        probe.init(new ProbeType("test-probe"), 200, "temp1");
        probe.setParentId(10);
        probe.setSource(svc);

        when(repo.findAllDevices()).thenReturn(List.of(device));
        when(repo.findAllServices(any())).thenReturn(List.of(svc));
        when(repo.findAllProbes(any())).thenReturn(List.of(probe));

        loader.load(store);

        Asset<?> found = store.findAsset(200);
        assertThat(found).isNotNull();
        assertThat(found.getParent()).isNotNull();
        assertThat(found.getParent().getId()).isEqualTo(10);
    }

    @Test
    @DisplayName("Loads attributes after asset loading")
    void loadsAttributes() {
        when(repo.findAllDevices()).thenReturn(List.of(createTestDevice(1, "floor1", null)));
        loader.load(store);
        verify(repo).loadAllAttributes(any());
    }

    @Test
    @DisplayName("End-to-end: metadata loaded by repo flows into driver setter via bindProperties")
    void bindsDriverFieldsAfterAttributeLoad() {
        // Build a Probe whose ProbeType declares an Address property (INT).
        ProbeType probeType = new ProbeType("temp-probe");
        probeType.addProperty(new AssetTypeProperty("Address", DataType.INT, "0", "addr"));

        BindingProbe probe = new BindingProbe();
        probe.init(probeType, 300, "p1");

        when(repo.findAllProbes(any())).thenReturn(List.of(probe));
        // Simulate loadAllAttributes populating the metadata map (as it does in production
        // from t_asset_attribute rows).
        doAnswer(inv -> {
            probe.setMetadata("Address", "1234");
            return null;
        }).when(repo).loadAllAttributes(any());

        loader.load(store);

        // If DatabaseAssetLoader skipped bindProperties(), address would stay at 0.
        // This assertion guards the loader→bindProperties→setter chain.
        // (XML parsing and DB attribute loading are covered by their own tests.)
        assertThat(probe.getAddress()).isEqualTo(1234);
    }

    /** Probe subclass with a typed setter, used to verify bindProperties wiring.
     *  Must be {@code public} so reflective {@code setter.invoke} can access it,
     *  matching the visibility of real driver subclasses (BacnetControl, etc.). */
    public static class BindingProbe extends Probe {
        private int address;
        public int  getAddress()              { return address; }
        public void setAddress(int address)   { this.address = address; }
    }

    private Device createTestDevice(int id, String name, String caption) {
        Device device = new Device();
        device.init(new DeviceType("test-device"), id, name);
        device.setParentId(Asset.INVALID_ID);
        if (caption != null) device.setCaption(caption);
        return device;
    }
}
