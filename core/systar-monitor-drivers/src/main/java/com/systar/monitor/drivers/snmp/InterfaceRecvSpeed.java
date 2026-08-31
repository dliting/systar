package com.systar.monitor.drivers.snmp;

import com.systar.monitor.result.IMonitorResult;

/**
 * SNMP interface probe that reports receive speed in Kbps.
 * <p>
 * Uses the two-sample delta algorithm from {@link InterfaceProbe}
 * to compute the rate of incoming octets.
 */
public class InterfaceRecvSpeed extends InterfaceProbe {

    @Override
    public void detect(IMonitorResult result) throws Exception {
        detectOnDemand();
        Float value = getCachedRecvSpeed();
        if (value == null) {
            result.setError("Failed to retrieve receive speed of interface " + getName());
        }
        result.setValue(value);
    }
}
