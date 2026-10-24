package com.systar.monitor.drivers.snmp;

import com.systar.monitor.result.IMonitorResult;

/**
 * SNMP interface probe that reports output packet loss rate (0.0 – 1.0).
 * <p>
 * Calculated as delta(discards or errors) / delta(total packets) between
 * two consecutive samples.
 */
public class InterfaceOutLossRate extends InterfaceProbe {

    @Override
    public void detect(IMonitorResult result) throws Exception {
        detectOnDemand();
        Float value = getCachedOutLossRate();
        if (value == null) {
            result.setError("Failed to retrieve output loss rate of interface " + getName());
        }
        result.setValue(value);
    }
}
