package com.systar.monitor.drivers.input;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.result.IMonitorResult;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;

/**
 * Passive probe for manual (human-entered) data input.
 * <p>
 * The {@link #detect(IMonitorResult)} method is a no-op because input probes
 * do not poll. Instead, data is pushed externally via
 * {@link #manualInput(Object, ResultDispatcher)}.
 */
public class InputProbe extends Probe {

    public InputProbe() {
    }

    // ======================== detection (no-op) ========================

    /**
     * No-op for passive input probes.
     * Data is supplied externally via {@link #manualInput(Object, ResultDispatcher)}.
     */
    @Override
    public void detect(IMonitorResult result) throws Exception {
        // Passive probe -- does not auto-detect.
    }

    // ======================== manual input ========================

    /**
     * Accepts a manually entered value and dispatches it through the
     * monitoring pipeline.
     *
     * @param value           the user-supplied value
     * @param resultDispatcher the dispatcher that processes the result
     */
    public void manualInput(Object value, ResultDispatcher resultDispatcher) {
        if (resultDispatcher == null) {
            return;
        }
        MonitorResult result = new MonitorResult(this, value);
        resultDispatcher.dispatch(result);
    }
}
