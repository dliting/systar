package com.systar.monitor.asset;

import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.DeviceType;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.SpaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetStoreTest {

    private AssetStore store;
    private SpaceType rootType;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        rootType = new SpaceType("rootType");
        store.createRoot(rootType, "root");
    }

    // ---- createRoot ----

    @Test
    @DisplayName("createRoot creates a root Space node")
    void createRoot() {
        assertThat(store.getRoot()).isNotNull();
        assertThat(store.getRoot().getName()).isEqualTo("root");
        assertThat(store.getRoot().getId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("createRoot adds the root to the asset index")
    void createRootInIndex() {
        assertThat(store.findAsset(-1)).isNotNull();
    }

    // ---- addAsset ----

    @Test
    @DisplayName("addAsset adds to root when parentId is INVALID_ID")
    void addAssetToRoot() {
        Device dev = new Device();
        dev.init(new DeviceType("devType"), 10, "device1");

        store.addAsset(dev);

        assertThat(store.findAsset(10)).isSameAs(dev);
        assertThat(dev.getParent()).isSameAs(store.getRoot());
        assertThat(dev.getContext()).isSameAs(store);
    }

    @Test
    @DisplayName("addAsset adds to specified parent")
    void addAssetToParent() {
        Device dev = new Device();
        dev.init(new DeviceType("devType"), 10, "device1");
        store.addAsset(dev);

        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 11, "probe1");
        probe.setParentId(10);
        store.addAsset(probe);

        assertThat(probe.getParent()).isSameAs(dev);
    }

    @Test
    @DisplayName("addAsset rejects null")
    void addAssetRejectsNull() {
        assertThatThrownBy(() -> store.addAsset(null))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("addAsset rejects duplicate id")
    void addAssetRejectsDuplicate() {
        Device dev1 = new Device();
        dev1.init(new DeviceType("dt"), 10, "dev1");
        store.addAsset(dev1);

        Device dev2 = new Device();
        dev2.init(new DeviceType("dt2"), 10, "dev2");
        assertThatThrownBy(() -> store.addAsset(dev2))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("addAsset rejects non-existent parent")
    void addAssetRejectsMissingParent() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 11, "probe1");
        probe.setParentId(999);
        assertThatThrownBy(() -> store.addAsset(probe))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("Parent asset not found");
    }

    @Test
    @DisplayName("addAsset rejects non-compound parent")
    void addAssetRejectsNonCompoundParent() {
        Probe parentProbe = new Probe();
        parentProbe.init(new ProbeType("pt"), 10, "parentProbe");
        parentProbe.setParentId(Asset.INVALID_ID);
        // Manually add to store bypassing parent validation (simulating existing leaf asset)
        store.addAsset(parentProbe);

        Probe child = new Probe();
        child.init(new ProbeType("pt2"), 11, "child");
        child.setParentId(10);
        assertThatThrownBy(() -> store.addAsset(child))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("not a compound");
    }

    // ---- removeAsset ----

    @Test
    @DisplayName("removeAsset removes asset and detaches from parent")
    void removeAsset() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "device1");
        store.addAsset(dev);

        Asset<?> removed = store.removeAsset(10);
        assertThat(removed).isSameAs(dev);
        assertThat(store.findAsset(10)).isNull();
        assertThat(dev.getParent()).isNull();
    }

    @Test
    @DisplayName("removeAsset returns null for non-existent id")
    void removeAssetNotFound() {
        assertThat(store.removeAsset(999)).isNull();
    }

    // ---- findAsset ----

    @Test
    @DisplayName("findAsset returns null for non-existent id")
    void findAssetNotFound() {
        assertThat(store.findAsset(999)).isNull();
    }

    // ---- getAssetsByKind ----

    @Test
    @DisplayName("getAssetsByKind filters by kind")
    void getAssetsByKind() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "device1");
        store.addAsset(dev);

        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 11, "probe1");
        probe.setParentId(10);
        store.addAsset(probe);

        List<Asset<?>> devices = store.getAssetsByKind(AssetKind.DEVICE);
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).getKind()).isEqualTo(AssetKind.DEVICE);
    }

    // ---- getAssets ----

    @Test
    @DisplayName("getAssets returns all assets including root")
    void getAssets() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "device1");
        store.addAsset(dev);

        Collection<Asset<?>> all = store.getAssets();
        assertThat(all).hasSize(2); // root + device
    }

    // ---- getFullPath ----

    @Test
    @DisplayName("getFullPath builds path from root to asset")
    void getFullPath() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "floor1");
        store.addAsset(dev);

        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 11, "tempSensor");
        probe.setParentId(10);
        store.addAsset(probe);

        String path = store.getFullPath(probe);
        assertThat(path).isEqualTo("root->floor1->tempSensor");
    }

    @Test
    @DisplayName("getFullPath returns empty for null")
    void getFullPathNull() {
        assertThat(store.getFullPath(null)).isEmpty();
    }

    @Test
    @DisplayName("getFullPath for root returns just root name")
    void getFullPathRoot() {
        assertThat(store.getFullPath(store.getRoot())).isEqualTo("root");
    }

    // ---- clear ----

    @Test
    @DisplayName("clear removes all assets and root reference")
    void clear() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "device1");
        store.addAsset(dev);

        store.clear();
        assertThat(store.getRoot()).isNull();
        assertThat(store.getAssets()).isEmpty();
    }
}
