package com.systar.monitor.asset;

import com.systar.monitor.asset.type.AssetType;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Abstract base class for compound (container) assets that hold children.
 * <p>
 * Uses a thread-safe {@link ConcurrentSkipListMap} keyed by child name.
 * Subclasses ({@code Space}, {@code Device}) provide the concrete asset kind
 * and visitor dispatch.
 *
 * @param <T> the concrete {@link AssetType} of this asset
 */
public abstract class CompoundAsset<T extends AssetType> extends Asset<T> {

    private final ConcurrentSkipListMap<String, Asset<?>> childDict =
            new ConcurrentSkipListMap<>();

    protected CompoundAsset() {
        super();
    }

    // ======================== child management ========================

    /**
     * Adds a child asset. The child must not be null and must not already
     * have a parent. Duplicate names are rejected.
     *
     * @param child the asset to add as a child
     * @throws AssetException if a child with the same name already exists
     */
    public void addChild(Asset<?> child) {
        if (child == null) {
            throw new AssetException("Child must not be null.");
        }
        if (child.getParent() != null) {
            throw new AssetException("Child already has a parent: " + child.getName());
        }
        Asset<?> existed = childDict.putIfAbsent(child.getName(), child);
        if (existed != null) {
            throw new AssetException(
                    "Duplicate child asset '" + child.getName() + "' in " + getId() + ".");
        }
        child.setParent(this);
    }

    /**
     * Removes a child by name.
     *
     * @param name the name of the child to remove
     * @return the removed asset, or {@code null} if not found
     */
    public Asset<?> removeChild(String name) {
        Asset<?> child = childDict.remove(name);
        if (child != null) {
            child.setParent(null);
        }
        return child;
    }

    /**
     * Gets a direct child by name.
     *
     * @param name the child name
     * @return the child asset, or {@code null} if not found
     */
    public Asset<?> getChild(String name) {
        return childDict.get(name);
    }

    /**
     * Returns an unmodifiable view of all direct children.
     *
     * @return unmodifiable collection of child assets
     */
    public Collection<Asset<?>> children() {
        return Collections.unmodifiableCollection(childDict.values());
    }

    /**
     * Recursively searches the entire subtree for a child with the given name.
     *
     * @param name the name to search for
     * @return the matching asset, or {@code null} if not found
     */
    public Asset<?> findChild(String name) {
        // Check direct children first
        Asset<?> direct = childDict.get(name);
        if (direct != null) {
            return direct;
        }
        // Recurse into compound children
        for (Asset<?> child : childDict.values()) {
            if (child instanceof CompoundAsset<?> compound) {
                Asset<?> found = compound.findChild(name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ======================== state management ========================

    /**
     * Recomputes this asset's state by taking the most severe state among
     * all direct children.
     */
    @Override
    protected void recomputeStateFromChildren() {
        updateStateByChildren();
    }

    /**
     * Traverses all direct children and sets this asset's state to the most
     * severe one, using {@link AssetState#max(AssetState, AssetState)}.
     */
    public void updateStateByChildren() {
        AssetState worst = null;
        for (Asset<?> child : childDict.values()) {
            worst = AssetState.max(worst, child.getState());
        }
        if (worst != null) {
            // Set directly to avoid re-triggering bubble-up
            super.setState(worst);
        }
    }

    // ======================== kind flags ========================

    @Override
    public boolean isCompound() {
        return true;
    }

    @Override
    public boolean isMonitor() {
        return false;
    }
}
