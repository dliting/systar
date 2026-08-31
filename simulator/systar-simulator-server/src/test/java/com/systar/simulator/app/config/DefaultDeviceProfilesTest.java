package com.systar.simulator.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.systar.simulator.config.ProfileParser;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.OpcUaAddress;
import com.systar.simulator.model.OpcUaEndpoint;
import com.systar.simulator.model.SimulatedDevice;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DefaultDeviceProfilesTest {

    private ProfileParser parser;

    @BeforeEach
    void setUp() {
        parser = new ProfileParser();
    }

    private InputStream loadProfile(String name) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("profiles/" + name);
        assertNotNull(is, "Profile resource not found: profiles/" + name);
        return is;
    }

    @Test
    @DisplayName("hvac-modbus.yml parses correctly")
    void hvacModbusProfile() throws IOException {
        List<SimulatedDevice> devices;
        try (InputStream is = loadProfile("hvac-modbus.yml")) {
            devices = parser.parse(is);
        }

        assertEquals(1, devices.size());
        SimulatedDevice dev = devices.get(0);
        assertEquals("ahu-01", dev.getId());
        assertEquals("AHU-01 Air Handler", dev.getName());
        assertEquals(com.systar.simulator.model.ProtocolType.MODBUS_TCP, dev.getProtocol());

        ModbusTcpEndpoint ep = (ModbusTcpEndpoint) dev.getEndpoint();
        assertEquals(55502, ep.getPort());
        assertEquals(1, ep.getUnitId());

        assertEquals(5, dev.getDataPoints().size());

        // Verify supply-temp is profile generator
        DataPoint supplyTemp = findDataPoint(dev, "supply-temp");
        assertNotNull(supplyTemp);
        assertTrue(supplyTemp.getGenerator() instanceof com.systar.simulator.generator.ProfileGenerator);

        // Verify return-temp is correlated generator
        DataPoint returnTemp = findDataPoint(dev, "return-temp");
        assertNotNull(returnTemp);
        assertTrue(returnTemp.getGenerator() instanceof com.systar.simulator.generator.CorrelatedGenerator);

        // Verify fan-running is coil address with bool type
        DataPoint fanRunning = findDataPoint(dev, "fan-running");
        assertNotNull(fanRunning);
        ModbusAddress fanAddr = (ModbusAddress) fanRunning.getAddress();
        assertEquals("coil", fanAddr.getRegisterType());
        assertEquals("bool", fanAddr.getDataType());
    }

    @Test
    @DisplayName("ups-modbus.yml parses correctly")
    void upsModbusProfile() throws IOException {
        List<SimulatedDevice> devices;
        try (InputStream is = loadProfile("ups-modbus.yml")) {
            devices = parser.parse(is);
        }

        assertEquals(1, devices.size());
        SimulatedDevice dev = devices.get(0);
        assertEquals("ups-01", dev.getId());
        assertEquals(2, ((ModbusTcpEndpoint) dev.getEndpoint()).getUnitId());

        assertEquals(5, dev.getDataPoints().size());

        // Verify battery-level is ramp generator
        DataPoint battery = findDataPoint(dev, "battery-level");
        assertNotNull(battery);
        assertTrue(battery.getGenerator() instanceof com.systar.simulator.generator.RampGenerator);

        // Verify on-battery is coil with fixed false
        DataPoint onBattery = findDataPoint(dev, "on-battery");
        assertNotNull(onBattery);
        ModbusAddress addr = (ModbusAddress) onBattery.getAddress();
        assertEquals("coil", addr.getRegisterType());
    }

    @Test
    @DisplayName("weather-opcua.yml parses correctly")
    void weatherOpcuaProfile() throws IOException {
        List<SimulatedDevice> devices;
        try (InputStream is = loadProfile("weather-opcua.yml")) {
            devices = parser.parse(is);
        }

        assertEquals(1, devices.size());
        SimulatedDevice dev = devices.get(0);
        assertEquals("weather-01", dev.getId());
        assertEquals(com.systar.simulator.model.ProtocolType.OPC_UA, dev.getProtocol());

        OpcUaEndpoint ep = (OpcUaEndpoint) dev.getEndpoint();
        assertEquals(55503, ep.getPort());
        assertEquals("systar-simulator", ep.getServerName());

        assertEquals(4, dev.getDataPoints().size());

        // Verify OPC UA address structure
        DataPoint outdoorTemp = findDataPoint(dev, "outdoor-temp");
        assertNotNull(outdoorTemp);
        OpcUaAddress addr = (OpcUaAddress) outdoorTemp.getAddress();
        assertEquals(2, addr.getNamespaceIndex());
        assertEquals("OutdoorTemperature", addr.getIdentifier());
        assertEquals(false, addr.isIntegerId());
    }

    @Test
    @DisplayName("pdu-modbus.yml parses correctly")
    void pduModbusProfile() throws IOException {
        List<SimulatedDevice> devices;
        try (InputStream is = loadProfile("pdu-modbus.yml")) {
            devices = parser.parse(is);
        }

        assertEquals(1, devices.size());
        SimulatedDevice dev = devices.get(0);
        assertEquals("pdu-01", dev.getId());
        assertEquals(3, ((ModbusTcpEndpoint) dev.getEndpoint()).getUnitId());

        assertEquals(5, dev.getDataPoints().size());

        // Verify power-total is correlated
        DataPoint power = findDataPoint(dev, "power-total");
        assertNotNull(power);
        assertTrue(power.getGenerator() instanceof com.systar.simulator.generator.CorrelatedGenerator);
    }

    @Test
    @DisplayName("all individual profiles collectively contain 4 devices")
    void allProfilesCollectively() throws IOException {
        List<InputStream> streams = new java.util.ArrayList<>();
        try {
            for (String name : java.util.List.of(
                    "hvac-modbus.yml", "ups-modbus.yml", "pdu-modbus.yml", "weather-opcua.yml")) {
                streams.add(loadProfile(name));
            }
            List<SimulatedDevice> devices = parser.parseAll(streams);
            assertEquals(4, devices.size());

            assertTrue(devices.stream().anyMatch(d -> d.getId().equals("ahu-01")));
            assertTrue(devices.stream().anyMatch(d -> d.getId().equals("ups-01")));
            assertTrue(devices.stream().anyMatch(d -> d.getId().equals("pdu-01")));
            assertTrue(devices.stream().anyMatch(d -> d.getId().equals("weather-01")));

            int totalDataPoints = devices.stream().mapToInt(d -> d.getDataPoints().size()).sum();
            assertEquals(19, totalDataPoints);
        } finally {
            for (InputStream s : streams) {
                try { s.close(); } catch (Exception ignored) {}
            }
        }
    }

    private DataPoint findDataPoint(SimulatedDevice dev, String id) {
        return dev.getDataPoints().stream()
                .filter(dp -> dp.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
