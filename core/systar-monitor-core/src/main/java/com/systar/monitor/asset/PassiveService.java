package com.systar.monitor.asset;

import com.systar.monitor.result.ResultDispatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive monitor service that receives pushed data from external sources.
 * <p>
 * Maintains a registry of {@link IPassiveMonitor} instances keyed by their
 * routing key, enabling incoming data to be dispatched to the correct monitor.
 */
public abstract class PassiveService extends MonitorService {

    /** registerKey -> Monitor mapping. */
    private final Map<String, Monitor<?>> monitorDict = new ConcurrentHashMap<>();

    /** monitorId -> registerKey reverse mapping. */
    private final Map<Integer, String> reverseMonitorDict = new ConcurrentHashMap<>();

    /** Result dispatcher for handling incoming monitor data. */
    private ResultDispatcher resultDispatcher;

    public PassiveService() {
        super(MonitorMode.PASSIVE);
    }

    // ======================== registry ========================

    /**
     * Registers a monitor with the given routing key.
     *
     * @param key     the unique routing key
     * @param monitor the monitor to register
     * @throws RuntimeException if the key is already registered by a different monitor
     */
    public void registerMonitor(String key, Monitor<?> monitor) {
        if (key == null || monitor == null) {
            return;
        }
        Monitor<?> existing = monitorDict.get(key);
        if (existing != null && existing != monitor) {
            throw new RuntimeException(
                    "Failed to register monitor: key '" + key + "' already in use by monitor " + existing.getId());
        }
        monitorDict.put(key, monitor);
        reverseMonitorDict.put(monitor.getId(), key);
    }

    /**
     * Unregisters the monitor associated with the given key.
     *
     * @param key the routing key to unregister
     * @return the previously associated monitor, or null
     */
    public Monitor<?> unregisterMonitor(String key) {
        if (key == null) {
            return null;
        }
        Monitor<?> m = monitorDict.remove(key);
        if (m != null) {
            reverseMonitorDict.remove(m.getId());
        }
        return m;
    }

    /**
     * Finds a monitor by its routing key.
     *
     * @param key the routing key
     * @return the monitor, or null if not found
     */
    public Monitor<?> getMonitor(String key) {
        return monitorDict.get(key);
    }

    /**
     * Finds the routing key for a given monitor ID.
     *
     * @param monitorId the monitor ID
     * @return the routing key, or null if not found
     */
    public String getKey(int monitorId) {
        return reverseMonitorDict.get(monitorId);
    }

    // ======================== result dispatcher ========================

    public ResultDispatcher getResultDispatcher() {
        return resultDispatcher;
    }

    /**
     * Sets the result dispatcher for this service.
     *
     * @param resultDispatcher the dispatcher to handle incoming data
     */
    public void setResultDispatcher(ResultDispatcher resultDispatcher) {
        this.resultDispatcher = resultDispatcher;
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
}
