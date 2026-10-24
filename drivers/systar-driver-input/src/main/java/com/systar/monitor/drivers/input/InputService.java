package com.systar.monitor.drivers.input;

import com.systar.monitor.asset.Monitor;
import com.systar.monitor.asset.PassiveService;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;

/**
 * Passive service for manual data entry.
 * <p>
 * Maintains a registry of monitors keyed by their source identifier.
 * External callers push data via {@link #inputData(String, Object)},
 * which looks up the target monitor and dispatches the result.
 */
public class InputService extends PassiveService {

    public InputService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        // No external resources to initialise for manual input.
    }

    @Override
    public void stop() {
        // No external resources to release.
    }

    // ======================== data entry ========================

    /**
     * Pushes a manually entered value for the monitor identified by
     * the given register key.
     *
     * @param registerKey the routing key that identifies the target monitor
     * @param value       the user-supplied value
     */
    public void inputData(String registerKey, Object value) {
        if (registerKey == null || registerKey.isBlank()) {
            return;
        }

        Monitor<?> monitor = getMonitor(registerKey);
        if (monitor == null) {
            return;
        }

        ResultDispatcher dispatcher = resolveResultDispatcher();
        if (dispatcher == null) {
            return;
        }

        MonitorResult result = new MonitorResult(monitor, value);
        dispatcher.dispatch(result);
    }

    // ======================== convenience ========================

    /**
     * Returns the result dispatcher.
     *
     * @return the result dispatcher, or null if not set
     */
    private ResultDispatcher resolveResultDispatcher() {
        return getResultDispatcher();
    }
}
