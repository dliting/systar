package com.systar.monitor.drivers.snmp;

import com.systar.monitor.result.IMonitorResult;

/**
 * SNMP interface probe that reports send speed in Kbps.
 * <p>
 * Uses the two-sample delta algorithm from {@link InterfaceProbe}
 * to compute the rate of outgoing octets.
 */
public class InterfaceSendSpeed extends InterfaceProbe {

    @Override
    public void detect(IMonitorResult result) throws Exception {
        detectOnDemand();
        Float value = getCachedSendSpeed();
        if (value == null) {
            result.setError("Failed to retrieve send speed of interface " + getName());
        }
        result.setValue(value);
    }
}
