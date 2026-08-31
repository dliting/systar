package com.systar.common.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory code dictionary manager.
 *
 * <p>Holds a collection of {@link CodeCatalog} instances indexed by catalog ID.
 * Pure Java implementation with no framework dependencies.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}.
 */
public class CodeDictManager {

    private final Map<Integer, CodeCatalog> catalogs = new ConcurrentHashMap<>();

    /**
     * Initialize (or replace) the catalog data.
     *
     * @param dataMap mapping from catalog ID to catalog instance
     */
    public void loadCatalogs(Map<Integer, CodeCatalog> dataMap) {
        catalogs.clear();
        if (dataMap != null) {
            catalogs.putAll(dataMap);
        }
    }

    /**
     * Get a catalog by its ID.
     *
     * @param catalogId catalog ID
     * @return the catalog, or null if not found
     */
    public CodeCatalog getCatalog(int catalogId) {
        return catalogs.get(catalogId);
    }

    /**
     * Get a specific code item from a catalog.
     *
     * @param catalogId catalog ID
     * @param itemId    item ID
     * @return the item, or null if the catalog or item does not exist
     */
    public CodeItem getItem(int catalogId, int itemId) {
        CodeCatalog catalog = catalogs.get(catalogId);
        return catalog != null ? catalog.getItem(itemId) : null;
    }

    /**
     * Get the caption of a code item, falling back to an empty string.
     *
     * @param catalogId catalog ID
     * @param itemId    item ID
     * @return caption text, or empty string if not found
     */
    public String getItemCaption(int catalogId, int itemId) {
        CodeItem item = getItem(catalogId, itemId);
        return item != null ? item.getCaption() : "";
    }
}
