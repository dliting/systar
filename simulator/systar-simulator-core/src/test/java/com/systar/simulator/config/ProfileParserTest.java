package com.systar.simulator.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.systar.simulator.generator.CorrelatedGenerator;
import com.systar.simulator.generator.DataGenerator;
import com.systar.simulator.generator.FixedGenerator;
import com.systar.simulator.generator.ProfileGenerator;
import com.systar.simulator.generator.RandomGenerator;
import com.systar.simulator.generator.RampGenerator;
import com.systar.simulator.generator.SineGenerator;
import com.systar.simulator.generator.StepGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.OpcUaAddress;
import com.systar.simulator.model.OpcUaEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ProfileParserTest {

    private ProfileParser parser;

    @BeforeEach
    void setUp() {
        parser = new ProfileParser();
    }

    private InputStream yaml(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    // --- Basic Modbus device parsing ---

    @Test
    @DisplayName("Parse basic Modbus TCP device from YAML")
    void parseBasicModbusDevice() {
        String yaml = """
                devices:
                  - id: "test-device"
                    name: "Test Device"
                    protocol: MODBUS_TCP
                    endpoint:
                      host: "0.0.0.0"
                      port: 55502
                      unitId: 1
                    dataPoints:
                      - id: "temp"
                        name: "Temperature"
                        address:
                          registerType: holding
                          offset: 0
                          dataType: float
                        generator:
                          type: fixed
                          value: 22.5
                      - id: "humidity"
                        name: "Humidity"
                        address:
                          registerType: holding
                          offset: 2
                          dataType: float
                        generator:
                          type: random
                          min: 30.0
                          max: 70.0
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));

        assertEquals(1, devices.size());

        SimulatedDevice device = devices.get(0);
        assertEquals("test-device", device.getId());
        assertEquals("Test Device", device.getName());
        assertEquals(ProtocolType.MODBUS_TCP, device.getProtocol());
        assertTrue(device.isEnabled());

        // Endpoint
        assertInstanceOf(ModbusTcpEndpoint.class, device.getEndpoint());
        ModbusTcpEndpoint ep = (ModbusTcpEndpoint) device.getEndpoint();
        assertEquals("0.0.0.0", ep.getHost());
        assertEquals(55502, ep.getPort());
        assertEquals(1, ep.getUnitId());

        // Data points
        assertEquals(2, device.getDataPoints().size());

        DataPoint temp = device.getDataPoints().get(0);
        assertEquals("temp", temp.getId());
        assertEquals("Temperature", temp.getName());

        assertInstanceOf(ModbusAddress.class, temp.getAddress());
        ModbusAddress tempAddr = (ModbusAddress) temp.getAddress();
        assertEquals("holding", tempAddr.getRegisterType());
        assertEquals(0, tempAddr.getOffset());
        assertEquals("float", tempAddr.getDataType());

        assertInstanceOf(FixedGenerator.class, temp.getGenerator());
        FixedGenerator tempGen = (FixedGenerator) temp.getGenerator();
        assertEquals(22.5, ((Number) tempGen.getValue()).doubleValue(), 0.001);

        DataPoint humidity = device.getDataPoints().get(1);
        assertEquals("humidity", humidity.getId());
        assertEquals("Humidity", humidity.getName());

        assertInstanceOf(ModbusAddress.class, humidity.getAddress());
        ModbusAddress humAddr = (ModbusAddress) humidity.getAddress();
        assertEquals("holding", humAddr.getRegisterType());
        assertEquals(2, humAddr.getOffset());
        assertEquals("float", humAddr.getDataType());

        assertInstanceOf(RandomGenerator.class, humidity.getGenerator());
        RandomGenerator humGen = (RandomGenerator) humidity.getGenerator();
        assertEquals(30.0, humGen.getMin(), 0.001);
        assertEquals(70.0, humGen.getMax(), 0.001);
    }

    // --- OPC-UA device parsing ---

    @Test
    @DisplayName("Parse OPC-UA device from YAML")
    void parseOpcUaDevice() {
        String yaml = """
                devices:
                  - id: "opc-server"
                    name: "OPC UA Server"
                    protocol: OPC_UA
                    endpoint:
                      host: "localhost"
                      port: 4840
                      securityPolicy: "None"
                      serverName: "test-server"
                    dataPoints:
                      - id: "pressure"
                        name: "Pressure"
                        address:
                          namespaceIndex: 2
                          identifier: "Pressure"
                          integerId: false
                        generator:
                          type: sine
                          amplitude: 5.0
                          offset: 100.0
                          periodSeconds: 120
                          noiseStdDev: 0.5
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));

        assertEquals(1, devices.size());
        SimulatedDevice device = devices.get(0);
        assertEquals("opc-server", device.getId());
        assertEquals(ProtocolType.OPC_UA, device.getProtocol());

        assertInstanceOf(OpcUaEndpoint.class, device.getEndpoint());
        OpcUaEndpoint ep = (OpcUaEndpoint) device.getEndpoint();
        assertEquals("localhost", ep.getHost());
        assertEquals(4840, ep.getPort());
        assertEquals("None", ep.getSecurityPolicy());
        assertEquals("test-server", ep.getServerName());

        DataPoint dp = device.getDataPoints().get(0);
        assertInstanceOf(OpcUaAddress.class, dp.getAddress());
        OpcUaAddress addr = (OpcUaAddress) dp.getAddress();
        assertEquals(2, addr.getNamespaceIndex());
        assertEquals("Pressure", addr.getIdentifier());
        assertEquals(false, addr.isIntegerId());

        assertInstanceOf(SineGenerator.class, dp.getGenerator());
        SineGenerator gen = (SineGenerator) dp.getGenerator();
        assertEquals(5.0, gen.getAmplitude(), 0.001);
        assertEquals(100.0, gen.getOffset(), 0.001);
        assertEquals(120.0, gen.getPeriodSeconds(), 0.001);
        assertEquals(0.5, gen.getNoiseStdDev(), 0.001);
    }

    // --- All generator types ---

    @Test
    @DisplayName("Parse step generator")
    void parseStepGenerator() {
        String yaml = """
                devices:
                  - id: "step-dev"
                    name: "Step Device"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints:
                      - id: "val"
                        name: "Step Value"
                        address:
                          registerType: holding
                          offset: 0
                          dataType: int
                        generator:
                          type: step
                          values: [10, 20, 30]
                          intervalSeconds: 5
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        DataGenerator gen = devices.get(0).getDataPoints().get(0).getGenerator();
        assertInstanceOf(StepGenerator.class, gen);
        StepGenerator sg = (StepGenerator) gen;
        assertEquals(3, sg.getValues().size());
        assertEquals(5.0, sg.getIntervalSeconds(), 0.001);
    }

    @Test
    @DisplayName("Parse ramp generator")
    void parseRampGenerator() {
        String yaml = """
                devices:
                  - id: "ramp-dev"
                    name: "Ramp Device"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints:
                      - id: "val"
                        name: "Ramp Value"
                        address:
                          registerType: holding
                          offset: 0
                          dataType: int
                        generator:
                          type: ramp
                          start: 0
                          end: 100
                          durationSeconds: 30
                          loop: false
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        DataGenerator gen = devices.get(0).getDataPoints().get(0).getGenerator();
        assertInstanceOf(RampGenerator.class, gen);
        RampGenerator rg = (RampGenerator) gen;
        assertEquals(0.0, rg.getStart(), 0.001);
        assertEquals(100.0, rg.getEnd(), 0.001);
        assertEquals(30.0, rg.getDurationSeconds(), 0.001);
        assertEquals(false, rg.isLoop());
    }

    @Test
    @DisplayName("Parse profile generator")
    void parseProfileGenerator() {
        String yaml = """
                devices:
                  - id: "prof-dev"
                    name: "Profile Device"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints:
                      - id: "val"
                        name: "Profile Value"
                        address:
                          registerType: holding
                          offset: 0
                          dataType: float
                        generator:
                          type: profile
                          segments:
                            - time: "06:00"
                              value: 20
                            - time: "12:00"
                              value: 35
                            - time: "18:00"
                              value: 25
                          noiseStdDev: 0.5
                          interpolation: step
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        DataGenerator gen = devices.get(0).getDataPoints().get(0).getGenerator();
        assertInstanceOf(ProfileGenerator.class, gen);
        ProfileGenerator pg = (ProfileGenerator) gen;
        assertEquals(3, pg.getSegments().size());
        assertEquals(0.5, pg.getNoiseStdDev(), 0.001);
        assertEquals("step", pg.getInterpolation());
    }

    @Test
    @DisplayName("Parse correlated generator")
    void parseCorrelatedGenerator() {
        String yaml = """
                devices:
                  - id: "corr-dev"
                    name: "Correlated Device"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints:
                      - id: "base"
                        name: "Base"
                        address:
                          registerType: holding
                          offset: 0
                          dataType: float
                        generator:
                          type: sine
                          amplitude: 10
                          offset: 50
                          periodSeconds: 60
                      - id: "derived"
                        name: "Derived"
                        address:
                          registerType: holding
                          offset: 2
                          dataType: float
                        generator:
                          type: correlated
                          expression: "base * 1.8 + 32"
                          references:
                            base: "base"
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        assertEquals(2, devices.get(0).getDataPoints().size());

        DataGenerator gen = devices.get(0).getDataPoints().get(1).getGenerator();
        assertInstanceOf(CorrelatedGenerator.class, gen);
        CorrelatedGenerator cg = (CorrelatedGenerator) gen;
        assertEquals("base * 1.8 + 32", cg.getExpression());
        assertEquals(1, cg.getReferences().size());
        assertEquals("base", cg.getReferences().get("base"));
    }

    // --- Multiple devices ---

    @Test
    @DisplayName("Parse multiple devices from YAML")
    void parseMultipleDevices() {
        String yaml = """
                devices:
                  - id: "dev-1"
                    name: "Device One"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints: []
                  - id: "dev-2"
                    name: "Device Two"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 503
                      unitId: 1
                    dataPoints: []
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        assertEquals(2, devices.size());
        assertEquals("dev-1", devices.get(0).getId());
        assertEquals("dev-2", devices.get(1).getId());
    }

    // --- parseAll ---

    @Test
    @DisplayName("parseAll merges multiple streams")
    void parseAllMergesStreams() {
        String yaml1 = """
                devices:
                  - id: "dev-a"
                    name: "Device A"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints: []
                """;
        String yaml2 = """
                devices:
                  - id: "dev-b"
                    name: "Device B"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 503
                      unitId: 1
                    dataPoints: []
                """;

        List<SimulatedDevice> devices = parser.parseAll(
                List.of(yaml(yaml1), yaml(yaml2)));
        assertEquals(2, devices.size());
        assertEquals("dev-a", devices.get(0).getId());
        assertEquals("dev-b", devices.get(1).getId());
    }

    // --- Error cases ---

    @Test
    @DisplayName("Missing 'devices' key throws IllegalArgumentException")
    void missingDevicesKey() {
        String yaml = "foo: bar\n";
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(yaml(yaml)));
    }

    @Test
    @DisplayName("Unknown generator type throws IllegalArgumentException")
    void unknownGeneratorType() {
        String yaml = """
                devices:
                  - id: "dev"
                    name: "Dev"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints:
                      - id: "dp"
                        name: "DP"
                        address:
                          registerType: holding
                          offset: 0
                          dataType: int
                        generator:
                          type: unknown_type
                """;
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(yaml(yaml)));
    }

    @Test
    @DisplayName("Unknown protocol type throws IllegalArgumentException")
    void unknownProtocolType() {
        String yaml = """
                devices:
                  - id: "dev"
                    name: "Dev"
                    protocol: UNKNOWN
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints: []
                """;
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(yaml(yaml)));
    }

    @Test
    @DisplayName("Missing required device id throws IllegalArgumentException")
    void missingDeviceId() {
        String yaml = """
                devices:
                  - name: "No ID Device"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    dataPoints: []
                """;
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(yaml(yaml)));
    }

    @Test
    @DisplayName("Disabled device has enabled=false")
    void disabledDevice() {
        String yaml = """
                devices:
                  - id: "disabled-dev"
                    name: "Disabled"
                    protocol: MODBUS_TCP
                    endpoint:
                      port: 502
                      unitId: 1
                    enabled: false
                    dataPoints: []
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        assertEquals(1, devices.size());
        assertEquals(false, devices.get(0).isEnabled());
    }

    @Test
    @DisplayName("Default endpoint values applied when host/port/unitId omitted")
    void defaultEndpointValues() {
        String yaml = """
                devices:
                  - id: "defaults-dev"
                    name: "Defaults"
                    protocol: MODBUS_TCP
                    endpoint: {}
                    dataPoints: []
                """;

        List<SimulatedDevice> devices = parser.parse(yaml(yaml));
        ModbusTcpEndpoint ep = (ModbusTcpEndpoint) devices.get(0).getEndpoint();
        assertEquals("0.0.0.0", ep.getHost());
        assertEquals(502, ep.getPort());
        assertEquals(1, ep.getUnitId());
    }
}
