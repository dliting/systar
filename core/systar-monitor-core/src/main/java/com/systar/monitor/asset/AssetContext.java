package com.systar.monitor.asset;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds global context for the asset model, including state change listeners.
 * <p>
 * This class provides listener management and notification. A full asset store
 * implementation will be added in a later iteration.
 */
public class AssetContext {

    private final List<AssetStateListener> listeners = new CopyOnWriteArrayList<>();

    /** Registers a state change listener. */
    public void addStateListener(AssetStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Removes a previously registered state change listener. */
    public void removeStateListener(AssetStateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all registered listeners of a state change.
     *
     * @param asset     the asset whose state changed
     * @param oldState  the previous state
     * @param newState  the new state
     */
    public void notifyStateChange(Asset<?> asset, AssetState oldState, AssetState newState) {
        for (AssetStateListener listener : listeners) {
            listener.onStateChanged(asset, oldState, newState);
        }
    }
}
