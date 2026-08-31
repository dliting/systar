package com.systar.simulator.fleet;

import com.systar.simulator.model.DeviceStatus;
import com.systar.simulator.model.FaultType;
import com.systar.simulator.model.SimulatedDevice;

import java.util.Collection;

/**
 * Central coordinator for the simulated device fleet.
 * <p>
 * Manages device lifecycle (start, stop, load profiles) and provides
 * operations for runtime overrides and fault injection.
 */
public interface FleetManager {

    /**
     * Replace the current device registry with the given collection.
     * All devices are registered with {@link DeviceStatus#STOPPED}.
     *
     * @param devices the device profiles to load
     */
    void loadProfiles(Collection<SimulatedDevice> devices);

    /**
     * Start all enabled devices in the fleet.
     *
     * @throws Exception if any device fails to start
     */
    void startAll() throws Exception;

    /**
     * Stop all running devices in the fleet and set their status to {@link DeviceStatus#STOPPED}.
     */
    void stopAll();

    /**
     * Start a single device by ID.
     *
     * @param deviceId the device to start
     * @throws Exception if the device cannot be started
     */
    void startDevice(String deviceId) throws Exception;

    /**
     * Stop a single device by ID and set its status to {@link DeviceStatus#STOPPED}.
     *
     * @param deviceId the device to stop
     */
    void stopDevice(String deviceId);

    /**
     * Query the current status of a device.
     *
     * @param deviceId the device ID
     * @return the device status, or {@code null} if the device is not found
     */
    DeviceStatus getDeviceStatus(String deviceId);

    /**
     * List all registered devices.
     *
     * @return an unmodifiable collection of all devices
     */
    Collection<SimulatedDevice> listDevices();

    /**
     * Look up a single device by ID.
     *
     * @param deviceId the device ID
     * @return the device, or {@code null} if not found
     */
    SimulatedDevice getDevice(String deviceId);

    /**
     * Apply a static value override to a data point.
     *
     * @param deviceId    the owning device ID
     * @param dataPointId the data point ID within the device
     * @param value        the override value (or {@code null} to clear)
     * @throws IllegalArgumentException if the device or data point is not found
     */
    void applyOverride(String deviceId, String dataPointId, Object value);

    /**
     * Remove a previously applied override from a data point.
     *
     * @param deviceId    the owning device ID
     * @param dataPointId the data point ID within the device
     * @throws IllegalArgumentException if the device or data point is not found
     */
    void clearOverride(String deviceId, String dataPointId);

    /**
     * Inject a fault condition into a device.
     *
     * @param deviceId the target device ID
     * @param fault    the fault type to activate
     * @throws IllegalArgumentException if the device is not found
     */
    void injectFault(String deviceId, FaultType fault);

    /**
     * Clear any active fault from a device.
     *
     * @param deviceId the target device ID
     * @throws IllegalArgumentException if the device is not found
     */
    void clearFault(String deviceId);
}
