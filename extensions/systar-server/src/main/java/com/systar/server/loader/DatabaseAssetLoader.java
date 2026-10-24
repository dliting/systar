package com.systar.server.loader;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.server.config.DatabaseInitializer;
import com.systar.server.plugin.PluginSeedExecutor;
import com.systar.server.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the asset tree from database tables into an {@link AssetStore}.
 * <p>
 * Loading order follows the parent-before-child constraint:
 * Device → Service → Probe → Control.
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
    private final PluginSeedExecutor    seedExecutor;
    private final ObjectProvider<DatabaseInitializer> databaseInitializer;

    public DatabaseAssetLoader(AssetRepository repo,
                               VirtualProbeEngine virtualProbeEngine,
                               PluginSeedExecutor seedExecutor,
                               ObjectProvider<DatabaseInitializer> databaseInitializer) {
        this.repo              = repo;
        this.virtualProbeEngine = virtualProbeEngine;
        this.seedExecutor      = seedExecutor;
        this.databaseInitializer = databaseInitializer;
    }

    @Override
    public void load(AssetStore store) {
        databaseInitializer.getIfAvailable();  // prod form: force the main-seed bean before plugin seeds
        seedExecutor.executeAll();

        Map<Integer, MonitorService> serviceIndex = new HashMap<>();
        Map<Integer, String> skippedServices = new HashMap<>();

        for (AssetRepository.DeviceRow row : repo.findAllDeviceRows()) {
            try {
                store.addAsset(repo.assembleDevice(row));
            } catch (AssetException e) {
                log.warn("Skipped device row id={} '{}': {}", row.id(), row.name(), e.getMessage());
            }
        }
        for (AssetRepository.ServiceRow row : repo.findAllServiceRows()) {
            try {
                store.addAsset(repo.assembleService(row, serviceIndex));
            } catch (AssetException e) {
                skippedServices.put(row.id(), e.getMessage());
                log.warn("Skipped service row id={} '{}': {}", row.id(), row.name(), e.getMessage());
            }
        }
        for (AssetRepository.ProbeRow row : repo.findAllProbeRows()) {
            try {
                store.addAsset(repo.assembleProbe(row, serviceIndex));
            } catch (AssetException e) {
                log.warn("Skipped probe row id={} '{}': {}{}",
                        row.id(), row.name(), e.getMessage(), cascadeNote(row.serviceId(), skippedServices));
            }
        }
        for (AssetRepository.ControlRow row : repo.findAllControlRows()) {
            try {
                store.addAsset(repo.assembleControl(row, serviceIndex));
            } catch (AssetException e) {
                log.warn("Skipped control row id={} '{}': {}{}",
                        row.id(), row.name(), e.getMessage(), cascadeNote(row.serviceId(), skippedServices));
            }
        }

        repo.loadAllAttributes(store.getAssets());

        for (Asset<?> asset : store.getAssets()) {
            asset.bindProperties();
        }

        for (Asset<?> asset : store.getAssets()) {
            if (asset instanceof VirtualProbe vp) {
                virtualProbeEngine.register(vp);
            }
        }

        log.info("Asset tree loaded from database (rows skipped at startup are WARNed above).");
    }

    /** Names the source row's own skip reason when a monitor cascades onto it. */
    private static String cascadeNote(Integer serviceId, Map<Integer, String> skippedServices) {
        if (serviceId == null) return "";
        String reason = skippedServices.get(serviceId);
        return reason == null ? ""
                : " (source service id=" + serviceId + " was skipped: " + reason + ")";
    }
}
