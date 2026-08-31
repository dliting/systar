package com.systar.monitor.drivers.iec104;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.iec104.Iec104Service.Iec104Connection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IEC 104 probe for electric power protocol data points (YC/YX).
 * <p>
 * Source format: {@code type:address} (e.g. {@code YC:12345}, {@code 遥测:100}).
 */
public class Iec104Probe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(Iec104Probe.class);

    private String dataType;
    private int address;

    public Iec104Probe() {
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
            throw new IllegalArgumentException("IEC 104 source must not be empty");
        }
        String[] parts = source.trim().split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "IEC 104 source format: type:address, got: " + source);
        }
        this.dataType = normaliseDataType(parts[0].trim());
        this.address = Integer.parseInt(parts[1].trim());
    }

    private String normaliseDataType(String typeStr) {
        return switch (typeStr) {
            case "遥测", "YC", "yc", "M_ME" -> "YC";
            case "遥信", "YX", "yx", "M_SP" -> "YX";
            default -> throw new IllegalArgumentException(
                    "Unknown IEC 104 data type: " + typeStr);
        };
    }

    // ======================== detection (active) ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (dataType == null) {
            ProbeType type = getType();
            String source = type != null ? type.getSource() : null;
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof Iec104Service service)) {
            throw new IllegalStateException("Iec104Probe must belong to an Iec104Service");
        }

        Iec104Connection conn = null;
        try {
            conn = (Iec104Connection) service.getConnection();
            Object value = conn.read(address);
            if (value == null) {
                result.setError("No data for IEC 104 address " + address);
            }
            result.setValue(value);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("IEC 104 read failed for {}/{} at {}:{}",
                    dataType, address, service.getHost(), service.getPort(), e);
            result.setError("IEC 104 read failed: " + e.getMessage());
            if (conn != null) {
                conn.close();
            }
        } finally {
            if (conn != null) {
                service.releaseConnection(conn);
            }
        }
    }

    // ======================== getters / setters ========================

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public int getAddress() { return address; }
    public void setAddress(int address) { this.address = address; }
}
