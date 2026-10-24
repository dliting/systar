package com.systar.monitor.asset;

import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.DeviceType;
import com.systar.monitor.asset.type.ProbeType;
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

    @BeforeEach
    void setUp() {
        store = new AssetStore();
    }

    // ---- neutral anchor ----

    @Test
    @DisplayName("anchor exists, is compound and kind-neutral")
    void anchorIsNeutral() {
        assertThat(store.getRoot()).isNotNull();
        assertThat(store.getRoot().isCompound()).isTrue();
        assertThat(store.getRoot().getKind()).isNull();
        assertThatThrownBy(() -> store.getRoot().accept(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("anchor is NOT in the flat index — no id=-1 leak via getAssets/findAsset")
    void anchorNotInIndex() {
        assertThat(store.findAsset(store.getRoot().getId())).isNull();
        assertThat(store.getAssets()).isEmpty();
        assertThat(store.getAssetsByKind(AssetKind.DEVICE)).isEmpty();
    }

    // ---- addAsset ----

    @Test
    @DisplayName("addAsset attaches top-level asset (parentId=0) to the anchor")
    void addAssetToAnchor() {
        Device dev = new Device();
        dev.init(new DeviceType("devType"), 10, "device1");

        store.addAsset(dev);

        assertThat(store.findAsset(10)).isSameAs(dev);
        assertThat(dev.getParent()).isSameAs(store.getRoot());
        assertThat(dev.getContext()).isSameAs(store);
        assertThat(store.getAssets()).hasSize(1); // anchor excluded
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
    @DisplayName("addAsset rejects the anchor itself (id=-1 reserved) and leaves the store uncorrupted")
    void addAssetRejectsAnchor() {
        assertThatThrownBy(() -> store.addAsset(store.getRoot()))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("reserved for the tree anchor");

        assertThat(store.getAssets()).isEmpty();
        assertThat(store.findAsset(-1)).isNull();
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
    @DisplayName("getAssets returns real assets only (anchor excluded)")
    void getAssets() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "device1");
        store.addAsset(dev);

        Collection<Asset<?>> all = store.getAssets();
        assertThat(all).hasSize(1);
    }

    // ---- getFullPath ----

    @Test
    @DisplayName("getFullPath builds path without the anchor and without leading separator")
    void getFullPath() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "floor1");
        store.addAsset(dev);

        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 11, "tempSensor");
        probe.setParentId(10);
        store.addAsset(probe);

        assertThat(store.getFullPath(probe)).isEqualTo("floor1->tempSensor");
        assertThat(store.getFullPath(dev)).isEqualTo("floor1");
    }

    @Test
    @DisplayName("getFullPath returns empty for null and for the anchor itself")
    void getFullPathNullAndAnchor() {
        assertThat(store.getFullPath(null)).isEmpty();
        assertThat(store.getFullPath(store.getRoot())).isEmpty();
    }

    // ---- clear ----

    @Test
    @DisplayName("clear empties the index and re-creates a fresh anchor")
    void clear() {
        Device dev = new Device();
        dev.init(new DeviceType("dt"), 10, "device1");
        store.addAsset(dev);

        store.clear();
        assertThat(store.getRoot()).isNotNull();
        assertThat(store.getAssets()).isEmpty();

        Device dev2 = new Device();
        dev2.init(new DeviceType("dt2"), 20, "device2");
        store.addAsset(dev2);
        assertThat(store.getAssets()).hasSize(1);
    }
}
