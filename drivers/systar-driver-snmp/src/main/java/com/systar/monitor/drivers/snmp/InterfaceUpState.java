package com.systar.monitor.drivers.snmp;

import com.systar.monitor.result.IMonitorResult;

/**
 * SNMP interface probe that reports the administrative/operational up state.
 * <p>
 * Returns {@code true} when the interface is in UP or TESTING state,
 * {@code false} when DOWN.
 */
public class InterfaceUpState extends InterfaceProbe {

    @Override
    public void detect(IMonitorResult result) throws Exception {
        detectOnDemand();
        Boolean value = getCachedUpState();
        if (value == null) {
            result.setError("Failed to retrieve up state of interface " + getName());
        }
        result.setValue(value);
    }
}
