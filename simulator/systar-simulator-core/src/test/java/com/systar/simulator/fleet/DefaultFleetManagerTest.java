package com.systar.simulator.fleet;

import com.systar.simulator.generator.FixedGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.DataPointAddress;
import com.systar.simulator.model.DeviceStatus;
import com.systar.simulator.model.FaultType;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;
import com.systar.simulator.protocol.ProtocolServer;
import com.systar.simulator.protocol.ServerStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFleetManagerTest {

    private static final int TIMEOUT_SECONDS = 180;

    /** Recording mock that tracks which devices were started and stopped. */
    private static class RecordingProtocolServer implements ProtocolServer {

        private final ProtocolType        protocolType;
        private final List<String>         startedDeviceIds = new ArrayList<>();
        private final List<String>         stoppedDeviceIds = new ArrayList<>();
        private boolean                    closed = false;

        RecordingProtocolServer(ProtocolType protocolType) {
            this.protocolType = protocolType;
        }

        @Override
        public void start(SimulatedDevice device) {
            startedDeviceIds.add(device.getId());
        }

        @Override
        public void stop(String deviceId) {
            stoppedDeviceIds.add(deviceId);
        }

        @Override
        public void updateValue(String deviceId, DataPointAddress address, Object value) {
            // no-op for this mock
        }

        @Override
        public ServerStatus getStatus() {
            return new ServerStatus(true, startedDeviceIds.size() - stoppedDeviceIds.size(), 0);
        }

        @Override
        public void close() {
            closed = true;
        }

        List<String> getStartedDeviceIds() { return startedDeviceIds; }
        List<String> getStoppedDeviceIds() { return stoppedDeviceIds; }
        boolean      isClosed()            { return closed; }
    }

    private RecordingProtocolServer modbusServer;
    private RecordingProtocolServer opcUaServer;
    private Map<ProtocolType, ProtocolServer> serverMap;
    private DefaultFleetManager fleetManager;

    @BeforeEach
    void setUp() {
        modbusServer = new RecordingProtocolServer(ProtocolType.MODBUS_TCP);
        opcUaServer  = new RecordingProtocolServer(ProtocolType.OPC_UA);

        serverMap = new LinkedHashMap<>();
        serverMap.put(ProtocolType.MODBUS_TCP, modbusServer);
        serverMap.put(ProtocolType.OPC_UA, opcUaServer);

        fleetManager = new DefaultFleetManager(serverMap);
    }

    // -- helper -------------------------------------------------------------

    private SimulatedDevice createDevice(String id, ProtocolType protocol, boolean enabled) {
        SimulatedDevice device = new SimulatedDevice();
        device.setId(id);
        device.setName("Device " + id);
        device.setProtocol(protocol);
        ModbusTcpEndpoint endpoint = new ModbusTcpEndpoint();
        endpoint.setHost("127.0.0.1");
        device.setEndpoint(endpoint);
        device.setEnabled(enabled);

        DataPoint dp = new DataPoint();
        dp.setId(id + "-dp1");
        dp.setName("DP1");
        dp.setAddress(new ModbusAddress(ModbusAddress.TYPE_HOLDING, 0, ModbusAddress.DATA_INT));
        dp.setGenerator(new FixedGenerator(42.0));
        device.setDataPoints(List.of(dp));

        return device;
    }

    // -- loadProfiles -------------------------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void loadProfilesRegistersDevices() {
        SimulatedDevice dev1 = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        SimulatedDevice dev2 = createDevice("dev-2", ProtocolType.OPC_UA, true);

        fleetManager.loadProfiles(List.of(dev1, dev2));

        Collection<SimulatedDevice> devices = fleetManager.listDevices();
        assertEquals(2, devices.size());

        // Verify initial status is STOPPED
        assertEquals(DeviceStatus.STOPPED, fleetManager.getDeviceStatus("dev-1"));
        assertEquals(DeviceStatus.STOPPED, fleetManager.getDeviceStatus("dev-2"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void loadProfilesClearsExistingDevices() {
        SimulatedDevice oldDev = createDevice("old-dev", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(oldDev));
        assertEquals(1, fleetManager.listDevices().size());

        SimulatedDevice newDev = createDevice("new-dev", ProtocolType.OPC_UA, true);
        fleetManager.loadProfiles(List.of(newDev));

        assertEquals(1, fleetManager.listDevices().size());
        assertNotNull(fleetManager.getDevice("new-dev"));
        assertNull(fleetManager.getDevice("old-dev"));
    }

    // -- startAll / stopAll -------------------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startAllStartsEnabledDevices() throws Exception {
        SimulatedDevice dev1 = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        SimulatedDevice dev2 = createDevice("dev-2", ProtocolType.OPC_UA, true);
        SimulatedDevice dev3 = createDevice("dev-3", ProtocolType.MODBUS_TCP, false);

        fleetManager.loadProfiles(List.of(dev1, dev2, dev3));
        fleetManager.startAll();

        assertEquals(DeviceStatus.RUNNING, fleetManager.getDeviceStatus("dev-1"));
        assertEquals(DeviceStatus.RUNNING, fleetManager.getDeviceStatus("dev-2"));
        assertEquals(DeviceStatus.STOPPED, fleetManager.getDeviceStatus("dev-3"));

        assertEquals(1, modbusServer.getStartedDeviceIds().size());
        assertEquals(1, opcUaServer.getStartedDeviceIds().size());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void stopAllSetsAllDevicesToStopped() throws Exception {
        SimulatedDevice dev1 = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        SimulatedDevice dev2 = createDevice("dev-2", ProtocolType.OPC_UA, true);

        fleetManager.loadProfiles(List.of(dev1, dev2));
        fleetManager.startAll();
        fleetManager.stopAll();

        assertEquals(DeviceStatus.STOPPED, fleetManager.getDeviceStatus("dev-1"));
        assertEquals(DeviceStatus.STOPPED, fleetManager.getDeviceStatus("dev-2"));

        assertEquals(List.of("dev-1"), modbusServer.getStoppedDeviceIds());
        assertEquals(List.of("dev-2"), opcUaServer.getStoppedDeviceIds());
    }

    // -- startDevice / stopDevice -------------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startDeviceDelegatesToCorrectProtocolServer() throws Exception {
        SimulatedDevice dev1 = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        SimulatedDevice dev2 = createDevice("dev-2", ProtocolType.OPC_UA, true);

        fleetManager.loadProfiles(List.of(dev1, dev2));
        fleetManager.startDevice("dev-1");

        assertEquals(DeviceStatus.RUNNING, fleetManager.getDeviceStatus("dev-1"));
        assertEquals(List.of("dev-1"), modbusServer.getStartedDeviceIds());
        assertTrue(opcUaServer.getStartedDeviceIds().isEmpty());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startDeviceWithUnknownIdThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fleetManager.startDevice("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startDeviceWithNoRegisteredServerThrowsIllegalStateException() throws Exception {
        Map<ProtocolType, ProtocolServer> emptyServers = Collections.emptyMap();
        DefaultFleetManager fm = new DefaultFleetManager(emptyServers);

        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fm.loadProfiles(List.of(dev));

        assertThrows(IllegalStateException.class,
                () -> fm.startDevice("dev-1"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void stopDeviceSetsStatusStopped() throws Exception {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));
        fleetManager.startDevice("dev-1");

        assertEquals(DeviceStatus.RUNNING, fleetManager.getDeviceStatus("dev-1"));

        fleetManager.stopDevice("dev-1");
        assertEquals(DeviceStatus.STOPPED, fleetManager.getDeviceStatus("dev-1"));
        assertEquals(List.of("dev-1"), modbusServer.getStoppedDeviceIds());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void stopDeviceWithUnknownIdDoesNothing() {
        assertDoesNotThrow(() -> fleetManager.stopDevice("nonexistent"));
    }

    // -- getDevice / getDeviceStatus ----------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void getDeviceWithUnknownIdReturnsNull() {
        assertNull(fleetManager.getDevice("nonexistent"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void getDeviceStatusWithUnknownIdReturnsNull() {
        assertNull(fleetManager.getDeviceStatus("nonexistent"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void getDeviceReturnsCorrectDevice() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));

        assertNotNull(fleetManager.getDevice("dev-1"));
        assertEquals("dev-1", fleetManager.getDevice("dev-1").getId());
    }

    // -- listDevices --------------------------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void listDevicesReturnsUnmodifiableCollection() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));

        Collection<SimulatedDevice> result = fleetManager.listDevices();
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new SimulatedDevice()));
    }

    // -- applyOverride / clearOverride --------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void applyOverrideSetsDataPointOverride() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));

        fleetManager.applyOverride("dev-1", "dev-1-dp1", 99.0);

        DataPoint dp = fleetManager.getDevice("dev-1").getDataPoints().get(0);
        assertEquals(99.0, dp.getOverride());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clearOverrideRemovesDataPointOverride() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));
        fleetManager.applyOverride("dev-1", "dev-1-dp1", 99.0);

        fleetManager.clearOverride("dev-1", "dev-1-dp1");

        DataPoint dp = fleetManager.getDevice("dev-1").getDataPoints().get(0);
        assertNull(dp.getOverride());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void applyOverrideWithUnknownDeviceThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fleetManager.applyOverride("nonexistent", "dp1", 1.0));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void applyOverrideWithUnknownDataPointThrowsIllegalArgumentException() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fleetManager.applyOverride("dev-1", "nonexistent-dp", 1.0));
        assertTrue(ex.getMessage().contains("nonexistent-dp"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clearOverrideWithUnknownDeviceThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fleetManager.clearOverride("nonexistent", "dp1"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    // -- injectFault / clearFault -------------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void injectFaultSetsActiveFault() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));

        fleetManager.injectFault("dev-1", FaultType.DISCONNECT);

        assertEquals(FaultType.DISCONNECT, fleetManager.getDevice("dev-1").getActiveFault());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clearFaultRemovesActiveFault() {
        SimulatedDevice dev = createDevice("dev-1", ProtocolType.MODBUS_TCP, true);
        fleetManager.loadProfiles(List.of(dev));
        fleetManager.injectFault("dev-1", FaultType.STALE_DATA);

        fleetManager.clearFault("dev-1");

        assertNull(fleetManager.getDevice("dev-1").getActiveFault());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void injectFaultWithUnknownDeviceThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fleetManager.injectFault("nonexistent", FaultType.NOISE_SPIKE));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clearFaultWithUnknownDeviceThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fleetManager.clearFault("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    // -- constructor ---------------------------------------------------------

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void constructorRejectsNullServers() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultFleetManager(null));
    }
}
