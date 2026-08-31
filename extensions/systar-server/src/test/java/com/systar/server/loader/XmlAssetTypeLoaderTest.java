package com.systar.server.loader;

import com.systar.data.entity.AssetTypeConfigEntity;
import com.systar.data.mapper.AssetTypeConfigMapper;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.type.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
        loader = new XmlAssetTypeLoader(mapper);
        store = new AssetStore();
    }

    @Test
    @DisplayName("Loads all 15 Service types from XML config files")
    void loadsServiceTypes() {
        loader.load(store);

        List<String> names = store.getServiceTypes().getAll()
                .stream().map(AssetType::getName).toList();

        assertThat(names).containsExactlyInAnyOrder(
                "ModbusMaster", "ModbusTcpMaster",
                "BACnetService", "IEC104Master", "SimulateService",
                "SiemensService", "SnmpService", "OpcUaService",
                "WeatherService", "EnvironmentalService", "InputService",
                "UpsService", "WsService", "TcpIpService", "MqttService"
        );
    }

    @Test
    @DisplayName("Loads all Probe types including new BACnet and SNMP variants")
    void loadsProbeTypes() {
        loader.load(store);

        List<String> names = store.getProbeTypes().getAll()
                .stream().map(AssetType::getName).toList();

        assertThat(names).containsExactlyInAnyOrder(
                "ModbusFloatFC3", "ModbusIntFC3", "ModbusBoolFC1",
                "BACnetBool", "BACnetFloat", "BACnetInt",
                "IEC104Float",
                "SimulateFloat", "SimulateInt",
                "SiemensFloat", "SnmpStr", "SnmpFloat", "SnmpBool", "SnmpInt",
                "SnmpInterface", "SnmpInterfaceRecvSpeed", "SnmpInterfaceSendSpeed",
                "SnmpInterfaceUpState", "SnmpInterfaceInLossRate", "SnmpInterfaceOutLossRate",
                "OpcUaFloat",
                "WeatherTemp", "EnvironmentalFloat", "InputValue",
                "UpsValue", "WsProbe", "TcpIpProbe", "MqttValue"
        );
    }

    @Test
    @DisplayName("Loads Control types from XML config files")
    void loadsControlTypes() {
        loader.load(store);

        List<String> names = store.getControlTypes().getAll()
                .stream().map(AssetType::getName).toList();

        assertThat(names).containsExactlyInAnyOrder(
                "ModbusInt16FC3FC6", "ModbusBoolFC3FC6",
                "BACnetBoolControl",
                "IEC104BoolControl", "IEC104FloatControl",
                "OpcUaBoolControl", "OpcUaFloatControl"
        );
    }

    @Test
    @DisplayName("Loads Space and Device types")
    void loadsSpaceAndDeviceTypes() {
        loader.load(store);

        assertThat(store.getSpaceTypes().getAll()).hasSize(1);
        assertThat(store.getDeviceTypes().getAll()).hasSize(1);
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

        // BACnet
        ServiceType bacnet = store.getServiceTypes().find("BACnetService");
        assertThat(bacnet).isNotNull();
        assertThat(bacnet.getProperties()).hasSize(3);
        assertThat(bacnet.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("Remotehost", "Remoteport", "DeviceId");

        // SNMP
        ServiceType snmp = store.getServiceTypes().find("SnmpService");
        assertThat(snmp).isNotNull();
        assertThat(snmp.getProperties()).hasSize(4);
        assertThat(snmp.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("Host", "Port", "Community", "Version");

        // IEC104
        ServiceType iec = store.getServiceTypes().find("IEC104Master");
        assertThat(iec).isNotNull();
        assertThat(iec.getProperties()).hasSize(3);
        assertThat(iec.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("Host", "Port", "CommonAddrStr");

        // MQTT
        ServiceType mqtt = store.getServiceTypes().find("MqttService");
        assertThat(mqtt).isNotNull();
        assertThat(mqtt.getProperties()).hasSize(5);
        assertThat(mqtt.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("BrokerUrl", "ClientId", "Username", "Password", "Qos");
    }

    @Test
    @DisplayName("Probe types have PropertyList with constraints")
    void probeTypesHaveConstraints() {
        loader.load(store);

        // BACnetFloat probe
        ProbeType bacnetFloat = store.getProbeTypes().find("BACnetFloat");
        assertThat(bacnetFloat).isNotNull();
        assertThat(bacnetFloat.getProperties()).hasSize(3);
        AssetTypeProperty objectType = bacnetFloat.getProperties().stream()
                .filter(p -> "ObjectType".equals(p.getName())).findFirst().orElseThrow();
        assertThat(objectType.getMin()).isEqualTo(0.0);
        assertThat(objectType.getMax()).isEqualTo(30.0);
        assertThat(objectType.isRequired()).isTrue();

        // IEC104Float probe
        ProbeType iecFloat = store.getProbeTypes().find("IEC104Float");
        assertThat(iecFloat).isNotNull();
        assertThat(iecFloat.getProperties()).hasSize(2);
        AssetTypeProperty address = iecFloat.getProperties().stream()
                .filter(p -> "Address".equals(p.getName())).findFirst().orElseThrow();
        assertThat(address.getMin()).isEqualTo(0.0);
        assertThat(address.getMax()).isEqualTo(2147483647.0);

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
    @DisplayName("BACnetControls no longer references ModbusControl and has PropertyList")
    void bacnetControlFixedJavaClass() {
        loader.load(store);

        ControlType bacnetCtrl = store.getControlTypes().find("BACnetBoolControl");
        assertThat(bacnetCtrl).isNotNull();
        assertThat(bacnetCtrl.getRelatedClass())
                .doesNotContain("modbus.ModbusControl");
        assertThat(bacnetCtrl.getProperties()).hasSize(3);
        assertThat(bacnetCtrl.getProperties().stream().map(AssetTypeProperty::getName).toList())
                .containsExactlyInAnyOrder("ObjectType", "InstanceNumber", "PropertyIdentifier");
    }

    @Test
    @DisplayName("Total type count: 1+1+15+28+7 = 52 (with SNMP Interface probes)")
    void totalTypeCount() {
        loader.load(store);

        int total = store.getSpaceTypes().getAll().size()
                + store.getDeviceTypes().getAll().size()
                + store.getServiceTypes().getAll().size()
                + store.getProbeTypes().getAll().size()
                + store.getControlTypes().getAll().size();

        assertThat(total).isEqualTo(52);
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
    @DisplayName("IEC104 and OPC UA float controls have ViewType.SLIDER")
    void floatControlsHaveSlider() {
        loader.load(store);

        ControlType iec = store.getControlTypes().find("IEC104FloatControl");
        assertThat(iec).isNotNull();
        assertThat(iec.getViewType()).isEqualTo(ViewType.SLIDER);

        ControlType opcua = store.getControlTypes().find("OpcUaFloatControl");
        assertThat(opcua).isNotNull();
        assertThat(opcua.getViewType()).isEqualTo(ViewType.SLIDER);
    }

    @Test
    @DisplayName("BACnet and IEC104 bool controls have ViewType.YESNO")
    void boolControlsHaveYesNo() {
        loader.load(store);

        ControlType bacnet = store.getControlTypes().find("BACnetBoolControl");
        assertThat(bacnet).isNotNull();
        assertThat(bacnet.getViewType()).isEqualTo(ViewType.YESNO);

        ControlType iec = store.getControlTypes().find("IEC104BoolControl");
        assertThat(iec).isNotNull();
        assertThat(iec.getViewType()).isEqualTo(ViewType.YESNO);
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
}
