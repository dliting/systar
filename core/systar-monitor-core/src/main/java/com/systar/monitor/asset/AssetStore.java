package com.systar.monitor.asset;

import com.systar.monitor.asset.type.*;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory asset repository that maintains a flat index by id and a tree
 * hierarchy mounted on a single neutral {@link CompoundAsset} anchor.
 *
 * <h3>Root structure</h3>
 * The constructor creates a <em>neutral tree anchor</em> (id=-1, empty name)
 * that is not part of the asset kind domain. Real assets from the database
 * carry {@code parent_id = 0} ({@link Asset#INVALID_ID}), which causes
 * {@link #addAsset(Asset)} to attach them under this anchor. The anchor is
 * never placed in the flat index, so it cannot leak into
 * {@link #getAssets()} or dashboards.
 * <p>
 * Also holds {@link AssetTypeManager} instances for each asset kind, populated
 * by {@link AssetTypeLoader} implementations during startup.
 * <p>
 * Thread-safe via {@link ConcurrentHashMap} for the asset index and delegated
 * concurrency controls in {@link CompoundAsset}.
 */
@Component
public class AssetStore extends AssetContext {

    /** Separator used by {@link #getFullPath(Asset)}. */
    public static final String PATH_SEPARATOR = "->";

    /** Root id constant for the neutral tree anchor. */
    private static final int ROOT_ID = -1;

    /** Flat index: asset id -> asset. */
    private final ConcurrentHashMap<Integer, Asset<?>> assets = new ConcurrentHashMap<>();

    /** The single neutral tree anchor (never stored in {@link #assets}). */
    private volatile CompoundAsset<AssetType> root;

    /** Type registries, populated by AssetTypeLoaders during startup. */
    private final AssetTypeManager<DeviceType> deviceTypes = new AssetTypeManager<>();
    private final AssetTypeManager<ServiceType> serviceTypes = new AssetTypeManager<>();
    private final AssetTypeManager<ProbeType> probeTypes = new AssetTypeManager<>();
    private final AssetTypeManager<ControlType> controlTypes = new AssetTypeManager<>();

    // ======================== lifecycle ========================

    public AssetStore() {
        root = newAnchor(this);
    }

    /**
     * Neutral tree anchor: a compound mount point that is not part of the asset
     * kind domain. Top-level assets (parent_id = 0) attach here. It is never
     * placed in the flat index, so it cannot leak into getAssets()/dashboards.
     */
    private static final class TreeAnchor extends CompoundAsset<AssetType> {

        @Override
        public AssetKind getKind() {
            return null;
        }

        @Override
        public <R> R accept(AssetVisitor<R> visitor) {
            throw new UnsupportedOperationException("Tree anchor is not part of the asset kind domain.");
        }
    }

    private static TreeAnchor newAnchor(AssetContext context) {
        TreeAnchor anchor = new TreeAnchor();
        anchor.setId(ROOT_ID);
        anchor.setName("");
        anchor.setCaption("");
        anchor.setContext(context);
        return anchor;
    }

    // ======================== add / remove ========================

    /**
     * Adds an asset to the store and attaches it to its parent.
     * <p>
     * The parent is resolved from {@code asset.getParentId()}. If the parent id
     * is {@link Asset#INVALID_ID}, the asset is attached directly to the root.
     *
     * @param asset the asset to add
     * @throws AssetException if the asset or parent is invalid
     */
    public void addAsset(Asset<?> asset) {
        if (asset == null) {
            throw new AssetException("Asset must not be null.");
        }
        if (asset.getId() == ROOT_ID) {
            throw new AssetException("Asset id %d is reserved for the tree anchor.".formatted(ROOT_ID));
        }

        // Atomic put-if-absent to prevent duplicate under concurrent access
        Asset<?> existing = assets.putIfAbsent(asset.getId(), asset);
        if (existing != null) {
            throw new AssetException("Asset already exists (id = %d).".formatted(asset.getId()));
        }

        // Set context so state changes propagate through this store
        asset.setContext(this);

        // Resolve parent and attach
        int parentId = asset.getParentId();
        Asset<?> parent = (parentId == Asset.INVALID_ID) ? root : assets.get(parentId);
        if (parent == null) {
            assets.remove(asset.getId());
            throw new AssetException("Parent asset not found (id = %d).".formatted(parentId));
        }

        if (asset.getId() != Asset.INVALID_ID && parent != null) {
            if (!parent.isCompound()) {
                assets.remove(asset.getId());
                throw new AssetException(
                        "Parent is not a compound asset (id = %d).".formatted(parent.getId()));
            }
            CompoundAsset<?> compoundParent = (CompoundAsset<?>) parent;
            compoundParent.addChild(asset);
        }
    }

    /**
     * Atomically replaces the asset with the given id.
     * <p>
     * Detaches the old asset from its parent, attaches the new one to its
     * parent, and updates the flat index in a single logical operation.
     * Thread-safe via {@link ConcurrentHashMap#compute}.
     *
     * @param id       the asset id to replace
     * @param newAsset the replacement asset (must have the same id)
     * @throws AssetException if the id does not exist or the new asset is invalid
     */
    public void replaceAsset(int id, Asset<?> newAsset) {
        if (newAsset == null) {
            throw new AssetException("New asset must not be null.");
        }
        assets.compute(id, (key, oldAsset) -> {
            if (oldAsset == null) {
                throw new AssetException("Asset not found (id = %d); cannot replace.".formatted(id));
            }
            // Detach old from parent
            CompoundAsset<?> oldParent = (oldAsset.getParent() instanceof CompoundAsset<?> cp) ? cp : null;
            if (oldParent != null) {
                oldParent.removeChild(oldAsset.getName());
            }
            // Attach new to parent
            newAsset.setContext(this);
            int parentId = newAsset.getParentId();
            Asset<?> parent = (parentId == Asset.INVALID_ID) ? root : assets.get(parentId);
            if (parent == null) {
                throw new AssetException("Parent asset not found (id = %d).".formatted(parentId));
            }
            if (parent != root && !parent.isCompound()) {
                throw new AssetException(
                        "Parent is not a compound asset (id = %d).".formatted(parent.getId()));
            }
            if (parent instanceof CompoundAsset<?> compoundParent) {
                compoundParent.addChild(newAsset);
            }
            return newAsset;
        });
    }

    /**
     * Removes an asset by id.
     * Also detaches it from its parent if applicable.
     *
     * @param id the asset id
     * @return the removed asset, or {@code null} if not found
     */
    public Asset<?> removeAsset(int id) {
        Asset<?> asset = assets.remove(id);
        if (asset == null) {
            return null;
        }
        CompoundAsset<?> parent = (asset.getParent() instanceof CompoundAsset<?> cp) ? cp : null;
        if (parent != null) {
            parent.removeChild(asset.getName());
        }
        return asset;
    }

    // ======================== queries ========================

    /**
     * Finds an asset by id.
     *
     * @param id the asset id
     * @return the asset, or {@code null} if not found
     */
    public Asset<?> findAsset(int id) {
        return assets.get(id);
    }

    /**
     * Returns all assets in the store.
     *
     * @return unmodifiable collection of all assets
     */
    public Collection<Asset<?>> getAssets() {
        return Collections.unmodifiableCollection(assets.values());
    }

    /**
     * Returns all assets matching the given kind.
     *
     * @param kind the asset kind to filter by
     * @return list of matching assets
     */
    public List<Asset<?>> getAssetsByKind(AssetKind kind) {
        List<Asset<?>> result = new ArrayList<>();
        for (Asset<?> asset : assets.values()) {
            if (asset.getKind() == kind) {
                result.add(asset);
            }
        }
        return result;
    }

    /**
     * Returns the neutral tree anchor.
     *
     * @return the anchor top-level assets attach to (never {@code null})
     */
    public CompoundAsset<AssetType> getRoot() {
        return root;
    }

    /**
     * Builds the full path from the root down to the given asset,
     * using {@link #PATH_SEPARATOR} between names.
     *
     * @param asset the target asset
     * @return the full path string, e.g. "floor1->deviceA->probe1"
     */
    public String getFullPath(Asset<?> asset) {
        if (asset == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        Asset<?> current = asset;
        while (current != null && current != root) {
            names.add(current.getName());
            current = current.getParent();
        }
        Collections.reverse(names);
        return String.join(PATH_SEPARATOR, names);
    }

    /**
     * Removes all assets from the store and re-creates a fresh anchor.
     */
    public void clear() {
        assets.clear();
        root = newAnchor(this);
    }

    // ======================== type managers ========================

    public AssetTypeManager<DeviceType> getDeviceTypes() { return deviceTypes; }
    public AssetTypeManager<ServiceType> getServiceTypes() { return serviceTypes; }
    public AssetTypeManager<ProbeType> getProbeTypes() { return probeTypes; }
    public AssetTypeManager<ControlType> getControlTypes() { return controlTypes; }
}
