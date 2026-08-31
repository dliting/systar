package com.systar.monitor.drivers.siemens;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.siemens.SiemensService.SiemensConnection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Siemens S7 PLC probe that reads data from S7 protocol registers.
 * <p>
 * Source format: {@code area.offset:type}
 * E.g. {@code DB1.DBD4:real}, {@code DB1.DBX0.0:bool}, {@code M10.2:bool}.
 */
public class SiemensProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(SiemensProbe.class);

    private String area;
    private int dbNumber;
    private int byteOffset;
    private int bitOffset;
    private String dataType;

    public SiemensProbe() {
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
            throw new IllegalArgumentException("Siemens source must not be empty");
        }
        String[] mainParts = source.trim().split(":");
        if (mainParts.length < 2) {
            throw new IllegalArgumentException(
                    "Siemens source format: area.offset:type, got: " + source);
        }
        this.dataType = mainParts[1].trim().toLowerCase();
        String address = mainParts[0].trim().toUpperCase();

        if (address.startsWith("DB")) {
            int dotIdx = address.indexOf('.');
            if (dotIdx < 0) throw new IllegalArgumentException("Invalid DB address: " + address);
            this.area = "DB";
            this.dbNumber = Integer.parseInt(address.substring(2, dotIdx));
            parseDbAddress(address.substring(dotIdx + 1));
        } else if (address.startsWith("M")) {
            this.area = "M";
            this.dbNumber = 0;
            String[] parts = address.substring(1).split("\\.");
            this.byteOffset = Integer.parseInt(parts[0]);
            this.bitOffset = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        } else {
            throw new IllegalArgumentException("Unknown Siemens address area: " + address);
        }
    }

    private void parseDbAddress(String remainder) {
        if (remainder.startsWith("DBX")) {
            String[] parts = remainder.substring(3).split("\\.");
            this.byteOffset = Integer.parseInt(parts[0]);
            this.bitOffset = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        } else if (remainder.startsWith("DBD")) {
            this.byteOffset = Integer.parseInt(remainder.substring(3));
            this.bitOffset = 0;
        } else if (remainder.startsWith("DBW")) {
            this.byteOffset = Integer.parseInt(remainder.substring(3));
            this.bitOffset = 0;
        } else if (remainder.startsWith("DBB")) {
            this.byteOffset = Integer.parseInt(remainder.substring(3));
            this.bitOffset = 0;
        } else {
            throw new IllegalArgumentException("Cannot parse DB address: " + remainder);
        }
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (area == null) {
            ProbeType type = getType();
            String source = type != null ? type.getSource() : null;
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof SiemensService siemensService)) {
            throw new IllegalStateException("SiemensProbe must belong to a SiemensService");
        }

        SiemensConnection conn = null;
        try {
            conn = (SiemensConnection) siemensService.getConnection();
            Object value = conn.read(area, dbNumber, byteOffset, bitOffset, dataType);
            if (value == null) {
                result.setError("No data for Siemens S7 " + area
                        + dbNumber + "." + byteOffset);
            }
            result.setValue(value);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("Siemens S7 read failed for {}:{} bit={} at {}:{}",
                    area, byteOffset, bitOffset, siemensService.getHost(),
                    siemensService.getPort(), e);
            result.setError("Siemens read failed: " + e.getMessage());
            if (conn != null) { conn.close(); }
        } finally {
            if (conn != null) siemensService.releaseConnection(conn);
        }
    }

    // ======================== getters ========================

    public String getArea() { return area; }
    public int getDbNumber() { return dbNumber; }
    public int getByteOffset() { return byteOffset; }
    public int getBitOffset() { return bitOffset; }
    public String getDataType() { return dataType; }
}
