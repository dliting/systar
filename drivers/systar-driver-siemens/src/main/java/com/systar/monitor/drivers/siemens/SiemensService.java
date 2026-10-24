package com.systar.monitor.drivers.siemens;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7Connector;
import com.github.s7connector.api.factory.S7ConnectorFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active service for Siemens S7 PLC communication via S7Connector.
 * Reads raw bytes from PLC memory and converts to typed values.
 */
public class SiemensService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(SiemensService.class);

    private static final int DEFAULT_PORT = 102;
    private static final int DEFAULT_RACK = 0;
    private static final int DEFAULT_SLOT = 1;

    // S7 data type identifiers
    static final String DATA_BOOL = "bool";
    static final String DATA_BYTE = "byte";
    static final String DATA_INT = "int";
    static final String DATA_WORD = "word";
    static final String DATA_DWORD = "dword";
    static final String DATA_DINT = "dint";
    static final String DATA_REAL = "real";

    // S7 memory area identifiers
    static final String AREA_DB = "DB";
    static final String AREA_FLAGS = "M";
    static final String AREA_INPUTS = "I";
    static final String AREA_OUTPUTS = "Q";

    private String host;
    private int port = DEFAULT_PORT;
    private int rack = DEFAULT_RACK;
    private int slot = DEFAULT_SLOT;

    public SiemensService() {
    }

    @Override
    public void start() throws Exception {
        LOG.info("SiemensService started for {}:{}", host, port);
    }

    @Override
    public void stop() {
        LOG.info("SiemensService stopped");
    }

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new SiemensConnection(this);
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getRack() { return rack; }
    public void setRack(int rack) { this.rack = rack; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    // ======================== inner connection class ========================

    public static class SiemensConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(SiemensConnection.class);

        private final SiemensService service;
        private S7Connector connector;

        public SiemensConnection(SiemensService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            connector = S7ConnectorFactory.buildTCPConnector()
                    .withHost(service.getHost())
                    .withPort(service.getPort())
                    .withRack(service.getRack())
                    .withSlot(service.getSlot())
                    .build();
            CONN_LOG.debug("S7 connection opened to {}:{}",
                    service.getHost(), service.getPort());
        }

        @Override
        public boolean isConnected() {
            return connector != null;
        }

        @Override
        public void close() {
            if (connector != null) {
                try { connector.close(); } catch (Exception e) {
                    CONN_LOG.warn("Error closing S7 connection", e);
                }
                connector = null;
            }
        }

        /**
         * Reads a typed value from PLC DB memory.
         */
        public Object read(String area, int dbNumber, int byteOffset, int bitOffset,
                           String dataType) throws Exception {
            DaveArea daveArea = resolveArea(area);

            int len = switch (dataType) {
                case DATA_BOOL, DATA_BYTE -> 1;
                case DATA_INT, DATA_WORD -> 2;
                case DATA_REAL, DATA_DWORD, DATA_DINT -> 4;
                default -> 1;
            };

            byte[] raw = connector.read(daveArea, dbNumber, byteOffset, len);

            return switch (dataType) {
                case DATA_BOOL  -> ((raw[0] >> bitOffset) & 1) == 1;
                case DATA_BYTE  -> raw[0];
                case DATA_INT   -> (short) (((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF));
                case DATA_WORD  -> ((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF);
                case DATA_DWORD -> ((long) (raw[0] & 0xFF) << 24)
                        | ((long) (raw[1] & 0xFF) << 16)
                        | ((long) (raw[2] & 0xFF) << 8)
                        | (raw[3] & 0xFF);
                case DATA_REAL  -> Float.intBitsToFloat(
                        ((raw[0] & 0xFF) << 24) | ((raw[1] & 0xFF) << 16)
                                | ((raw[2] & 0xFF) << 8) | (raw[3] & 0xFF));
                default -> raw;
            };
        }

        private DaveArea resolveArea(String area) {
            return switch (area) {
                case AREA_DB -> DaveArea.DB;
                case AREA_FLAGS -> DaveArea.FLAGS;
                case AREA_INPUTS -> DaveArea.INPUTS;
                case AREA_OUTPUTS -> DaveArea.OUTPUTS;
                default -> throw new IllegalArgumentException("Unknown S7 area: " + area);
            };
        }
    }
}
