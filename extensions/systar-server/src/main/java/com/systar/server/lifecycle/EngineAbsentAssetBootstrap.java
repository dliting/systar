package com.systar.server.lifecycle;

import com.systar.monitor.asset.AssetLoader;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.type.AssetTypeLoader;
import com.systar.monitor.server.MonitorEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Engine-absent counterpart of the engine's own bootstrap. When no
 * {@link MonitorEngine} bean is present (the exported open-source form),
 * the server populates the {@link AssetStore} itself — type registries
 * first, then asset instances — so asset management stays fully usable
 * without the engine. With the engine present its bootstrap runs this
 * same sequence and this component does nothing.
 *
 * <p>Implements {@link SmartInitializingSingleton} like the engine's
 * bootstrap, so platform config/dict loading (InitializingBean) is
 * guaranteed to have completed before assets load.
 */
@Component
public class EngineAbsentAssetBootstrap implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(EngineAbsentAssetBootstrap.class);

    private final ObjectProvider<MonitorEngine> engine;
    private final List<AssetTypeLoader> typeLoaders;
    private final AssetLoader assetLoader;
    private final AssetStore assetStore;

    public EngineAbsentAssetBootstrap(ObjectProvider<MonitorEngine> engine,
                                      List<AssetTypeLoader> typeLoaders,
                                      AssetLoader assetLoader,
                                      AssetStore assetStore) {
        this.engine      = engine;
        this.typeLoaders = typeLoaders;
        this.assetLoader = assetLoader;
        this.assetStore  = assetStore;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (engine.getIfAvailable() != null) {
            return;  // the engine's bootstrap owns the loading sequence
        }
        log.info("Monitor engine not present; server-side asset loading active.");
        for (AssetTypeLoader typeLoader : typeLoaders) {
            typeLoader.load(assetStore);
        }
        assetLoader.load(assetStore);
        log.info("Asset store populated without engine. Total count: {}", assetStore.getAssets().size());
    }
}
