package com.systar.monitor.drivers.snmp;

import org.snmp4j.smi.Variable;

public class SnmpFloatProbe extends SnmpProbe {
    @Override
    protected Object convertValue(Variable var) {
        return Float.parseFloat(var.toString());
    }
}
