package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;
import com.systar.monitor.asset.type.AssetTypeProperty;
import com.systar.monitor.asset.type.ServiceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modbus TCP active service that manages a connection pool.
 * <p>
 * Configuration is resolved in priority order:
 * <ol>
 *   <li>Asset instance metadata (runtime overrides)</li>
 *   <li>Type property default values (design-time configuration)</li>
 *   <li>Built-in defaults</li>
 * </ol>
 * <p>
 * Recognized configuration keys:
 * <ul>
 *   <li>{@code host} - Modbus TCP host (default: "127.0.0.1")</li>
 *   <li>{@code port} - Modbus TCP port (default: 502)</li>
 *   <li>{@code unitId} - Modbus slave unit ID (default: 1)</li>
 *   <li>{@code timeout} - Socket timeout in milliseconds (default: 5000)</li>
 *   <li>{@code maxConnections} - Connection pool size (default: 10)</li>
 * </ul>
 */
public class ModbusService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusService.class);

    // Default configuration
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 502;
    private static final int DEFAULT_UNIT_ID = 1;
    private static final int DEFAULT_TIMEOUT = 5000;
    private static final int DEFAULT_MAX_CONNECTIONS = 10;

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

    /**
     * Resolves configuration from type property defaults and runtime metadata.
     * <p>
     * Priority: metadata (runtime) > type property defaults > built-in defaults.
     */
    private void resolveConfig() {
        // Build a config map from type property defaults
        Map<String, Object> configDefaults = new java.util.HashMap<>();
        ServiceType type = getType();
        if (type != null && type.getProperties() != null) {
            for (AssetTypeProperty prop : type.getProperties()) {
                if (prop.getName() != null && prop.getDefaultValue() != null) {
                    configDefaults.put(prop.getName(), prop.getDefaultValue());
                }
            }
        }

        // Read from type property defaults first
        this.host = getString(configDefaults, "host", DEFAULT_HOST);
        this.port = getInt(configDefaults, "port", DEFAULT_PORT);
        this.unitId = getInt(configDefaults, "unitId", DEFAULT_UNIT_ID);
        this.timeout = getInt(configDefaults, "timeout", DEFAULT_TIMEOUT);
        setMaxConnections(getInt(configDefaults, "maxConnections", DEFAULT_MAX_CONNECTIONS));

        // Override from runtime metadata if present
        Object metaHost = getMetadata("host");
        if (metaHost != null) this.host = metaHost.toString();

        Object metaPort = getMetadata("port");
        if (metaPort != null) this.port = toInt(metaPort, this.port);

        Object metaUnitId = getMetadata("unitId");
        if (metaUnitId != null) this.unitId = toInt(metaUnitId, this.unitId);

        Object metaTimeout = getMetadata("timeout");
        if (metaTimeout != null) this.timeout = toInt(metaTimeout, this.timeout);

        Object metaMaxConn = getMetadata("maxConnections");
        if (metaMaxConn != null) setMaxConnections(toInt(metaMaxConn, getMaxConnections()));
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
        resolveConfig();
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

    // ======================== helpers ========================

    private static String getString(Map<String, Object> props, String key, String defaultVal) {
        Object val = props.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static int getInt(Map<String, Object> props, String key, int defaultVal) {
        Object val = props.get(key);
        if (val == null) return defaultVal;
        return toInt(val, defaultVal);
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
