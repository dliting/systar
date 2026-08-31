package com.systar.monitor.drivers.snmp;

import org.snmp4j.smi.Variable;

public class SnmpIntProbe extends SnmpProbe {
    @Override
    protected Object convertValue(Variable var) {
        return Integer.parseInt(var.toString());
    }
}
