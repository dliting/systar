package com.systar.monitor.asset;

/**
 * Abstraction for a connection used by active monitors.
 * <p>
 * Implementations manage the lifecycle of a communication channel
 * (e.g., TCP socket, serial port, HTTP session) used for polling data.
 */
public interface MonitorConnection extends AutoCloseable {

    /** Opens the connection. */
    void open() throws Exception;

    /** Returns whether the connection is currently open. */
    boolean isConnected();

    /** Closes the connection and releases resources. */
    @Override
    void close();
}
