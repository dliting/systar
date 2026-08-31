package com.systar.monitor.drivers.simulate;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

/**
 * Active service for simulated data sources.
 * <p>
 * Does not require a real communication channel; provides a
 * {@link DummyConnection} that satisfies the connection pool contract
 * without performing any I/O.
 */
public class SimulateService extends ActiveService {

    public SimulateService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        // No external resources to initialise for a simulated source.
    }

    @Override
    public void stop() {
        // No external resources to release.
    }

    // ======================== connection factory ========================

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new DummyConnection();
    }

    // ======================== inner class ========================

    /**
     * No-op connection for the simulate driver.
     * Always reports as connected; open/close are no-ops.
     */
    private static class DummyConnection implements MonitorConnection {

        private volatile boolean open = true;

        @Override
        public void open() throws Exception {
            open = true;
        }

        @Override
        public boolean isConnected() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
