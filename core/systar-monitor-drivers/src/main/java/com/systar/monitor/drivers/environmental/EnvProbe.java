package com.systar.monitor.drivers.environmental;

import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;

/**
 * Environmental sensor probe that receives data via passive TCP reception.
 * <p>
 * Data is pushed by the EnvService Netty server and routed to this probe
 * by deviceId or deviceId:attribute compound key.
 * <p>
 * Source format: attribute name, e.g. {@code temperature}, {@code humidity},
 * {@code pm25}, {@code co2}, {@code tvoc}.
 */
public class EnvProbe extends Probe {

    private String attribute;

    public EnvProbe() {
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
            throw new IllegalArgumentException("Env source (attribute name) must not be empty");
        }
        this.attribute = source.trim().toLowerCase();
    }

    // ======================== detection (passive) ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        // Passive probe: data is pushed by EnvService Netty handler.
        // Return the last known value.
        result.setValue(getValue());
        result.setSampleTime(System.currentTimeMillis());
    }

    // ======================== IPassiveMonitor ========================

    @Override
    public String makeRegisterKey() {
        return attribute;
    }

    // ======================== getters / setters ========================

    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
}
