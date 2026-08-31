package com.systar.simulator.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.systar.simulator.generator.RandomGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ProfileValidatorTest {

    private ProfileParser   parser;
    private ProfileValidator validator;

    @BeforeEach
    void setUp() {
        parser    = new ProfileParser();
        validator = new ProfileValidator();
    }

    private InputStream yaml(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    private List<SimulatedDevice> parse(String yaml) {
        return parser.parse(yaml(yaml));
    }

    // --- Valid profile passes ---

    @Test
    @DisplayName("Valid profile passes without exception")
    void validProfilePasses() {
        String yaml = """
                devices:
                  - id: "valid-dev"
                    name: "Valid Device"
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

        List<SimulatedDevice> devices = parse(yaml);
        assertDoesNotThrow(() -> validator.validate(devices));
    }

    // --- Duplicate device id ---

    @Test
    @DisplayName("Duplicate device id throws ValidationException")
    void duplicateDeviceIdThrows() {
        SimulatedDevice dev1 = new SimulatedDevice();
        dev1.setId("dup");
        dev1.setName("Device 1");
        dev1.setProtocol(ProtocolType.MODBUS_TCP);
        dev1.setEndpoint(makeModbusEndpoint(502, 1));

        SimulatedDevice dev2 = new SimulatedDevice();
        dev2.setId("dup");
        dev2.setName("Device 2");
        dev2.setProtocol(ProtocolType.MODBUS_TCP);
        dev2.setEndpoint(makeModbusEndpoint(503, 1));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(dev1, dev2)));
    }

    // --- Duplicate dataPoint id within device ---

    @Test
    @DisplayName("Duplicate dataPoint id within device throws ValidationException")
    void duplicateDataPointIdThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("dup-dp-dev");
        device.setName("Dup DP Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        DataPoint dp1 = new DataPoint();
        dp1.setId("same-id");
        dp1.setName("DP1");
        dp1.setAddress(new ModbusAddress("holding", 0, "int"));
        dp1.setGenerator(new FixedGenerator(10));

        DataPoint dp2 = new DataPoint();
        dp2.setId("same-id");
        dp2.setName("DP2");
        dp2.setAddress(new ModbusAddress("holding", 2, "int"));
        dp2.setGenerator(new FixedGenerator(20));

        device.setDataPoints(List.of(dp1, dp2));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Overlapping Modbus offsets ---

    @Test
    @DisplayName("Overlapping Modbus offsets throws ValidationException")
    void overlappingModbusOffsetsThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("overlap-dev");
        device.setName("Overlap Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        // float takes 2 registers: offset 0 covers 0-1, offset 1 covers 1-2 => overlap
        DataPoint dp1 = new DataPoint();
        dp1.setId("dp1");
        dp1.setName("DP1");
        dp1.setAddress(new ModbusAddress("holding", 0, "float"));
        dp1.setGenerator(new FixedGenerator(10.0));

        DataPoint dp2 = new DataPoint();
        dp2.setId("dp2");
        dp2.setName("DP2");
        dp2.setAddress(new ModbusAddress("holding", 1, "float"));
        dp2.setGenerator(new FixedGenerator(20.0));

        device.setDataPoints(List.of(dp1, dp2));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Unresolved correlated reference ---

    @Test
    @DisplayName("Unresolved correlated reference throws ValidationException")
    void unresolvedCorrelatedReferenceThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("unresolved-dev");
        device.setName("Unresolved Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        CorrelatedGenerator gen = new CorrelatedGenerator();
        gen.setExpression("missing * 2");
        gen.setReferences(java.util.Map.of("alias", "nonexistent-id"));

        DataPoint dp = new DataPoint();
        dp.setId("dp1");
        dp.setName("DP1");
        dp.setAddress(new ModbusAddress("holding", 0, "int"));
        dp.setGenerator(gen);

        device.setDataPoints(List.of(dp));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Circular correlated dependency ---

    @Test
    @DisplayName("Circular correlated dependency throws ValidationException")
    void circularCorrelatedDependencyThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("cycle-dev");
        device.setName("Cycle Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        // dp-a references dp-b, dp-b references dp-a
        CorrelatedGenerator genA = new CorrelatedGenerator();
        genA.setExpression("b * 2");
        genA.setReferences(java.util.Map.of("b", "dp-b"));

        CorrelatedGenerator genB = new CorrelatedGenerator();
        genB.setExpression("a + 1");
        genB.setReferences(java.util.Map.of("a", "dp-a"));

        DataPoint dpA = new DataPoint();
        dpA.setId("dp-a");
        dpA.setName("DP A");
        dpA.setAddress(new ModbusAddress("holding", 0, "int"));
        dpA.setGenerator(genA);

        DataPoint dpB = new DataPoint();
        dpB.setId("dp-b");
        dpB.setName("DP B");
        dpB.setAddress(new ModbusAddress("holding", 2, "int"));
        dpB.setGenerator(genB);

        device.setDataPoints(List.of(dpA, dpB));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Invalid port ---

    @Test
    @DisplayName("Invalid port number throws ValidationException")
    void invalidPortThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("bad-port-dev");
        device.setName("Bad Port Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        ModbusTcpEndpoint ep = new ModbusTcpEndpoint();
        ep.setPort(99999);
        ep.setUnitId(1);
        device.setEndpoint(ep);

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Duplicate unitId on same port ---

    @Test
    @DisplayName("Duplicate unitId on same Modbus port throws ValidationException")
    void duplicateUnitIdOnSamePortThrows() {
        SimulatedDevice dev1 = new SimulatedDevice();
        dev1.setId("dev1");
        dev1.setName("Device 1");
        dev1.setProtocol(ProtocolType.MODBUS_TCP);
        dev1.setEndpoint(makeModbusEndpoint(502, 1));

        SimulatedDevice dev2 = new SimulatedDevice();
        dev2.setId("dev2");
        dev2.setName("Device 2");
        dev2.setProtocol(ProtocolType.MODBUS_TCP);
        dev2.setEndpoint(makeModbusEndpoint(502, 1));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(dev1, dev2)));
    }

    // --- Missing required device fields ---

    @Test
    @DisplayName("Missing device name throws ValidationException")
    void missingDeviceNameThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("no-name");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Missing required dataPoint fields ---

    @Test
    @DisplayName("Missing dataPoint generator throws ValidationException")
    void missingDataPointGeneratorThrows() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("no-gen-dev");
        device.setName("No Gen Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        DataPoint dp = new DataPoint();
        dp.setId("dp1");
        dp.setName("DP1");
        dp.setAddress(new ModbusAddress("holding", 0, "int"));
        // generator is null

        device.setDataPoints(List.of(dp));

        assertThrows(ValidationException.class,
                () -> validator.validate(List.of(device)));
    }

    // --- Non-overlapping Modbus offsets pass ---

    @Test
    @DisplayName("Non-overlapping Modbus offsets pass validation")
    void nonOverlappingOffsetsPass() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("ok-offset-dev");
        device.setName("OK Offset Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setEndpoint(makeModbusEndpoint(502, 1));

        // float at offset 0 (2 registers), int at offset 2 (1 register) => no overlap
        DataPoint dp1 = new DataPoint();
        dp1.setId("dp1");
        dp1.setName("DP1");
        dp1.setAddress(new ModbusAddress("holding", 0, "float"));
        dp1.setGenerator(new FixedGenerator(10.0));

        DataPoint dp2 = new DataPoint();
        dp2.setId("dp2");
        dp2.setName("DP2");
        dp2.setAddress(new ModbusAddress("holding", 2, "int"));
        dp2.setGenerator(new FixedGenerator(20));

        device.setDataPoints(List.of(dp1, dp2));

        assertDoesNotThrow(() -> validator.validate(List.of(device)));
    }

    // --- Helpers ---

    private static ModbusTcpEndpoint makeModbusEndpoint(int port, int unitId) {
        ModbusTcpEndpoint ep = new ModbusTcpEndpoint();
        ep.setPort(port);
        ep.setUnitId(unitId);
        return ep;
    }
}
