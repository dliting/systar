package com.systar.server.loader;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.server.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the asset tree from database tables into an {@link AssetStore}.
 * <p>
 * Loading order follows the parent-before-child constraint:
 * Space → Device → Service → Probe → Control.
 * <p>
 * After all assets are loaded, instance-level KV attributes from
 * {@code t_asset_attribute} are applied to each asset via
 * {@link AssetRepository#loadAllAttributes}.
 */
@Component
public class DatabaseAssetLoader implements AssetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAssetLoader.class);

    private final AssetRepository       repo;
    private final VirtualProbeEngine    virtualProbeEngine;

    public DatabaseAssetLoader(AssetRepository repo, VirtualProbeEngine virtualProbeEngine) {
        this.repo              = repo;
        this.virtualProbeEngine = virtualProbeEngine;
    }

    @Override
    public void load(AssetStore store) {
        Map<Integer, MonitorService> serviceIndex = new HashMap<>();

        for (Space space : repo.findAllSpaces()) store.addAsset(space);
        for (Device device : repo.findAllDevices()) store.addAsset(device);
        for (MonitorService svc : repo.findAllServices(serviceIndex)) store.addAsset(svc);
        for (Probe probe : repo.findAllProbes(serviceIndex)) store.addAsset(probe);
        for (Control ctrl : repo.findAllControls(serviceIndex)) store.addAsset(ctrl);

        repo.loadAllAttributes(store.getAssets());

        for (Asset<?> asset : store.getAssets()) {
            asset.bindProperties();
        }

        for (Asset<?> asset : store.getAssets()) {
            if (asset instanceof VirtualProbe vp) {
                virtualProbeEngine.register(vp);
            }
        }

        log.info("Asset tree loaded: spaces/devices/services loaded from database.");
    }
}
