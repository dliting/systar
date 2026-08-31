package com.systar.server.loader;

import com.systar.common.util.TimeSpan;
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
        store.createRoot(new SpaceType("root"), "root");

        when(repo.findAllSpaces()).thenReturn(Collections.emptyList());
        when(repo.findAllDevices()).thenReturn(Collections.emptyList());
        when(repo.findAllServices(any())).thenReturn(Collections.emptyList());
        when(repo.findAllProbes(any())).thenReturn(Collections.emptyList());
        when(repo.findAllControls(any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Loads spaces into asset store")
    void loadsSpaces() {
        Space space = createTestSpace(1, "floor1", "First Floor");
        when(repo.findAllSpaces()).thenReturn(List.of(space));

        loader.load(store);

        Asset<?> asset = store.findAsset(1);
        assertThat(asset).isNotNull();
        assertThat(asset.getName()).isEqualTo("floor1");
        assertThat(asset.getCaption()).isEqualTo("First Floor");
        assertThat(asset.getKind()).isEqualTo(AssetKind.SPACE);
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
    @DisplayName("Empty database produces no extra assets beyond root")
    void emptyDb() {
        loader.load(store);
        assertThat(store.getAssets()).hasSize(1);
        assertThat(store.getRoot()).isNotNull();
    }

    @Test
    @DisplayName("Tree hierarchy: Space -> Device -> children")
    void buildsTreeHierarchy() {
        Space space = createTestSpace(1, "floor1", null);
        Device device = new Device();
        device.init(new DeviceType("test-device"), 10, "deviceA");
        device.setParentId(1);

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

        when(repo.findAllSpaces()).thenReturn(List.of(space));
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
        when(repo.findAllSpaces()).thenReturn(List.of(createTestSpace(1, "floor1", null)));
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

    private Space createTestSpace(int id, String name, String caption) {
        Space space = new Space();
        space.init(new SpaceType("test-space"), id, name);
        if (caption != null) space.setCaption(caption);
        return space;
    }
}
