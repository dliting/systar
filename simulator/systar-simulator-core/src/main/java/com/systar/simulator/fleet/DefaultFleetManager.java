package com.systar.simulator.fleet;

import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.DeviceStatus;
import com.systar.simulator.model.FaultType;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;
import com.systar.simulator.protocol.ProtocolServer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default implementation of {@link FleetManager}.
 * <p>
 * Maintains an in-memory device registry and delegates protocol-level
 * operations (start, stop) to the appropriate {@link ProtocolServer}
 * selected by each device's {@link ProtocolType}.
 */
public class DefaultFleetManager implements FleetManager {

    private final Map<String, SimulatedDevice>          devices = new LinkedHashMap<>();
    private final Map<ProtocolType, ProtocolServer>     servers;

    /**
     * Create a fleet manager backed by the given protocol servers.
     *
     * @param servers protocol server implementations keyed by protocol type
     * @throws IllegalArgumentException if {@code servers} is null
     */
    public DefaultFleetManager(Map<ProtocolType, ProtocolServer> servers) {
        if (servers == null) {
            throw new IllegalArgumentException("servers must not be null");
        }
        this.servers = Map.copyOf(servers);
    }

    @Override
    public void loadProfiles(Collection<SimulatedDevice> devices) {
        this.devices.clear();
        for (SimulatedDevice device : devices) {
            device.setStatus(DeviceStatus.STOPPED);
            this.devices.put(device.getId(), device);
        }
    }

    @Override
    public void startAll() throws Exception {
        for (SimulatedDevice device : devices.values()) {
            if (device.isEnabled()) {
                startDevice(device.getId());
            }
        }
    }

    @Override
    public void stopAll() {
        for (SimulatedDevice device : devices.values()) {
            stopDevice(device.getId());
        }
    }

    @Override
    public void startDevice(String deviceId) throws Exception {
        SimulatedDevice device = requireDevice(deviceId);
        ProtocolServer   server = servers.get(device.getProtocol());
        if (server == null) {
            throw new IllegalStateException(
                    "No protocol server registered for " + device.getProtocol());
        }
        server.start(device);
        device.setStatus(DeviceStatus.RUNNING);
    }

    @Override
    public void stopDevice(String deviceId) {
        SimulatedDevice device = devices.get(deviceId);
        if (device == null) {
            return;
        }
        ProtocolServer server = servers.get(device.getProtocol());
        if (server != null) {
            server.stop(deviceId);
        }
        device.setStatus(DeviceStatus.STOPPED);
    }

    @Override
    public DeviceStatus getDeviceStatus(String deviceId) {
        SimulatedDevice device = devices.get(deviceId);
        return device != null ? device.getStatus() : null;
    }

    @Override
    public Collection<SimulatedDevice> listDevices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    @Override
    public SimulatedDevice getDevice(String deviceId) {
        return devices.get(deviceId);
    }

    @Override
    public void applyOverride(String deviceId, String dataPointId, Object value) {
        DataPoint dataPoint = requireDataPoint(deviceId, dataPointId);
        dataPoint.setOverride(value);
    }

    @Override
    public void clearOverride(String deviceId, String dataPointId) {
        DataPoint dataPoint = requireDataPoint(deviceId, dataPointId);
        dataPoint.setOverride(null);
    }

    @Override
    public void injectFault(String deviceId, FaultType fault) {
        SimulatedDevice device = requireDevice(deviceId);
        device.setActiveFault(fault);
    }

    @Override
    public void clearFault(String deviceId) {
        SimulatedDevice device = requireDevice(deviceId);
        device.setActiveFault(null);
    }

    // -- helper methods -----------------------------------------------------

    private SimulatedDevice requireDevice(String deviceId) {
        SimulatedDevice device = devices.get(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }
        return device;
    }

    private DataPoint requireDataPoint(String deviceId, String dataPointId) {
        SimulatedDevice device = requireDevice(deviceId);
        for (DataPoint dp : device.getDataPoints()) {
            if (dp.getId().equals(dataPointId)) {
                return dp;
            }
        }
        throw new IllegalArgumentException(
                "Data point not found: " + dataPointId + " in device: " + deviceId);
    }
}
