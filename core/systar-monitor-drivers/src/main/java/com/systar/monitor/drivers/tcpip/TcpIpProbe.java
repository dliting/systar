package com.systar.monitor.drivers.tcpip;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.tcpip.TcpIpService.TcpIpConnection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic TCP/IP probe that reads data via custom TCP protocol frames
 * or performs a simple connectivity check.
 * <p>
 * Source formats:
 * <ul>
 *   <li>{@code connectivity:0:bool} -- test TCP connection (returns true/false)</li>
 *   <li>{@code register:address:type} -- send request frame, parse response</li>
 * </ul>
 */
public class TcpIpProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(TcpIpProbe.class);

    private static final String MODE_CONNECTIVITY = "connectivity";

    private String registerType;
    private int address;
    private String dataType;

    public TcpIpProbe() {
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
            throw new IllegalArgumentException("TCP/IP source must not be empty");
        }
        String[] parts = source.trim().split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "TCP/IP source format: registerType:address:dataType, got: " + source);
        }
        this.registerType = parts[0].trim().toLowerCase();
        this.address = Integer.parseInt(parts[1].trim());
        this.dataType = parts[2].trim().toLowerCase();
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (registerType == null) {
            ProbeType type = getType();
            String source = type != null ? type.getSource() : null;
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof TcpIpService tcpIpService)) {
            throw new IllegalStateException("TcpIpProbe must belong to a TcpIpService");
        }

        if (MODE_CONNECTIVITY.equals(registerType)) {
            detectConnectivity(tcpIpService, result);
        } else {
            detectRegister(tcpIpService, result);
        }
    }

    private void detectConnectivity(TcpIpService service, IMonitorResult result) {
        TcpIpConnection conn = null;
        try {
            conn = (TcpIpConnection) service.getConnection();
            result.setValue(true);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.debug("TCP connectivity check failed for {}:{}", service.getHost(), service.getPort(), e);
            result.setError("Connection failed: " + e.getMessage());
            if (conn != null) {
                conn.close();
            }
        } finally {
            service.releaseConnection(conn);
        }
    }

    private void detectRegister(TcpIpService service, IMonitorResult result) throws Exception {
        TcpIpConnection conn = null;
        try {
            conn = (TcpIpConnection) service.getConnection();
            byte[] request = buildRequestFrame();
            byte[] response = conn.sendAndReceive(request);
            if (response == null) {
                throw new Exception("No response from device");
            }
            Object value = parseResponseFrame(response);
            result.setValue(value);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warn("TCP/IP register read failed for {}:{}", service.getHost(), service.getPort(), e);
            result.setError("TCP/IP read failed: " + e.getMessage());
            if (conn != null) {
                conn.close();
            }
        } finally {
            if (conn != null) {
                service.releaseConnection(conn);
            }
        }
    }

    // ======================== frame building ========================

    private byte[] buildRequestFrame() {
        // Default raw frame: [registerType byte][address high][address low][dataType byte]
        int typeCode = switch (registerType) {
            case "register" -> 0x03;
            case "coil" -> 0x01;
            default -> 0x03;
        };
        int dataCode = switch (dataType) {
            case "float" -> 0x04;
            case "int" -> 0x02;
            case "bool" -> 0x01;
            case "short" -> 0x02;
            default -> 0x02;
        };
        return new byte[]{
                (byte) typeCode,
                (byte) ((address >> 8) & 0xFF),
                (byte) (address & 0xFF),
                (byte) dataCode
        };
    }

    private Object parseResponseFrame(byte[] response) {
        if (response.length < 1) {
            return null;
        }
        return switch (dataType) {
            case "bool" -> response[0] != 0;
            case "int" -> response.length >= 2
                    ? ((response[0] & 0xFF) << 8) | (response[1] & 0xFF)
                    : response[0] & 0xFF;
            case "float" -> response.length >= 4
                    ? Float.intBitsToFloat(
                    ((response[0] & 0xFF) << 24) | ((response[1] & 0xFF) << 16)
                            | ((response[2] & 0xFF) << 8) | (response[3] & 0xFF))
                    : null;
            default -> response;
        };
    }

    // ======================== getters / setters ========================

    public String getRegisterType() {
        return registerType;
    }

    public void setRegisterType(String registerType) {
        this.registerType = registerType;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }
}
