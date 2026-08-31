package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetTypeManagerTest {

    private AssetTypeManager<AssetType> manager;

    @BeforeEach
    void setUp() {
        manager = new AssetTypeManager<>();
    }

    @Test
    @DisplayName("register and find by name")
    void registerAndFind() {
        AssetType type = new AssetType("tempSensor", AssetKind.PROBE);
        manager.register(type);

        assertThat(manager.find("tempSensor")).isSameAs(type);
    }

    @Test
    @DisplayName("find returns null for unknown name")
    void findUnknown() {
        assertThat(manager.find("unknown")).isNull();
    }

    @Test
    @DisplayName("register rejects null")
    void registerNull() {
        assertThatThrownBy(() -> manager.register(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("register rejects duplicate name")
    void registerDuplicate() {
        manager.register(new AssetType("dup", AssetKind.PROBE));
        assertThatThrownBy(() -> manager.register(new AssetType("dup", AssetKind.DEVICE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate type");
    }

    @Test
    @DisplayName("getAll returns all types in insertion order")
    void getAll() {
        AssetType t1 = new AssetType("a", AssetKind.SPACE);
        AssetType t2 = new AssetType("b", AssetKind.DEVICE);
        AssetType t3 = new AssetType("c", AssetKind.PROBE);
        manager.register(t1);
        manager.register(t2);
        manager.register(t3);

        Collection<AssetType> all = manager.getAll();
        assertThat(all).containsExactly(t1, t2, t3);
    }

    @Test
    @DisplayName("getAll returns empty when no types registered")
    void getAllEmpty() {
        assertThat(manager.getAll()).isEmpty();
    }

    @Test
    @DisplayName("size returns correct count")
    void size() {
        assertThat(manager.size()).isZero();
        manager.register(new AssetType("a", AssetKind.SPACE));
        manager.register(new AssetType("b", AssetKind.DEVICE));
        assertThat(manager.size()).isEqualTo(2);
    }

    // ---- AssetType tests ----

    @Test
    @DisplayName("AssetType isCompound/isMonitor based on kind")
    void assetTypeFlags() {
        AssetType spaceType = new AssetType("s", AssetKind.SPACE);
        assertThat(spaceType.isCompound()).isTrue();
        assertThat(spaceType.isMonitor()).isFalse();

        AssetType probeType = new AssetType("p", AssetKind.PROBE);
        assertThat(probeType.isCompound()).isFalse();
        assertThat(probeType.isMonitor()).isTrue();
    }

    @Test
    @DisplayName("AssetType with null kind returns false for both flags")
    void assetTypeNullKind() {
        AssetType type = new AssetType();
        assertThat(type.isCompound()).isFalse();
        assertThat(type.isMonitor()).isFalse();
    }

    @Test
    @DisplayName("AssetType getDataType returns first property type")
    void getDataType() {
        AssetType type = new AssetType("t", AssetKind.PROBE);
        type.addProperty(new AssetTypeProperty("temp", DataType.FLOAT, "0.0", "temp"));
        assertThat(type.getDataType()).isEqualTo(DataType.FLOAT);
    }

    @Test
    @DisplayName("AssetType getDataType returns null when no properties")
    void getDataTypeNoProperties() {
        AssetType type = new AssetType("t", AssetKind.PROBE);
        assertThat(type.getDataType()).isNull();
    }

    // ---- Specific type constructors ----

    @Test
    @DisplayName("SpaceType sets kind to SPACE")
    void spaceTypeKind() {
        SpaceType st = new SpaceType("mySpace");
        assertThat(st.getKind()).isEqualTo(AssetKind.SPACE);
    }

    @Test
    @DisplayName("DeviceType sets kind to DEVICE")
    void deviceTypeKind() {
        DeviceType dt = new DeviceType("myDevice");
        assertThat(dt.getKind()).isEqualTo(AssetKind.DEVICE);
    }

    @Test
    @DisplayName("ProbeType sets kind to PROBE")
    void probeTypeKind() {
        ProbeType pt = new ProbeType("myProbe");
        assertThat(pt.getKind()).isEqualTo(AssetKind.PROBE);
    }

    @Test
    @DisplayName("ControlType sets kind to CONTROL")
    void controlTypeKind() {
        ControlType ct = new ControlType("myControl");
        assertThat(ct.getKind()).isEqualTo(AssetKind.CONTROL);
    }

    @Test
    @DisplayName("ServiceType sets kind to SERVICE")
    void serviceTypeKind() {
        ServiceType st = new ServiceType("myService");
        assertThat(st.getKind()).isEqualTo(AssetKind.SERVICE);
    }

    // ---- Space and Device concrete asset tests ----

    @Test
    @DisplayName("Space kind is SPACE")
    void spaceKind() {
        Space space = new Space();
        space.init(new SpaceType("st"), 1, "s1");
        assertThat(space.getKind()).isEqualTo(AssetKind.SPACE);
    }

    @Test
    @DisplayName("Device kind is DEVICE")
    void deviceKind() {
        Device device = new Device();
        device.init(new DeviceType("dt"), 1, "d1");
        assertThat(device.getKind()).isEqualTo(AssetKind.DEVICE);
    }

    // ---- DataType enum ----

    @Test
    @DisplayName("DataType has all expected values")
    void dataTypeValues() {
        assertThat(DataType.values()).containsExactly(
                DataType.INT, DataType.FLOAT, DataType.BOOLEAN, DataType.STRING, DataType.TIMESPAN);
    }

    // ---- AssetTypeProperty ----

    @Test
    @DisplayName("AssetTypeProperty constructor sets fields")
    void propertyConstructor() {
        AssetTypeProperty prop = new AssetTypeProperty("p1", DataType.INT, "42", "test prop");
        assertThat(prop.getName()).isEqualTo("p1");
        assertThat(prop.getDataType()).isEqualTo(DataType.INT);
        assertThat(prop.getDefaultValue()).isEqualTo("42");
        assertThat(prop.getDescription()).isEqualTo("test prop");
    }
}
