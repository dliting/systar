package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ServiceType;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract base class for monitor services.
 * <p>
 * A service manages a group of {@link Monitor} instances and defines the
 * communication model: active (polling) via {@link ActiveService}, or
 * passive (push) via {@link PassiveService}.
 */
public abstract class MonitorService extends Asset<ServiceType> {

    private final MonitorMode mode;
    private final List<Monitor<?>> monitors = new CopyOnWriteArrayList<>();

    protected MonitorService(MonitorMode mode) {
        this.mode = mode;
    }

    // ======================== monitor management ========================

    /**
     * Adds a monitor to this service.
     *
     * @param monitor the monitor to add
     */
    public void addMonitor(Monitor<?> monitor) {
        if (monitor != null && !monitors.contains(monitor)) {
            monitors.add(monitor);
        }
    }

    /**
     * Removes a monitor from this service.
     *
     * @param monitor the monitor to remove
     */
    public void removeMonitor(Monitor<?> monitor) {
        monitors.remove(monitor);
    }

    /**
     * Returns all monitors managed by this service.
     *
     * @return unmodifiable view of monitors
     */
    public Collection<Monitor<?>> getMonitors() {
        return List.copyOf(monitors);
    }

    // ======================== accessors ========================

    public MonitorMode getMode() {
        return mode;
    }

    // ======================== Asset overrides ========================

    @Override
    public boolean isCompound() {
        return false;
    }

    @Override
    public boolean isMonitor() {
        return false;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // ======================== lifecycle (subclass implements) ========================

    /**
     * Starts this service (opens connections, registers passive listeners, etc.).
     *
     * @throws Exception if start-up fails
     */
    public abstract void start() throws Exception;

    /**
     * Stops this service (closes connections, unregisters listeners, etc.).
     */
    public abstract void stop();
}
