package com.systar.monitor.drivers.snmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.Variable;

public class SnmpBoolProbe extends SnmpProbe {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpBoolProbe.class);

    @Override
    protected Object convertValue(Variable var) {
        String s = var.toString().toLowerCase();
        if ("true".equals(s) || "1".equals(s))  return true;
        if ("false".equals(s) || "0".equals(s)) return false;
        LOG.warn("SnmpBoolProbe '{}' received unrecognized boolean value '{}' — defaulting to false",
                getName(), s);
        return false;
    }
}
