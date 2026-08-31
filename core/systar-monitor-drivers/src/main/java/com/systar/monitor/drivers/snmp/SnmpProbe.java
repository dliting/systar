package com.systar.monitor.drivers.snmp;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.snmp.SnmpService.SnmpConnection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.Variable;

/**
 * SNMP probe that reads data via SNMP GET requests.
 * <p>
 * Source format: OID string, e.g.:
 * <ul>
 *   <li>{@code 1.3.6.1.2.1.2.2.1.10.1} -- interface in-octets</li>
 *   <li>{@code 1.3.6.1.2.1.1.3.0} -- system uptime</li>
 * </ul>
 */
public class SnmpProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpProbe.class);

    private String oid;

    public SnmpProbe() {
    }

    @Override
    public void init(ProbeType type, int id, String name) {
        super.init(type, id, name);
        if (type != null && type.getSource() != null) {
            parseSource(type.getSource());
        }
    }

    // ======================== source parsing ========================

    private void parseSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("SNMP source (OID) must not be empty");
        }
        String trimmed = source.trim();
        if (!trimmed.matches("^[0-9][0-9.]*$")) {
            throw new IllegalArgumentException("Invalid SNMP OID format: " + source);
        }
        this.oid = trimmed;
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (oid == null) {
            ProbeType type = getType();
            String source = type != null ? type.getSource() : null;
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof SnmpService snmpService)) {
            throw new IllegalStateException("SnmpProbe must belong to a SnmpService");
        }

        SnmpConnection conn = null;
        try {
            conn = (SnmpConnection) snmpService.getConnection();
            Variable var = conn.getVar(oid);
            if (var == null) {
                result.setError("No response for SNMP OID " + oid);
                result.setValue(null);
            } else {
                result.setValue(convertValue(var));
            }
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("SNMP GET failed for OID {} at {}:{}", oid,
                    snmpService.getHost(), snmpService.getPort(), e);
            result.setError("SNMP read failed: " + e.getMessage());
            if (conn != null) {
                conn.close();
            }
        } finally {
            if (conn != null) {
                snmpService.releaseConnection(conn);
            }
        }
    }

    // ======================== value conversion ========================

    /**
     * Converts an SNMP4J Variable to a Java object using toString().
     */
    protected Object convertValue(Variable var) {
        return var.toString();
    }

    // ======================== OID utility ========================

    /**
     * Converts an OID string like "1.3.6.1.2.1" to an int array.
     */
    public static int[] oidToIntArray(String oid) {
        String[] parts = oid.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    // ======================== getters / setters ========================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
}
