package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Modbus TCP active service that manages a connection pool.
 * <p>
 * Configuration binds through the framework's reflective property binding
 * ({@link com.systar.monitor.asset.Asset#bindProperties()}), in priority
 * order: instance metadata (runtime overrides), then type property defaults
 * (design-time configuration), then the field initial values below
 * (built-in defaults). XML property names: {@code Host}, {@code Port},
 * {@code UnitId}, {@code MaxConnections}, {@code Timeout}.
 */
public class ModbusService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusService.class);

    // Default configuration
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 502;
    private static final int DEFAULT_UNIT_ID = 1;
    private static final int DEFAULT_TIMEOUT = 5000;

    // Resolved configuration
    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private int unitId = DEFAULT_UNIT_ID;
    private int timeout = DEFAULT_TIMEOUT;

    // Track all created connections for cleanup
    private final List<MonitorConnection> allConnections = new ArrayList<>();

    public ModbusService() {
        super();
    }

    // ======================== ActiveService ========================

    @Override
    public MonitorConnection createConnection() {
        ModbusConnection conn = new ModbusConnection(host, port, unitId, timeout);
        synchronized (allConnections) {
            allConnections.add(conn);
        }
        return conn;
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        LOG.info("Starting Modbus service: {}:{} [unitId={}, maxConnections={}]",
                host, port, unitId, getMaxConnections());

        // Pre-validate by opening a single connection
        MonitorConnection testConn = createConnection();
        try {
            testConn.open();
        } finally {
            testConn.close();
            synchronized (allConnections) {
                allConnections.remove(testConn);
            }
        }
        LOG.info("Modbus service started successfully: {}:{}", host, port);
    }

    @Override
    public void stop() {
        LOG.info("Stopping Modbus service: {}:{}", host, port);

        // Close all tracked connections
        synchronized (allConnections) {
            for (MonitorConnection conn : allConnections) {
                try {
                    conn.close();
                } catch (Exception e) {
                    LOG.warn("Error closing Modbus connection", e);
                }
            }
            allConnections.clear();
        }
        LOG.info("Modbus service stopped: {}:{}", host, port);
    }

    // ======================== accessors ========================

    /**
     * Returns a typed ModbusConnection from the connection pool.
     */
    public ModbusConnection getModbusConnection() throws Exception {
        return (ModbusConnection) getConnection();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getUnitId() {
        return unitId;
    }

    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "ModbusService[" + host + ":" + port + ", unitId=" + unitId + "]";
    }
}
