package com.systar.monitor.asset.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for {@link AssetType} instances, keyed by name.
 * <p>
 * Each asset kind (Space, Device, Probe, etc.) typically gets its own
 * {@code AssetTypeManager} instance so that types can be looked up quickly
 * at runtime.
 * <p>
 * <b>Registration is startup-only.</b> Types are loaded during application
 * startup by {@link com.systar.monitor.asset.type.AssetTypeLoader} implementations.
 * Once loading is complete, the registry is treated as immutable at runtime.
 * The {@link #register(Object)} method is not thread-safe with respect to
 * concurrent {@link #find(String)} calls from scheduling threads — this is
 * acceptable because registration only happens during the single-threaded
 * startup phase.
 *
 * @param <T> the concrete {@link AssetType} subclass managed by this container
 */
public class AssetTypeManager<T extends AssetType> {

    private final ConcurrentHashMap<String, T> types = new ConcurrentHashMap<>();

    /** Insertion-ordered list kept in sync with the map for ordered iteration. */
    private final List<T> ordered = Collections.synchronizedList(new ArrayList<>());

    /**
     * Registers a new type.
     *
     * @param type the type to register
     * @throws IllegalArgumentException if a type with the same name already exists
     */
    public void register(T type) {
        if (type == null) {
            throw new IllegalArgumentException("Type must not be null.");
        }
        T existing = types.putIfAbsent(type.getName(), type);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate type: " + type.getName());
        }
        ordered.add(type);
    }

    /**
     * Finds a type by name.
     *
     * @param name the type name
     * @return the matching type, or {@code null} if not found
     */
    public T find(String name) {
        if (name == null) return null;
        return types.get(name);
    }

    /**
     * Returns all registered types in insertion order.
     *
     * @return unmodifiable collection of all types
     */
    public Collection<T> getAll() {
        return List.copyOf(ordered);
    }

    /** Returns the number of registered types. */
    public int size() {
        return types.size();
    }
}
