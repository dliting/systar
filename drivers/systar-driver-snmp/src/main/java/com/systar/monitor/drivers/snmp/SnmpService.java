package com.systar.monitor.drivers.snmp;

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

/**
 * Active service for SNMP agent communication using SNMP4J.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>{@code host} -- SNMP agent host address</li>
 *   <li>{@code port} -- SNMP agent port (default: 161)</li>
 *   <li>{@code community} -- SNMP community string (default: "public")</li>
 *   <li>{@code version} -- SNMP version: "1", "2c", or "3" (default: "2c")</li>
 *   <li>{@code timeout} -- request timeout in ms (default: 5000)</li>
 *   <li>{@code retries} -- number of retries (default: 1)</li>
 * </ul>
 */
public class SnmpService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpService.class);

    private static final int DEFAULT_PORT = 161;
    private static final String DEFAULT_COMMUNITY = "public";
    private static final String DEFAULT_VERSION = "2c";
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RETRIES = 1;

    private String host;
    private int port = DEFAULT_PORT;
    private String community = DEFAULT_COMMUNITY;
    private String version = DEFAULT_VERSION;
    private int timeout = DEFAULT_TIMEOUT_MS;
    private int retries = DEFAULT_RETRIES;

    public SnmpService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        LOG.info("SnmpService started for {}:{}", host, port);
    }

    @Override
    public void stop() {
        LOG.info("SnmpService stopped");
    }

    // ======================== connection factory ========================

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new SnmpConnection(this);
    }

    // ======================== helpers ========================

    int resolveSnmpVersion(String ver) {
        return switch (ver.toLowerCase().trim()) {
            case "1", "v1" -> SnmpConstants.version1;
            case "2c", "v2c" -> SnmpConstants.version2c;
            case "3", "v3" -> SnmpConstants.version3;
            default -> throw new IllegalArgumentException("Invalid SNMP version: " + ver);
        };
    }

    // ======================== getters / setters ========================

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getCommunity() { return community; }
    public void setCommunity(String community) { this.community = community; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }

    // ======================== inner connection class ========================

    /**
     * SNMP connection wrapping an SNMP4J session with a CommunityTarget.
     */
    public static class SnmpConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(SnmpConnection.class);

        private final SnmpService service;
        private Snmp snmp;
        private TransportMapping<UdpAddress> transport;

        public SnmpConnection(SnmpService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();
            CONN_LOG.debug("SNMP session opened for {}:{}", service.getHost(), service.getPort());
        }

        @Override
        public boolean isConnected() {
            return snmp != null;
        }

        @Override
        public void close() {
            try {
                if (snmp != null) {
                    snmp.close();
                }
            } catch (Exception e) {
                CONN_LOG.warn("Error closing SNMP session", e);
            }
            snmp = null;
            transport = null;
        }

        /**
         * Sends an SNMP GET for the given OID and returns the variable value.
         *
         * @param oid the OID string to query
         * @return the Variable from the response, or null if not available
         * @throws Exception if the SNMP request fails
         */
        public Variable getVar(String oid) throws Exception {
            Target<Address> target = createTarget();
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent<Address> response = snmp.send(pdu, target);
            if (response == null || response.getResponse() == null) {
                return null;
            }
            PDU respPdu = response.getResponse();
            if (respPdu.getErrorStatus() != PDU.noError) {
                throw new Exception("SNMP error: " + respPdu.getErrorStatusText()
                        + " for OID " + oid);
            }
            VariableBinding vb = respPdu.get(0);
            Variable var = vb.getVariable();
            return var.isException() ? null : var;
        }

        private Target<Address> createTarget() {
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(service.getCommunity()));
            target.setAddress(GenericAddress.parse("udp:"
                    + service.getHost() + "/" + service.getPort()));
            target.setVersion(service.resolveSnmpVersion(service.getVersion()));
            target.setTimeout(service.getTimeout());
            target.setRetries(service.getRetries());
            return target;
        }
    }
}
