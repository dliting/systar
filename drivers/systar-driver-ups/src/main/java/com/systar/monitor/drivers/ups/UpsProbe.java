package com.systar.monitor.drivers.ups;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.ups.UpsService.UpsConnection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.Variable;

/**
 * UPS (Uninterruptible Power Supply) probe that reads data via SNMP UPS-MIB.
 * <p>
 * Source format: attribute name (e.g. {@code input_voltage}, {@code battery_level}).
 */
public class UpsProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(UpsProbe.class);

    private String attribute;

    public UpsProbe() {
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
            throw new IllegalArgumentException("UPS source (attribute name) must not be empty");
        }
        this.attribute = source.trim().toLowerCase();
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (attribute == null) {
            ProbeType type = getType();
            String source = type != null ? type.getSource() : null;
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof UpsService upsService)) {
            throw new IllegalStateException("UpsProbe must belong to an UpsService");
        }

        UpsConnection conn = null;
        try {
            conn = (UpsConnection) upsService.getConnection();
            Variable var = conn.readAttribute(attribute);
            if (var == null) {
                result.setError("No UPS data for attribute: " + attribute);
            } else {
                result.setValue(var.toString());
            }
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("UPS read failed for attribute '{}' at {}:{}", attribute,
                    upsService.getHost(), upsService.getPort(), e);
            result.setError("UPS read failed: " + e.getMessage());
            if (conn != null) {
                conn.close();
            }
        } finally {
            if (conn != null) {
                upsService.releaseConnection(conn);
            }
        }
    }

    // ======================== getters / setters ========================

    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
}
