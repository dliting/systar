package com.systar.simulator.protocol;

/**
 * Immutable snapshot of a {@link ProtocolServer}'s operational state.
 */
public class ServerStatus {

    private final boolean running;
    private final int     deviceCount;
    private final int     connectionCount;

    public ServerStatus(boolean running, int deviceCount, int connectionCount) {
        this.running         = running;
        this.deviceCount     = deviceCount;
        this.connectionCount = connectionCount;
    }

    public boolean isRunning()          { return running; }
    public int     getDeviceCount()     { return deviceCount; }
    public int     getConnectionCount() { return connectionCount; }
}
