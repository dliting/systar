package com.systar.simulator.app.scheduler;

import com.systar.simulator.fleet.FleetManager;
import com.systar.simulator.generator.DataGenerator;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DataUpdateSchedulerTest {

    private static final int TIMEOUT_SECONDS = 180;

    // ======================== Recording mock ========================

    record UpdateRecord(String deviceId, DataPointAddress address, Object value) {}

    /** Recording mock that tracks updateValue calls and started/stopped devices. */
    static class RecordingProtocolServer implements ProtocolServer {

        private final List<String>       startedDeviceIds = new ArrayList<>();
        private final List<String>       stoppedDeviceIds = new ArrayList<>();
        private final List<UpdateRecord> updates          = new ArrayList<>();
        private boolean                   closed           = false;

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
            updates.add(new UpdateRecord(deviceId, address, value));
        }

        @Override
        public ServerStatus getStatus() {
            return new ServerStatus(true, 0, 0);
        }

        @Override
        public void close() {
            closed = true;
        }

        List<UpdateRecord> getUpdates() { return updates; }
    }

    // ======================== Stub FleetManager ========================

    /** Minimal FleetManager stub that returns a fixed device list. */
    private static class StubFleetManager implements FleetManager {

        private final Collection<SimulatedDevice> devices;

        StubFleetManager(Collection<SimulatedDevice> devices) {
            this.devices = devices;
        }

        @Override public void loadProfiles(Collection<SimulatedDevice> devices) {}
        @Override public void startAll() throws Exception {}
        @Override public void stopAll() {}
        @Override public void startDevice(String deviceId) throws Exception {}
        @Override public void stopDevice(String deviceId) {}
        @Override public DeviceStatus getDeviceStatus(String deviceId) { return null; }
        @Override public Collection<SimulatedDevice> listDevices() { return devices; }
        @Override public SimulatedDevice getDevice(String deviceId) { return null; }
        @Override public void applyOverride(String deviceId, String dataPointId, Object value) {}
        @Override public void clearOverride(String deviceId, String dataPointId) {}
        @Override public void injectFault(String deviceId, FaultType fault) {}
        @Override public void clearFault(String deviceId) {}
    }

    // ======================== Fields & setup ========================

    private RecordingProtocolServer modbusServer;
    private Map<ProtocolType, ProtocolServer> serverMap;

    @BeforeEach
    void setUp() {
        modbusServer = new RecordingProtocolServer();
        serverMap    = Map.of(ProtocolType.MODBUS_TCP, modbusServer);
    }

    // -- helper -----------------------------------------------------------

    private SimulatedDevice createRunningDevice(String id, DataGenerator generator) {
        SimulatedDevice device = new SimulatedDevice();
        device.setId(id);
        device.setName("Device " + id);
        device.setProtocol(ProtocolType.MODBUS_TCP);

        ModbusTcpEndpoint endpoint = new ModbusTcpEndpoint();
        endpoint.setHost("127.0.0.1");
        device.setEndpoint(endpoint);
        device.setStatus(DeviceStatus.RUNNING);

        DataPoint dp = new DataPoint();
        dp.setId(id + "-dp1");
        dp.setName("DP1");
        dp.setAddress(new ModbusAddress(ModbusAddress.TYPE_HOLDING, 0, ModbusAddress.DATA_INT));
        dp.setGenerator(generator);
        device.setDataPoints(List.of(dp));

        return device;
    }

    private SimulatedDevice createStoppedDevice(String id) {
        SimulatedDevice device = createRunningDevice(id, new FixedGenerator(1.0));
        device.setStatus(DeviceStatus.STOPPED);
        return device;
    }

    // ======================== Tests ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickUpdatesDataPointValueForRunningDevice() {
        SimulatedDevice device = createRunningDevice("dev-1", new FixedGenerator(42.0));
        assertNull(device.getDataPoints().get(0).getCurrentValue());

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        DataPoint dp = device.getDataPoints().get(0);
        assertEquals(42.0, dp.getCurrentValue());
        assertTrue(dp.getLastUpdateMillis() > 0);
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickPushesUpdatedValueToProtocolServer() {
        SimulatedDevice device = createRunningDevice("dev-1", new FixedGenerator(99.0));

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        List<UpdateRecord> updates = modbusServer.getUpdates();
        assertEquals(1, updates.size());
        assertEquals("dev-1", updates.get(0).deviceId());
        assertEquals(99.0, updates.get(0).value());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickSkipsStoppedDevices() {
        SimulatedDevice device = createStoppedDevice("dev-1");

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        assertNull(device.getDataPoints().get(0).getCurrentValue());
        assertTrue(modbusServer.getUpdates().isEmpty());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickSkipsStaleDataFaultDevices() {
        SimulatedDevice device = createRunningDevice("dev-1", new FixedGenerator(55.0));
        device.setActiveFault(FaultType.STALE_DATA);

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        assertNull(device.getDataPoints().get(0).getCurrentValue());
        assertTrue(modbusServer.getUpdates().isEmpty());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickUsesOverrideValueWhenSet() {
        FixedGenerator generator = new FixedGenerator(10.0);
        SimulatedDevice device   = createRunningDevice("dev-1", generator);
        device.getDataPoints().get(0).setOverride(77.0);

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        assertEquals(77.0, device.getDataPoints().get(0).getCurrentValue());
        // Verify the override value is pushed to server
        assertEquals(77.0, modbusServer.getUpdates().get(0).value());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickHandlesMultipleDevices() {
        SimulatedDevice dev1 = createRunningDevice("dev-1", new FixedGenerator(10.0));
        SimulatedDevice dev2 = createRunningDevice("dev-2", new FixedGenerator(20.0));

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(dev1, dev2)), serverMap, 1000L, null);

        scheduler.tick();

        assertEquals(10.0, dev1.getDataPoints().get(0).getCurrentValue());
        assertEquals(20.0, dev2.getDataPoints().get(0).getCurrentValue());
        assertEquals(2, modbusServer.getUpdates().size());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickSkipsDataPointWithNullAddress() {
        SimulatedDevice device   = createRunningDevice("dev-1", new FixedGenerator(42.0));
        device.getDataPoints().get(0).setAddress(null);

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        // Value should still be updated on the data point
        assertEquals(42.0, device.getDataPoints().get(0).getCurrentValue());
        // But not pushed to server (null address)
        assertTrue(modbusServer.getUpdates().isEmpty());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickSkipsDataPointWithNullValue() {
        // Generator that produces null
        DataGenerator nullGen = ctx -> null;
        SimulatedDevice device = createRunningDevice("dev-1", nullGen);

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        assertNull(device.getDataPoints().get(0).getCurrentValue());
        assertTrue(modbusServer.getUpdates().isEmpty());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickGeneratesWithSeededRandom() {
        // Two schedulers with same seed should behave deterministically
        SimulatedDevice device = createRunningDevice("dev-1", new FixedGenerator(42.0));

        DataUpdateScheduler scheduler1 = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, 12345L);
        DataUpdateScheduler scheduler2 = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, 12345L);

        scheduler1.tick();
        long value1 = device.getDataPoints().get(0).getLastUpdateMillis();

        scheduler2.tick();
        long value2 = device.getDataPoints().get(0).getLastUpdateMillis();

        // Both should update successfully
        assertTrue(value1 > 0);
        assertTrue(value2 > 0);
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickHandlesEmptyFleet() {
        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(Collections.emptyList()), serverMap, 1000L, null);

        assertDoesNotThrow(scheduler::tick);
        assertTrue(modbusServer.getUpdates().isEmpty());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickHandlesDataPointWithNoGenerator() {
        SimulatedDevice device   = createRunningDevice("dev-1", null);
        device.getDataPoints().get(0).setGenerator(null);

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        scheduler.tick();

        // No generator and no override => value should be null
        assertNull(device.getDataPoints().get(0).getCurrentValue());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void tickUpdatesLastUpdateMillis() {
        SimulatedDevice device = createRunningDevice("dev-1", new FixedGenerator(5.0));

        DataUpdateScheduler scheduler = new DataUpdateScheduler(
                new StubFleetManager(List.of(device)), serverMap, 1000L, null);

        long beforeTick = System.currentTimeMillis();
        scheduler.tick();
        long afterTick = System.currentTimeMillis();

        long lastUpdate = device.getDataPoints().get(0).getLastUpdateMillis();
        assertTrue(lastUpdate >= beforeTick && lastUpdate <= afterTick,
                "lastUpdateMillis should be between before and after tick");
    }
}
