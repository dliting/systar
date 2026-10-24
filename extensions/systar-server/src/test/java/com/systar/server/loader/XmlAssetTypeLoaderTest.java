package com.systar.server.loader;

import com.systar.data.entity.AssetTypeConfigEntity;
import com.systar.data.mapper.AssetTypeConfigMapper;
import com.systar.monitor.asset.AssetException;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.type.*;
import com.systar.server.plugin.DriverPluginLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class XmlAssetTypeLoaderTest {

    private AssetTypeConfigMapper mapper;
    private XmlAssetTypeLoader loader;
    private AssetStore store;

    @BeforeEach
    void setUp() {
        mapper = mock(AssetTypeConfigMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        loader = new XmlAssetTypeLoader(mapper, new DriverPluginLoader("./target/no-plugins"));
        store = new AssetStore();
    }

    @Test
    @DisplayName("Loads all Service types (13)")
    void loadsServiceTypes() {
        loader.load(store);

        List<String> names = store.getServiceTypes().getAll()
                .stream().map(AssetType::getName).toList();

        List<String> expected = List.of(
                "ModbusMaster", "ModbusTcpMaster", "SimulateService",
                "SiemensService", "SnmpService", "OpcUaService",
                "WeatherService", "EnvironmentalService", "InputService",
                "UpsService", "WsService", "TcpIpService", "MqttService"
        );
        assertThat(names).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("Loads all Probe types (24)")
    void loadsProbeTypes() {
        loader.load(store);

        List<String> names = store.getProbeTypes().getAll()
                .stream().map(AssetType::getName).toList();

        List<String> expected = List.of(
                "ModbusFloatFC3", "ModbusIntFC3", "ModbusBoolFC1",
                "SimulateFloat", "SimulateInt",
                "SiemensFloat", "SnmpStr", "SnmpFloat", "SnmpBool", "SnmpInt",
                "SnmpInterface", "SnmpInterfaceRecvSpeed", "SnmpInterfaceSendSpeed",
                "SnmpInterfaceUpState", "SnmpInterfaceInLossRate", "SnmpInterfaceOutLossRate",
                "OpcUaFloat",
                "WeatherTemp", "EnvironmentalFloat", "InputValue",
                "UpsValue", "WsProbe", "TcpIpProbe", "MqttValue"
        );
        assertThat(names).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("Loads Control types (4)")
    void loadsControlTypes() {
        loader.load(store);

        List<String> names = store.getControlTypes().getAll()
                .stream().map(AssetType::getName).toList();

        List<String> expected = List.of(
                "ModbusInt16FC3FC6", "ModbusBoolFC3FC6",
                "OpcUaBoolControl", "OpcUaFloatControl"
        );
        assertThat(names).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("Loads Device types")
    void loadsDeviceTypes() {
        loader.load(store);

        assertThat(store.getDeviceTypes().getAll()).hasSize(1);
    }

    @Test
    @DisplayName("Loading twice replaces type registrations instead of failing on duplicates")
    void loadTwiceIsIdempotent() {
        loader.load(store);
        int serviceCount = store.getServiceTypes().size();

        loader.load(store);

        assertThat(store.getServiceTypes().size()).isEqualTo(serviceCount);
        assertThat(store.getDeviceTypes().find("GenericDevice")).isNotNull();
    }

    @Test
    @DisplayName("Previously broken types now have correct JavaClass")
    void brokenTypesHaveJavaClass() {
        loader.load(store);

        Stream.of("SnmpService", "MqttService", "OpcUaService", "SiemensService",
                        "WsService", "TcpIpService", "UpsService")
                .forEach(name -> {
                    ServiceType type = store.getServiceTypes().find(name);
                    assertThat(type).as("Service type '%s' should be loaded", name).isNotNull();
                    assertThat(type.getRelatedClass())
                            .as("Service type '%s' should have JavaClass", name)
                            .isNotBlank();
                });

        Stream.of("SnmpStr", "MqttValue", "OpcUaFloat", "SiemensFloat",
                        "WsProbe", "TcpIpProbe", "UpsValue")
                .forEach(name -> {
                    ProbeType type = store.getProbeTypes().find(name);
                    assertThat(type).as("Probe type '%s' should be loaded", name).isNotNull();
                    assertThat(type.getRelatedClass())
                            .as("Probe type '%s' should have JavaClass", name)
                            .isNotBlank();
                });
    }

    @Test
    @DisplayName("Syncs type configs to database")
    void syncsToDatabase() {
        loader.load(store);

        verify(mapper, atLeastOnce()).insert(any(AssetTypeConfigEntity.class));
    }

    @Test
    @DisplayName("Probe types reference correct Service source")
    void probeSourceReferencesService() {
        loader.load(store);

        for (AssetType type : store.getProbeTypes().getAll()) {
            if (type instanceof MonitorType mt && mt.getSource() != null) {
                ServiceType source = store.getServiceTypes().find(mt.getSource());
                assertThat(source)
                        .as("Probe '%s' references source '%s' which should exist",
                                type.getName(), mt.getSource())
                        .isNotNull();
            }
        }
    }

    @Test
    @DisplayName("Service types have PropertyList with expected properties")
    void serviceTypesHaveProperties() {
        loader.load(store);

        // SNMP
        ServiceType snmp = store.getServiceTypes().find("SnmpService");
        assertThat(snmp).isNotNull();
        assertThat(snmp.getProperties()).hasSize(5);
        assertThat(snmp.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("Host", "Port", "Community", "Version", "Timeout");

        // MQTT
        ServiceType mqtt = store.getServiceTypes().find("MqttService");
        assertThat(mqtt).isNotNull();
        assertThat(mqtt.getProperties()).hasSize(5);
        assertThat(mqtt.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("BrokerUrl", "ClientId", "Username", "Password", "Qos");

        // OpcUa — property names must match driver setters (SecurityPolicy, not SecurityMode)
        ServiceType opcua = store.getServiceTypes().find("OpcUaService");
        assertThat(opcua).isNotNull();
        assertThat(opcua.getProperties()).hasSize(4);
        assertThat(opcua.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("Host", "Port", "SecurityPolicy", "EndpointUrl");
    }

    @Test
    @DisplayName("Probe types have PropertyList with constraints")
    void probeTypesHaveConstraints() {
        loader.load(store);

        // SNMP probe
        ProbeType snmpProbe = store.getProbeTypes().find("SnmpStr");
        assertThat(snmpProbe).isNotNull();
        assertThat(snmpProbe.getProperties()).hasSize(1);
        assertThat(snmpProbe.getProperties().stream().findFirst().orElseThrow().getName()).isEqualTo("Oid");
    }

    @Test
    @DisplayName("Modbus probe PropertyList with RegisterAddr and constraints is preserved")
    void modbusPropertiesPreserved() {
        loader.load(store);

        ProbeType modbusFloat = store.getProbeTypes().find("ModbusFloatFC3");
        assertThat(modbusFloat).isNotNull();
        assertThat(modbusFloat.getProperties()).hasSize(1);
        AssetTypeProperty regAddr = modbusFloat.getProperties().stream().findFirst().orElseThrow();
        assertThat(regAddr.getName()).isEqualTo("RegisterAddr");
        assertThat(regAddr.getMin()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Total type count: 42")
    void totalTypeCount() {
        loader.load(store);

        int total = store.getDeviceTypes().getAll().size()
                + store.getServiceTypes().getAll().size()
                + store.getProbeTypes().getAll().size()
                + store.getControlTypes().getAll().size();

        assertThat(total).isEqualTo(42);
    }

    // ======================== Min/Max/MaxLength parsing ========================

    @Test
    @DisplayName("Parses Min/Max from DataType element")
    void parsesMinMaxFromDataType() {
        loader.load(store);

        ServiceType type = store.getServiceTypes().find("ModbusMaster");
        assertThat(type).isNotNull();

        AssetTypeProperty portProp = type.findProperty("MaxConnections");
        assertThat(portProp).isNotNull();
        assertThat(portProp.getMin()).isEqualTo(1.0);
        assertThat(portProp.getMax()).isEqualTo(255.0);
    }

    @Test
    @DisplayName("Every protocol service exposes Timeout with its driver's built-in default")
    void everyServiceExposesTunableTimeout() {
        loader.load(store);

        assertTimeoutDefault("ModbusTcpMaster", "5000");
        assertTimeoutDefault("SnmpService", "5000");
        assertTimeoutDefault("UpsService", "5000");
        assertTimeoutDefault("TcpIpService", "5000");
    }

    private void assertTimeoutDefault(String typeName, String expectedDefault) {
        ServiceType type = store.getServiceTypes().find(typeName);
        assertThat(type).as("Service type '%s' should be loaded", typeName).isNotNull();
        AssetTypeProperty timeout = type.findProperty("Timeout");
        assertThat(timeout)
                .as("Service type '%s' should expose a per-instance Timeout property", typeName)
                .isNotNull();
        assertThat(timeout.getDefaultValue())
                .as("Timeout default of '%s'", typeName)
                .isEqualTo(expectedDefault);
    }

    @Test
    @DisplayName("Modbus connection-pool default is 10, matching the ActiveService built-in")
    void modbusMaxConnectionsDefaultsToTen() {
        loader.load(store);

        AssetTypeProperty maxConn = store.getServiceTypes().find("ModbusMaster").findProperty("MaxConnections");
        assertThat(maxConn).isNotNull();
        assertThat(maxConn.getDefaultValue()).isEqualTo("10");
        assertThat(maxConn.getMin()).isEqualTo(1.0);
        assertThat(maxConn.getMax()).isEqualTo(255.0);
    }

    @Test
    @DisplayName("Parses Min/Max from DataType element (INT)")
    void parsesMaxLengthFromDataType() {
        loader.load(store);

        ServiceType type = store.getServiceTypes().find("ModbusMaster");
        AssetTypeProperty unitIdProp = type.findProperty("UnitId");
        assertThat(unitIdProp).isNotNull();
        assertThat(unitIdProp.getMin()).isEqualTo(0.0);
        assertThat(unitIdProp.getMax()).isEqualTo(247.0);
        assertThat(unitIdProp.getDataType()).isEqualTo(DataType.INT);
    }

    @Test
    @DisplayName("Parses Min only without Max")
    void parsesMinOnly() {
        loader.load(store);

        ProbeType type = store.getProbeTypes().find("SimulateFloat");
        assertThat(type).isNotNull();

        AssetTypeProperty periodProp = type.findProperty("Period");
        assertThat(periodProp).isNotNull();
        assertThat(periodProp.getMin()).isEqualTo(1.0);
        assertThat(periodProp.getMax()).isNull();
    }

    @Test
    @DisplayName("Properties without DataType constraints have null min/max/maxLength")
    void propertiesWithoutConstraintsHaveNullBounds() {
        loader.load(store);

        // SimulateFloat.MinValue has <DataType>FLOAT</DataType> with no Min/Max/MaxLength attrs
        ProbeType type = store.getProbeTypes().find("SimulateFloat");
        assertThat(type).isNotNull();

        AssetTypeProperty minValProp = type.findProperty("MinValue");
        assertThat(minValProp).isNotNull();
        assertThat(minValProp.getMin()).isNull();
        assertThat(minValProp.getMax()).isNull();
        assertThat(minValProp.getMaxLength()).isNull();
    }

    @Test
    @DisplayName("Inherited properties preserve min/max/maxLength from parent")
    void inheritedPropertiesPreserveConstraints() {
        loader.load(store);

        // ModbusTcpMaster extends ModbusMaster, should inherit MaxConnections with min/max
        ServiceType type = store.getServiceTypes().find("ModbusTcpMaster");
        assertThat(type).isNotNull();

        AssetTypeProperty maxConnProp = type.findProperty("MaxConnections");
        assertThat(maxConnProp).isNotNull();
        assertThat(maxConnProp.getMin()).isEqualTo(1.0);
        assertThat(maxConnProp.getMax()).isEqualTo(255.0);
    }

    @Test
    @DisplayName("AssetTypeProperty 4-arg constructor leaves min/max/maxLength null, infers viewType")
    void fourArgConstructorLeavesBoundsNull() {
        AssetTypeProperty prop = new AssetTypeProperty("test", DataType.INT, "0", "desc");

        assertThat(prop.getMin()).isNull();
        assertThat(prop.getMax()).isNull();
        assertThat(prop.getMaxLength()).isNull();
        assertThat(prop.getDataType()).isEqualTo(DataType.INT);
        assertThat(prop.getName()).isEqualTo("test");
        assertThat(prop.getViewType()).isEqualTo(ViewType.TEXTFIELD);
    }

    @Test
    @DisplayName("AssetTypeProperty 7-arg constructor sets all fields and infers viewType")
    void sevenArgConstructorSetsAllFields() {
        AssetTypeProperty prop = new AssetTypeProperty(
                "test", DataType.INT, "0", "desc", 1.0, 100.0, null);

        assertThat(prop.getName()).isEqualTo("test");
        assertThat(prop.getDataType()).isEqualTo(DataType.INT);
        assertThat(prop.getDefaultValue()).isEqualTo("0");
        assertThat(prop.getDescription()).isEqualTo("desc");
        assertThat(prop.getMin()).isEqualTo(1.0);
        assertThat(prop.getMax()).isEqualTo(100.0);
        assertThat(prop.getMaxLength()).isNull();
        assertThat(prop.isRequired()).isFalse();
        assertThat(prop.getViewType()).isEqualTo(ViewType.TEXTFIELD);
    }

    @Test
    @DisplayName("AssetTypeProperty copy constructor preserves min/max/maxLength and viewType")
    void copyConstructorPreservesBounds() {
        AssetTypeProperty original = new AssetTypeProperty(
                "src", DataType.STRING, "", "desc", null, null, 255);
        original.setRequired(true);
        original.setViewType(ViewType.TEXTAREA);

        AssetTypeProperty copy = new AssetTypeProperty(original);

        assertThat(copy.getName()).isEqualTo("src");
        assertThat(copy.getDataType()).isEqualTo(DataType.STRING);
        assertThat(copy.getMin()).isNull();
        assertThat(copy.getMax()).isNull();
        assertThat(copy.getMaxLength()).isEqualTo(255);
        assertThat(copy.isRequired()).isTrue();
        assertThat(copy.getViewType()).isEqualTo(ViewType.TEXTAREA);
    }

    @Test
    @DisplayName("Control type properties parse Min constraint")
    void controlTypePropertiesParseMin() {
        loader.load(store);

        ControlType type = store.getControlTypes().find("ModbusInt16FC3FC6");
        assertThat(type).isNotNull();

        AssetTypeProperty regProp = type.findProperty("InRegisterAddr");
        assertThat(regProp).isNotNull();
        assertThat(regProp.getMin()).isEqualTo(0.0);
    }

    // ======================== ViewType parsing ========================

    @Test
    @DisplayName("ModbusInt16FC3FC6 has ViewType.SLIDER from XML")
    void modbusIntControlHasSliderViewType() {
        loader.load(store);

        ControlType type = store.getControlTypes().find("ModbusInt16FC3FC6");
        assertThat(type).isNotNull();
        assertThat(type.getViewType()).isEqualTo(ViewType.SLIDER);
        assertThat(type.getDataType()).isEqualTo(DataType.INT);
    }

    @Test
    @DisplayName("ModbusBoolFC3FC6 has explicit ViewType.YESNO from XML")
    void modbusBoolControlHasYesNo() {
        loader.load(store);

        ControlType type = store.getControlTypes().find("ModbusBoolFC3FC6");
        assertThat(type).isNotNull();
        assertThat(type.getDataType()).isEqualTo(DataType.BOOLEAN);
        assertThat(type.getViewType()).isEqualTo(ViewType.YESNO);
    }

    @Test
    @DisplayName("All Control types have explicit ViewType")
    void allControlTypesHaveViewType() {
        loader.load(store);

        for (AssetType type : store.getControlTypes().getAll()) {
            assertThat(type instanceof MonitorType mt && mt.getViewType() != null)
                    .as("Control type '%s' should have ViewType", type.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("OPC UA float control has ViewType.SLIDER")
    void floatControlsHaveSlider() {
        loader.load(store);

        ControlType opcua = store.getControlTypes().find("OpcUaFloatControl");
        assertThat(opcua).isNotNull();
        assertThat(opcua.getViewType()).isEqualTo(ViewType.SLIDER);
    }

    @Test
    @DisplayName("Probe types without explicit ViewType infer from DataType")
    void probeTypesInferViewType() {
        loader.load(store);

        ProbeType floatProbe = store.getProbeTypes().find("SimulateFloat");
        assertThat(floatProbe).isNotNull();
        assertThat(floatProbe.getDataType()).isEqualTo(DataType.FLOAT);
        assertThat(floatProbe.getViewType()).isEqualTo(ViewType.TEXTFIELD);

        ProbeType intProbe = store.getProbeTypes().find("SimulateInt");
        assertThat(intProbe).isNotNull();
        assertThat(intProbe.getDataType()).isEqualTo(DataType.INT);
        assertThat(intProbe.getViewType()).isEqualTo(ViewType.TEXTFIELD);
    }

    @Test
    @DisplayName("inferViewType: BOOLEAN→YESNO, others→TEXTFIELD")
    void inferViewTypeMapping() {
        AssetTypeProperty boolProp = new AssetTypeProperty("b", DataType.BOOLEAN, "true", "bool");
        assertThat(boolProp.getViewType()).isEqualTo(ViewType.YESNO);

        AssetTypeProperty intProp = new AssetTypeProperty("i", DataType.INT, "0", "int");
        assertThat(intProp.getViewType()).isEqualTo(ViewType.TEXTFIELD);

        AssetTypeProperty floatProp = new AssetTypeProperty("f", DataType.FLOAT, "0.0", "float");
        assertThat(floatProp.getViewType()).isEqualTo(ViewType.TEXTFIELD);

        AssetTypeProperty strProp = new AssetTypeProperty("s", DataType.STRING, "", "str");
        assertThat(strProp.getViewType()).isEqualTo(ViewType.TEXTFIELD);

        AssetTypeProperty tsProp = new AssetTypeProperty("t", DataType.TIMESPAN, "1s", "ts");
        assertThat(tsProp.getViewType()).isEqualTo(ViewType.TEXTFIELD);

        AssetTypeProperty nullTypeProp = new AssetTypeProperty("n", null, "", "null");
        assertThat(nullTypeProp.getViewType()).isEqualTo(ViewType.TEXTFIELD);
    }

    @Test
    @DisplayName("Control type properties inherit ViewType inference")
    void controlPropertyViewTypeInference() {
        loader.load(store);

        ControlType type = store.getControlTypes().find("ModbusBoolFC3FC6");
        assertThat(type).isNotNull();

        // RegisterAddr is INT type, should infer TEXTFIELD
        AssetTypeProperty regProp = type.findProperty("InRegisterAddr");
        assertThat(regProp).isNotNull();
        assertThat(regProp.getViewType()).isEqualTo(ViewType.TEXTFIELD);
    }

    // ======================== DB sync versioning ========================

    @Nested
    @DisplayName("DB sync versioning: only real content changes bump the version")
    class DbSyncVersioning {

        @Test
        @DisplayName("reload with unchanged XML performs no insert/update/delete")
        void unchangedReloadSkipsUpdate() {
            loader.load(store);

            ArgumentCaptor<AssetTypeConfigEntity> captor =
                    ArgumentCaptor.forClass(AssetTypeConfigEntity.class);
            verify(mapper, atLeastOnce()).insert(captor.capture());
            List<AssetTypeConfigEntity> insertedRows = captor.getAllValues();

            clearInvocations(mapper);
            when(mapper.selectList(any())).thenReturn(insertedRows);

            loader.load(new AssetStore());

            verify(mapper, never()).insert(any(AssetTypeConfigEntity.class));
            verify(mapper, never()).updateById(any(AssetTypeConfigEntity.class));
            verify(mapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("changed caption bumps the version exactly once for that type")
        void changedCaptionBumpsVersion() {
            loader.load(store);

            ArgumentCaptor<AssetTypeConfigEntity> captor =
                    ArgumentCaptor.forClass(AssetTypeConfigEntity.class);
            verify(mapper, atLeastOnce()).insert(captor.capture());
            List<AssetTypeConfigEntity> rows = captor.getAllValues();
            rows.forEach(r -> {
                if ("ModbusMaster".equals(r.getTypeName())) r.setCaption("STALE");
            });

            clearInvocations(mapper);
            when(mapper.selectList(any())).thenReturn(rows);

            loader.load(new AssetStore());

            ArgumentCaptor<AssetTypeConfigEntity> updated =
                    ArgumentCaptor.forClass(AssetTypeConfigEntity.class);
            verify(mapper, times(1)).updateById(updated.capture());
            assertThat(updated.getValue().getTypeName()).isEqualTo("ModbusMaster");
            assertThat(updated.getValue().getCaption()).isEqualTo("Modbus Master");
            assertThat(updated.getValue().getVersion()).isEqualTo(2);
            verify(mapper, never()).insert(any(AssetTypeConfigEntity.class));
        }

        @Test
        @DisplayName("DB rows absent from XML are deleted exactly once (obsolete cleanup)")
        void obsoleteTypeDeletedFromDb() {
            AssetTypeConfigEntity orphan = new AssetTypeConfigEntity();
            orphan.setId(999L);
            orphan.setKind("SERVICE");
            orphan.setTypeName("GoneService");
            orphan.setCaption("gone");
            orphan.setVersion(1);

            loader.load(store);

            ArgumentCaptor<AssetTypeConfigEntity> captor =
                    ArgumentCaptor.forClass(AssetTypeConfigEntity.class);
            verify(mapper, atLeastOnce()).insert(captor.capture());
            List<AssetTypeConfigEntity> rows = new ArrayList<>(captor.getAllValues());
            rows.add(orphan);

            clearInvocations(mapper);
            when(mapper.selectList(any())).thenReturn(rows);

            loader.load(new AssetStore());

            verify(mapper, times(1)).deleteById(999L);
        }

        @Test
        @DisplayName("null version in a changed DB row is treated as 0, not an NPE")
        void nullVersionRowHandled() {
            loader.load(store);

            ArgumentCaptor<AssetTypeConfigEntity> captor =
                    ArgumentCaptor.forClass(AssetTypeConfigEntity.class);
            verify(mapper, atLeastOnce()).insert(captor.capture());
            List<AssetTypeConfigEntity> rows = captor.getAllValues();
            rows.forEach(r -> {
                if ("ModbusMaster".equals(r.getTypeName())) {
                    r.setCaption("STALE");
                    r.setVersion(null);
                }
            });

            clearInvocations(mapper);
            when(mapper.selectList(any())).thenReturn(rows);

            loader.load(new AssetStore());

            ArgumentCaptor<AssetTypeConfigEntity> updated =
                    ArgumentCaptor.forClass(AssetTypeConfigEntity.class);
            verify(mapper, times(1)).updateById(updated.capture());
            assertThat(updated.getValue().getTypeName()).isEqualTo("ModbusMaster");
            assertThat(updated.getValue().getVersion()).isEqualTo(1);
        }
    }

    // ======================== plugin-sourced type XMLs ========================

    @Nested
    @DisplayName("Directory scanning and extra scan paths")
    class DirectoryScan {

        @TempDir
        Path tempDir;

        /** Plugin loader rooted at tempDir; one plugin "extra" holds the fixture XMLs. */
        private XmlAssetTypeLoader extraLoader() {
            DriverPluginLoader pluginLoader = new DriverPluginLoader(tempDir.toString());
            pluginLoader.afterPropertiesSet();
            return new XmlAssetTypeLoader(mapper, pluginLoader);
        }

        private void writeXml(String fileName, String content) throws Exception {
            Path pkg = Files.createDirectories(
                    tempDir.resolve("extra/com/systar/monitor/drivers/extra"));
            Files.writeString(pkg.resolve(fileName), content);
        }

        @Test
        @DisplayName("plugin-sourced type contributes alongside the built-ins")
        void discoversTypeFromPluginSource() throws Exception {
            writeXml("private-services.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Services>
                        <Service Name="MyPrivateService" Caption="私有服务">
                            <JavaClass>com.systar.monitor.drivers.simulate.SimulateService</JavaClass>
                        </Service>
                    </Services>
                    """);

            extraLoader().load(store);

            ServiceType privateType = store.getServiceTypes().find("MyPrivateService");
            assertThat(privateType).as("plugin-sourced type should be registered").isNotNull();
            assertThat(privateType.getRelatedClass())
                    .isEqualTo("com.systar.monitor.drivers.simulate.SimulateService");
            assertThat(store.getServiceTypes().find("ModbusMaster"))
                    .as("built-in types must still load from default patterns").isNotNull();
        }

        @Test
        @DisplayName("unknown root element fails fast naming the file and the legal roots")
        void unknownRootElementFailsFast() throws Exception {
            writeXml("bad-root.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FancyList>
                        <Service Name="X"/>
                    </FancyList>
                    """);

            assertThatThrownBy(() -> extraLoader().load(store))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("bad-root.xml")
                    .hasMessageContaining("Services");
        }

        @Test
        @DisplayName("duplicate type name across sources throws (no override semantics)")
        void duplicateTypeNameAcrossPathsThrows() throws Exception {
            writeXml("dup-services.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Services>
                        <Service Name="ModbusMaster" Caption="重复类型"/>
                    </Services>
                    """);

            assertThatThrownBy(() -> extraLoader().load(store))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("ModbusMaster");
        }

        @Test
        @DisplayName("missing plugin root resolves to nothing and is not an error")
        void missingPluginRootIgnored() {
            DriverPluginLoader pluginLoader = new DriverPluginLoader(tempDir.resolve("nope").toString());
            pluginLoader.afterPropertiesSet();
            XmlAssetTypeLoader l = new XmlAssetTypeLoader(mapper, pluginLoader);

            assertThatCode(() -> l.load(store)).doesNotThrowAnyException();
            assertThat(store.getServiceTypes().find("ModbusMaster"))
                    .as("built-in types still load when the plugin root is empty")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Plugin-sourced types and failure semantics")
    class PluginSources {

        @TempDir
        Path root;

        private XmlAssetTypeLoader loaderWithFixturePlugins() throws Exception {
            // plugin "p1": service type + probe type whose Source exists
            Path p1 = Files.createDirectories(root.resolve("p1/com/systar/monitor/drivers/p1"));
            Files.writeString(p1.resolve("p1-services.xml"),
                    "<Services><Service Name=\"P1Svc\"><JavaClass>com.systar.monitor.drivers.p1.P1Service</JavaClass></Service></Services>");
            Files.writeString(p1.resolve("p1-probes.xml"),
                    "<ProbeList><Probe Name=\"P1Probe\"><Source>P1Svc</Source><DataType>FLOAT</DataType></Probe></ProbeList>");
            // plugin "p2": probe type whose Source service type is absent (XML-only dangling)
            Path p2 = Files.createDirectories(root.resolve("p2/com/systar/monitor/drivers/p2"));
            Files.writeString(p2.resolve("p2-probes.xml"),
                    "<ProbeList><Probe Name=\"P2Probe\"><Source>MissingSvc</Source><DataType>FLOAT</DataType></Probe>"
                            + "<Probe Name=\"P2Ok\"><Source>P1Svc</Source><DataType>INT</DataType></Probe></ProbeList>");
            DriverPluginLoader pluginLoader = new DriverPluginLoader(root.toString());
            pluginLoader.afterPropertiesSet();
            return new XmlAssetTypeLoader(mapper, pluginLoader);
        }

        @Test
        @DisplayName("XML-only plugin with missing Source: that type WARN-skipped, siblings load")
        void danglingSourceSkipsOnlyThatType() throws Exception {
            AssetStore s = new AssetStore();
            loaderWithFixturePlugins().load(s);
            assertThat(s.getProbeTypes().find("P1Probe")).isNotNull();
            assertThat(s.getProbeTypes().find("P2Ok")).isNotNull();
            assertThat(s.getProbeTypes().find("P2Probe")).isNull();
        }

        @Test
        @DisplayName("Same type name from two plugins fails startup naming both sources")
        void duplicateTypeNameAcrossPluginsNamesBoth() throws Exception {
            for (String p : new String[]{"aaa", "bbb"}) {
                Path d = Files.createDirectories(root.resolve(p + "/com/systar/monitor/drivers/" + p));
                Files.writeString(d.resolve("s.xml"),
                        "<Services><Service Name=\"DupSvc\"/></Services>");
            }
            DriverPluginLoader pluginLoader = new DriverPluginLoader(root.toString());
            pluginLoader.afterPropertiesSet();
            XmlAssetTypeLoader l = new XmlAssetTypeLoader(mapper, pluginLoader);
            assertThatThrownBy(() -> l.load(new AssetStore()))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("DupSvc")
                    .hasMessageContaining("aaa")
                    .hasMessageContaining("bbb");
        }
    }
}
