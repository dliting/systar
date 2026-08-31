package com.systar.monitor.drivers.ups;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.util.Map;
import java.util.Set;

/**
 * Active service for UPS monitoring via SNMP (RFC 1628 UPS-MIB).
 * Supports SNMP as primary protocol; Modbus fallback reserved for future.
 */
public class UpsService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(UpsService.class);

    private static final int DEFAULT_PORT = 161;
    private static final String DEFAULT_COMMUNITY = "public";
    private static final String DEFAULT_PROTOCOL = "snmp";
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RETRIES = 1;

    /** UPS-MIB OID mapping per RFC 1628. */
    private static final Map<String, String> UPS_OID_MAP = Map.ofEntries(
            Map.entry("input_voltage", "1.3.6.1.2.1.33.1.3.3.1.3.1"),
            Map.entry("output_voltage", "1.3.6.1.2.1.33.1.4.4.1.3.1"),
            Map.entry("battery_level", "1.3.6.1.2.1.33.1.2.4.0"),
            Map.entry("battery_status", "1.3.6.1.2.1.33.1.2.1.0"),
            Map.entry("ups_status", "1.3.6.1.2.1.33.1.4.1.0"),
            Map.entry("output_frequency", "1.3.6.1.2.1.33.1.4.4.1.2.1"),
            Map.entry("input_frequency", "1.3.6.1.2.1.33.1.3.3.1.2.1"),
            Map.entry("load_level", "1.3.6.1.2.1.33.1.4.4.1.5.1")
    );

    private String host;
    private int port = DEFAULT_PORT;
    private String protocol = DEFAULT_PROTOCOL;
    private String community = DEFAULT_COMMUNITY;
    private String version = "2c";
    private int timeout = DEFAULT_TIMEOUT_MS;
    private int retries = DEFAULT_RETRIES;

    public UpsService() {
    }

    // ======================== OID resolution ========================

    public static String resolveOid(String attribute) {
        String oid = UPS_OID_MAP.get(attribute);
        if (oid == null) {
            throw new IllegalArgumentException("Unknown UPS attribute: " + attribute);
        }
        return oid;
    }

    public static Set<String> getSupportedAttributes() {
        return UPS_OID_MAP.keySet();
    }

    int resolveSnmpVersion(String ver) {
        return switch (ver.toLowerCase().trim()) {
            case "1", "v1" -> SnmpConstants.version1;
            case "2c", "v2c" -> SnmpConstants.version2c;
            case "3", "v3" -> SnmpConstants.version3;
            default -> throw new IllegalArgumentException("Invalid SNMP version: " + ver);
        };
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        LOG.info("UpsService started for {}:{}", host, port);
    }

    @Override
    public void stop() {
        LOG.info("UpsService stopped");
    }

    // ======================== connection factory ========================

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new UpsConnection(this);
    }

    // ======================== getters / setters ========================

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getCommunity() { return community; }
    public void setCommunity(String community) { this.community = community; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }

    // ======================== inner connection class ========================

    public static class UpsConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(UpsConnection.class);

        private final UpsService service;
        private Snmp snmp;
        private TransportMapping<UdpAddress> transport;

        public UpsConnection(UpsService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();
        }

        @Override
        public boolean isConnected() {
            return snmp != null;
        }

        @Override
        public void close() {
            try {
                if (snmp != null) snmp.close();
            } catch (Exception e) {
                CONN_LOG.warn("Error closing UPS SNMP session", e);
            }
            snmp = null;
            transport = null;
        }

        /**
         * Reads a UPS attribute via SNMP GET using UPS-MIB OIDs.
         */
        public Variable readAttribute(String attribute) throws Exception {
            String oidStr = UpsService.resolveOid(attribute);
            Target<Address> target = createTarget();
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oidStr)));
            pdu.setType(PDU.GET);

            ResponseEvent<Address> response = snmp.send(pdu, target);
            if (response == null || response.getResponse() == null) {
                throw new Exception("No SNMP response for UPS attribute: " + attribute);
            }
            PDU respPdu = response.getResponse();
            if (respPdu.getErrorStatus() != PDU.noError) {
                throw new Exception("SNMP error: " + respPdu.getErrorStatusText());
            }
            Variable var = respPdu.get(0).getVariable();
            return var.isException() ? null : var;
        }

        private Target<Address> createTarget() {
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(service.getCommunity()));
            target.setAddress(GenericAddress.parse(
                    "udp:" + service.getHost() + "/" + service.getPort()));
            target.setVersion(service.resolveSnmpVersion(service.getVersion()));
            target.setTimeout(service.getTimeout());
            target.setRetries(service.getRetries());
            return target;
        }
    }
}
