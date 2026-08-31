package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetStore;

/**
 * Plugin-style extension point for loading asset type definitions.
 * <p>
 * Implementations read type metadata from a configuration source (e.g. XML,
 * properties, or a driver module's own descriptor), register the resulting
 * {@link AssetType} instances into the {@link AssetStore}'s type managers, and
 * may synchronize the definitions to a persistent store.
 * <p>
 * Spring beans implementing this interface are automatically collected by
 * {@code MonitorServer} via {@code @Autowired List<AssetTypeLoader>}.
 */
@FunctionalInterface
public interface AssetTypeLoader {

    /**
     * Load asset type definitions and register them with the given store.
     *
     * @param assetStore the in-memory asset store whose type managers receive
     *                   the parsed type definitions
     */
    void load(AssetStore assetStore);
}
