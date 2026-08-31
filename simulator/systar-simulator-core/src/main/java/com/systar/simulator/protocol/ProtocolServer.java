package com.systar.simulator.protocol;

import com.systar.simulator.model.DataPointAddress;
import com.systar.simulator.model.SimulatedDevice;

/**
 * SPI for protocol-specific server implementations (e.g. Modbus TCP slave,
 * OPC UA server).
 * <p>
 * Each implementation manages the lifecycle of simulated devices for one
 * protocol type, handling incoming connections and serving the current
 * data-point values.
 */
public interface ProtocolServer extends AutoCloseable {

    /**
     * Start serving data for the given device.
     *
     * @param device the device to register with this server
     * @throws Exception if the server cannot start
     */
    void start(SimulatedDevice device) throws Exception;

    /**
     * Stop serving data for the device identified by {@code deviceId}.
     *
     * @param deviceId the ID of the device to unregister
     */
    void stop(String deviceId);

    /**
     * Push an updated value to the server so connected clients observe the
     * change on the next poll or subscription callback.
     *
     * @param deviceId the owning device ID
     * @param address  the protocol-specific address of the data point
     * @param value    the new value
     */
    void updateValue(String deviceId, DataPointAddress address, Object value);

    /**
     * Query the current operational status of this server.
     *
     * @return current server status
     */
    ServerStatus getStatus();

    /**
     * Gracefully shut down the server and release all resources.
     */
    @Override
    void close();
}
