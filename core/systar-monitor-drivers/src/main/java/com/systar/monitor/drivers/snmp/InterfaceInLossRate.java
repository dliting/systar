package com.systar.monitor.drivers.snmp;

import com.systar.monitor.result.IMonitorResult;

/**
 * SNMP interface probe that reports input packet loss rate (0.0 – 1.0).
 * <p>
 * Calculated as delta(discards or errors) / delta(total packets) between
 * two consecutive samples.
 */
public class InterfaceInLossRate extends InterfaceProbe {

    @Override
    public void detect(IMonitorResult result) throws Exception {
        detectOnDemand();
        Float value = getCachedInLossRate();
        if (value == null) {
            result.setError("Failed to retrieve input loss rate of interface " + getName());
        }
        result.setValue(value);
    }
}
