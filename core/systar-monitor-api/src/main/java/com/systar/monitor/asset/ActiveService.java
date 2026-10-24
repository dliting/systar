package com.systar.monitor.asset;

import java.util.ArrayList;
import java.util.List;

/**
 * Active monitor service that polls its monitors at fixed intervals.
 * <p>
 * Manages a pool of {@link MonitorConnection} instances shared among
 * all monitors. Connections are checked out, used for detection, and
 * returned to the pool.
 */
public abstract class ActiveService extends MonitorService {

    private static final int DEFAULT_MAX_CONNECTIONS = 10;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;

    /** Lock object used for connection pool synchronization. */
    private Object connectionLock;

    private final List<MonitorConnection> freeConnections = new ArrayList<>();
    private final List<MonitorConnection> usedConnections = new ArrayList<>();

    public ActiveService() {
        super(MonitorMode.ACTIVE);
        this.connectionLock = this;
    }

    // ======================== connection pool ========================

    /**
     * Retrieves a connection from the pool.
     * <p>
     * If no free connection is available and the pool has not reached
     * {@link #maxConnections}, a new connection is created via
     * {@link #createConnection()}. If the pool is full, this method
     * blocks until a connection is released.
     *
     * @return a connected {@link MonitorConnection}
     * @throws Exception if connection creation fails
     */
    public MonitorConnection getConnection() throws Exception {
        synchronized (connectionLock) {
            if (freeConnections.size() + usedConnections.size() < maxConnections) {
                MonitorConnection c = createConnection();
                freeConnections.add(c);
            }
            while (freeConnections.isEmpty()) {
                connectionLock.wait();
            }

            MonitorConnection c = freeConnections.remove(freeConnections.size() - 1);
            // Automatically reconnect if stale
            if (!c.isConnected()) {
                c.open();
            }
            usedConnections.add(c);
            return c;
        }
    }

    /**
     * Returns a connection to the pool.
     *
     * @param connection the connection to release
     */
    public void releaseConnection(MonitorConnection connection) {
        if (connection == null) {
            return;
        }
        synchronized (connectionLock) {
            usedConnections.remove(connection);
            freeConnections.add(connection);
            connectionLock.notifyAll();
        }
    }

    /**
     * Sets the lock object used for connection pool synchronization.
     * <p>
     * This allows multiple services to share a single connection pool
     * by using the same lock object (e.g., a shared gateway).
     *
     * @param lock the lock object, must not be null
     */
    public void setConnectionLock(Object lock) {
        if (lock == null) {
            throw new IllegalArgumentException("Connection lock must not be null.");
        }
        this.connectionLock = lock;
    }

    public Object getConnectionLock() {
        return connectionLock;
    }

    // ======================== configuration ========================

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections > 0 ? maxConnections : 1;
    }

    // ======================== Asset ========================

    @Override
    public AssetKind getKind() {
        return AssetKind.SERVICE;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // ======================== abstract factory ========================

    /**
     * Creates a new connection for this service.
     * <p>
     * Subclasses implement the concrete connection mechanism
     * (e.g., TCP socket, HTTP client, serial port).
     *
     * @return a new, unopened connection
     * @throws Exception if connection creation fails
     */
    public abstract MonitorConnection createConnection() throws Exception;
}
