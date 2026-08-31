package com.systar.simulator.app.protocol;

import com.systar.monitor.drivers.modbus.ModbusConnection;
import com.systar.simulator.generator.FixedGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test that uses Systar's actual {@link ModbusConnection}
 * client to verify the simulator's Modbus TCP slave server works correctly.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ModbusTcpRoundTripTest {

    private static final int TIMEOUT_MS = 3000;

    private ModbusTcpProtocolServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        SimulatedDevice device = new SimulatedDevice();
        device.setId("test-modbus");
        device.setName("Test Modbus Device");
        device.setProtocol(ProtocolType.MODBUS_TCP);

        ModbusTcpEndpoint endpoint = new ModbusTcpEndpoint();
        endpoint.setPort(port);
        endpoint.setUnitId(1);
        device.setEndpoint(endpoint);

        DataPoint floatPoint = new DataPoint();
        floatPoint.setId("temp");
        floatPoint.setName("Temperature");
        floatPoint.setAddress(new ModbusAddress(ModbusAddress.TYPE_HOLDING, 0, ModbusAddress.DATA_FLOAT));
        floatPoint.setGenerator(new FixedGenerator(22.5f));
        floatPoint.setCurrentValue(22.5f);
        device.getDataPoints().add(floatPoint);

        DataPoint boolPoint = new DataPoint();
        boolPoint.setId("fan");
        boolPoint.setName("Fan Running");
        boolPoint.setAddress(new ModbusAddress(ModbusAddress.TYPE_COIL, 0, ModbusAddress.DATA_BOOL));
        boolPoint.setGenerator(new FixedGenerator(true));
        boolPoint.setCurrentValue(true);
        device.getDataPoints().add(boolPoint);

        DataPoint intPoint = new DataPoint();
        intPoint.setId("pressure");
        intPoint.setName("Pressure");
        intPoint.setAddress(new ModbusAddress(ModbusAddress.TYPE_HOLDING, 10, ModbusAddress.DATA_INT));
        intPoint.setGenerator(new FixedGenerator(100));
        intPoint.setCurrentValue(100);
        device.getDataPoints().add(intPoint);

        server = new ModbusTcpProtocolServer();
        server.start(device);

        // Push initial values into the register bank
        server.updateValue("test-modbus", floatPoint.getAddress(), 22.5f);
        server.updateValue("test-modbus", boolPoint.getAddress(), true);
        server.updateValue("test-modbus", intPoint.getAddress(), 100);
    }

    @Test
    @DisplayName("read holding register returns correct float value")
    void readFloat() throws Exception {
        ModbusConnection conn = new ModbusConnection("localhost", port, 1, TIMEOUT_MS);
        try {
            conn.open();
            int[] regs = conn.readHoldingRegisters(0, 2);
            // Big-endian IEEE 754 float across two registers
            float value = Float.intBitsToFloat((regs[0] << 16) | (regs[1] & 0xFFFF));
            assertThat(value).isCloseTo(22.5f, org.assertj.core.data.Offset.offset(0.01f));
        } finally {
            conn.close();
        }
    }

    @Test
    @DisplayName("read coil returns correct boolean value")
    void readCoil() throws Exception {
        ModbusConnection conn = new ModbusConnection("localhost", port, 1, TIMEOUT_MS);
        try {
            conn.open();
            boolean[] coils = conn.readCoils(0, 1);
            assertThat(coils[0]).isTrue();
        } finally {
            conn.close();
        }
    }

    @Test
    @DisplayName("read coil returns false when not set")
    void readCoilFalse() throws Exception {
        ModbusConnection conn = new ModbusConnection("localhost", port, 1, TIMEOUT_MS);
        try {
            conn.open();
            // Coil at offset 1 was never written, should be false by default
            boolean[] coils = conn.readCoils(1, 1);
            assertThat(coils[0]).isFalse();
        } finally {
            conn.close();
        }
    }

    @Test
    @DisplayName("write single register updates value and can be read back")
    void writeRegister() throws Exception {
        ModbusConnection conn = new ModbusConnection("localhost", port, 1, TIMEOUT_MS);
        try {
            conn.open();
            conn.writeSingleRegister(10, 42);
            int[] regs = conn.readHoldingRegisters(10, 1);
            assertThat(regs[0]).isEqualTo(42);
        } finally {
            conn.close();
        }
    }

    @Test
    @DisplayName("write single coil updates value and can be read back")
    void writeCoil() throws Exception {
        ModbusConnection conn = new ModbusConnection("localhost", port, 1, TIMEOUT_MS);
        try {
            conn.open();
            conn.writeCoil(1, true);
            boolean[] coils = conn.readCoils(1, 1);
            assertThat(coils[0]).isTrue();

            conn.writeCoil(1, false);
            coils = conn.readCoils(1, 1);
            assertThat(coils[0]).isFalse();
        } finally {
            conn.close();
        }
    }

    @Test
    @DisplayName("read input registers after updateValue")
    void readInputRegister() throws Exception {
        // Write to input register via updateValue
        DataPoint inputPoint = new DataPoint();
        inputPoint.setId("input-volt");
        inputPoint.setAddress(new ModbusAddress(ModbusAddress.TYPE_INPUT, 0, ModbusAddress.DATA_INT));
        server.updateValue("test-modbus", inputPoint.getAddress(), 220);

        ModbusConnection conn = new ModbusConnection("localhost", port, 1, TIMEOUT_MS);
        try {
            conn.open();
            int[] regs = conn.readInputRegisters(0, 1);
            assertThat(regs[0]).isEqualTo(220);
        } finally {
            conn.close();
        }
    }

    @Test
    @DisplayName("wrong unit ID returns illegal data address exception")
    void wrongUnitId() throws Exception {
        // Unit ID 2 is not registered; the server should respond with exception
        ModbusConnection conn = new ModbusConnection("localhost", port, 2, TIMEOUT_MS);
        try {
            conn.open();
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
                conn.readHoldingRegisters(0, 1);
            });
        } finally {
            conn.close();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }
}
