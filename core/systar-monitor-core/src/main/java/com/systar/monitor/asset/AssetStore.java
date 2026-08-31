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
 * hierarchy rooted at a single {@link Space} node.
 *
 * <h3>Root structure</h3>
 * The constructor creates a <em>virtual root</em> Space (id=-1, empty name)
 * that serves as a transparent tree anchor. Real assets from the database
 * carry {@code parent_id = 0} ({@link Asset#INVALID_ID}), which causes
 * {@link #addAsset(Asset)} to attach them under this virtual root.
 * <p>
 * This two-level design (virtual root → real root space) keeps the in-memory
 * model clean while matching the database convention where {@code parent_id = 0}
 * means "top-level". API consumers such as {@code AssetController.getAssetTree()}
 * skip the virtual root when real children exist, so end users never see it.
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

    /** Root id constant for the virtual root space. */
    private static final int ROOT_ID = -1;

    /** Flat index: asset id -> asset. */
    private final ConcurrentHashMap<Integer, Asset<?>> assets = new ConcurrentHashMap<>();

    /** The single root space node. */
    private volatile Space root;

    /** Type registries, populated by AssetTypeLoaders during startup. */
    private final AssetTypeManager<SpaceType> spaceTypes = new AssetTypeManager<>();
    private final AssetTypeManager<DeviceType> deviceTypes = new AssetTypeManager<>();
    private final AssetTypeManager<ServiceType> serviceTypes = new AssetTypeManager<>();
    private final AssetTypeManager<ProbeType> probeTypes = new AssetTypeManager<>();
    private final AssetTypeManager<ControlType> controlTypes = new AssetTypeManager<>();

    // ======================== lifecycle ========================

    public AssetStore() {
        // Self-initialize a virtual root as tree anchor.
        // Real root spaces (parent_id=0 in DB) attach under this node.
        // The virtual root is transparent — API consumers should skip it
        // when there are real children.
        Space space = new Space();
        space.setId(ROOT_ID);
        space.setName("");
        space.setCaption("");
        space.setContext(this);
        root = space;
        assets.put(space.getId(), space);
    }

    /** The id assigned to the auto-created virtual root anchor. */
    public static final int VIRTUAL_ROOT_ID = ROOT_ID;

    /**
     * Creates and registers the root space node.
     * Must be called before any other asset is added.
     *
     * @param rootType the space type to assign to the root node
     * @param rootName the name of the root node
     */
    public void createRoot(SpaceType rootType, String rootName) {
        Space space = new Space();
        space.setId(ROOT_ID);
        space.setName(rootName);
        space.setCaption(rootName);
        space.setType(rootType);
        space.setContext(this);
        root = space;
        assets.put(space.getId(), space);
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
     * Returns the root space node.
     *
     * @return the root, or {@code null} if {@link #createRoot} has not been called
     */
    public Space getRoot() {
        return root;
    }

    /**
     * Builds the full path from the root down to the given asset,
     * using {@link #PATH_SEPARATOR} between names.
     *
     * @param asset the target asset
     * @return the full path string, e.g. "root->floor1->deviceA"
     */
    public String getFullPath(Asset<?> asset) {
        if (asset == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        Asset<?> current = asset;
        while (current != null) {
            names.add(current.getName());
            current = current.getParent();
        }
        Collections.reverse(names);
        return String.join(PATH_SEPARATOR, names);
    }

    /**
     * Removes all assets from the store and clears the root reference.
     */
    public void clear() {
        assets.clear();
        root = null;
    }

    // ======================== type managers ========================

    public AssetTypeManager<SpaceType> getSpaceTypes() { return spaceTypes; }
    public AssetTypeManager<DeviceType> getDeviceTypes() { return deviceTypes; }
    public AssetTypeManager<ServiceType> getServiceTypes() { return serviceTypes; }
    public AssetTypeManager<ProbeType> getProbeTypes() { return probeTypes; }
    public AssetTypeManager<ControlType> getControlTypes() { return controlTypes; }
}
