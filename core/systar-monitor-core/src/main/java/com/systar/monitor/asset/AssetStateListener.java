package com.systar.monitor.asset;

/**
 * Callback interface for observing state changes on assets.
 */
public interface AssetStateListener {

    /**
     * Called when an asset's state changes.
     *
     * @param asset     the asset whose state changed
     * @param oldState  the previous state
     * @param newState  the new state
     */
    void onStateChanged(Asset<?> asset, AssetState oldState, AssetState newState);
}
