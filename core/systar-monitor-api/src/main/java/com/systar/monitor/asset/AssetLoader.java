package com.systar.monitor.asset;

/**
 * Strategy interface for loading assets into an {@link AssetStore}.
 * <p>
 * Implementations encapsulate a specific loading mechanism (e.g. database
 * query, XML file parse, REST API call) and populate the given store.
 */
@FunctionalInterface
public interface AssetLoader {

    /**
     * Loads assets into the given store.
     *
     * @param store the target asset store to populate
     * @throws AssetException if loading fails
     */
    void load(AssetStore store);
}
