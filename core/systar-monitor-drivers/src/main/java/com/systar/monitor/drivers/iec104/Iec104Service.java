package com.systar.monitor.drivers.iec104;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;
import org.openmuc.j60870.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Active service for IEC 60870-5-104 protocol using j60870.
 * <p>
 * Manages connections to IEC 104 remote stations (RTUs). Each connection
 * wraps a j60870 {@link Connection} that receives spontaneous ASDUs and
 * caches data point values keyed by information object address (IOA).
 * <p>
 * Probes read from the cache via {@link Iec104Connection#read(int)}.
 */
public class Iec104Service extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(Iec104Service.class);

    static final int DEFAULT_PORT = 2404;
    static final int DEFAULT_TIMEOUT_MS = 10000;
    static final int DEFAULT_COMMON_ADDRESS = 1;
    /** Station interrogation qualifier (general interrogation). */
    static final int STATION_INTERROGATION_QUALIFIER = 20;
    /** Command qualifier — 0 = unspecified pulse, sufficient for boolean/setpoint commands. */
    static final int DEFAULT_COMMAND_QUALIFIER = 0;
    /** Command select flag — false = execute directly (not select-then-execute). */
    static final boolean DEFAULT_COMMAND_SELECT = false;

    private String host;
    private int port = DEFAULT_PORT;
    /** Common ASDU address of the controlled station (RTU). */
    private int commonAsduAddress = DEFAULT_COMMON_ADDRESS;
    /** Originator address (client address) included in sent ASDUs. */
    private int originatorAddress;
    private int timeout = DEFAULT_TIMEOUT_MS;

    public Iec104Service() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        LOG.info("Iec104Service started for {}:{}", host, port);
    }

    @Override
    public void stop() {
        LOG.info("Iec104Service stopped");
    }

    // ======================== connection factory ========================

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new Iec104Connection(this);
    }

    // ======================== getters / setters ========================

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getCommonAsduAddress() { return commonAsduAddress; }
    public void setCommonAsduAddress(int commonAsduAddress) { this.commonAsduAddress = commonAsduAddress; }

    public int getOriginatorAddress() { return originatorAddress; }
    public void setOriginatorAddress(int originatorAddress) { this.originatorAddress = originatorAddress; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    // ======================== inner connection class ========================

    /**
     * IEC 104 connection backed by a j60870 {@link Connection}.
     * <p>
     * On {@link #open()} a j60870 client connection is established and data
     * transfer is started. Incoming ASDUs are decoded and their values are
     * stored in an address-indexed cache.
     */
    public static class Iec104Connection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(Iec104Connection.class);

        private final Iec104Service service;
        private Connection connection;
        private final ConcurrentHashMap<Integer, Object> dataCache = new ConcurrentHashMap<>();

        public Iec104Connection(Iec104Service service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            InetAddress addr = InetAddress.getByName(service.getHost());
            connection = new ClientConnectionBuilder(addr)
                    .setPort(service.getPort())
                    .connect();

            if (service.getOriginatorAddress() != 0) {
                connection.setOriginatorAddress(service.getOriginatorAddress());
            }

            connection.startDataTransfer(new ConnectionEventListener() {
                @Override
                public void newASdu(ASdu aSdu) {
                    decodeAndCache(aSdu);
                }

                @Override
                public void connectionClosed(IOException e) {
                    CONN_LOG.warn("IEC 104 connection closed: {}",
                            e != null ? e.getMessage() : "closed by remote");
                }
            }, service.getTimeout());

            // Send station interrogation to populate initial cache
            connection.interrogation(service.getCommonAsduAddress(),
                    CauseOfTransmission.ACTIVATION,
                    new IeQualifierOfInterrogation(STATION_INTERROGATION_QUALIFIER));

            CONN_LOG.info("IEC 104 connection established to {}:{}",
                    service.getHost(), service.getPort());
        }

        @Override
        public boolean isConnected() {
            return connection != null;
        }

        @Override
        public void close() {
            if (connection != null) {
                connection.close();
                connection = null;
            }
            dataCache.clear();
        }

        /**
         * Reads the latest cached value for the given information object address.
         *
         * @param address the information object address (IOA)
         * @return the cached value, or null if no data received yet
         */
        public Object read(int address) {
            return dataCache.get(address);
        }

        /**
         * Sends a control command to the remote station.
         * <p>
         * Boolean values use {@link TypeId#C_SC_NA_1} (single command).
         * Float values use {@link TypeId#C_SE_NC_1} (set short-float command).
         */
        public void write(int commonAddr, int address, Object value) throws Exception {
            if (connection == null) {
                throw new IllegalStateException("IEC 104 connection is not open");
            }
            int ca = commonAddr > 0 ? commonAddr : service.getCommonAsduAddress();
            if (value instanceof Boolean b) {
                connection.singleCommand(ca, CauseOfTransmission.ACTIVATION, address,
                        new IeSingleCommand(b, DEFAULT_COMMAND_QUALIFIER, DEFAULT_COMMAND_SELECT));
            } else if (value instanceof Number n) {
                connection.setShortFloatCommand(ca, CauseOfTransmission.ACTIVATION, address,
                        new IeShortFloat(n.floatValue()),
                        new IeQualifierOfSetPointCommand(DEFAULT_COMMAND_QUALIFIER, DEFAULT_COMMAND_SELECT));
            } else if (value instanceof String s) {
                if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                    connection.singleCommand(ca, CauseOfTransmission.ACTIVATION, address,
                            new IeSingleCommand(Boolean.parseBoolean(s),
                                    DEFAULT_COMMAND_QUALIFIER, DEFAULT_COMMAND_SELECT));
                } else {
                    connection.setShortFloatCommand(ca, CauseOfTransmission.ACTIVATION, address,
                            new IeShortFloat(Float.parseFloat(s)),
                            new IeQualifierOfSetPointCommand(
                                    DEFAULT_COMMAND_QUALIFIER, DEFAULT_COMMAND_SELECT));
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported IEC 104 write value type: "
                                + (value == null ? "null" : value.getClass().getName()));
            }
        }

        // ======================== ASDU decoding ========================

        void decodeAndCache(ASdu aSdu) {
            TypeId typeId = aSdu.getTypeIdentification();
            for (InformationObject io : aSdu.getInformationObjects()) {
                int ioa = io.getInformationObjectAddress();
                InformationElement[][] elements = io.getInformationElements();
                if (elements.length == 0 || elements[0].length == 0) {
                    continue;
                }
                InformationElement ie = elements[0][0];

                if (isYcTelemetry(typeId) && ie instanceof IeShortFloat sf) {
                    dataCache.put(ioa, sf.getValue());
                    CONN_LOG.debug("YC update: address={}, value={}", ioa, sf.getValue());
                } else if (isYxSignal(typeId) && ie instanceof IeSinglePointWithQuality sp) {
                    dataCache.put(ioa, sp.isOn());
                    CONN_LOG.debug("YX update: address={}, on={}", ioa, sp.isOn());
                }
            }
        }

        static boolean isYcTelemetry(TypeId typeId) {
            return typeId == TypeId.M_ME_NC_1 || typeId == TypeId.M_ME_TF_1
                    || typeId == TypeId.M_ME_NA_1 || typeId == TypeId.M_ME_TA_1
                    || typeId == TypeId.M_ME_NB_1 || typeId == TypeId.M_ME_TB_1
                    || typeId == TypeId.M_ME_ND_1;
        }

        static boolean isYxSignal(TypeId typeId) {
            return typeId == TypeId.M_SP_NA_1 || typeId == TypeId.M_SP_TB_1
                    || typeId == TypeId.M_SP_TA_1
                    || typeId == TypeId.M_DP_NA_1 || typeId == TypeId.M_DP_TB_1
                    || typeId == TypeId.M_DP_TA_1;
        }

        int cacheSize() {
            return dataCache.size();
        }
    }
}
