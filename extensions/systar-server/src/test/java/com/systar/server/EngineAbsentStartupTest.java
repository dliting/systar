package com.systar.server;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.server.MonitorEngine;
import com.systar.server.dto.AssetCreateRequest;
import com.systar.server.service.AssetOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the engine-optional contract: with the engine auto-configuration
 * excluded (the exported form has no engine on the classpath at all), the
 * server context still starts, the server populates the AssetStore itself
 * (type registries + seeded assets), and asset CRUD stays fully usable —
 * runtime monitoring operations are the only engine-only concern.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=com.systar.engine.EngineAutoConfiguration")
@ActiveProfiles("dev")
class EngineAbsentStartupTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private AssetStore store;

    @Autowired
    private AssetOrchestrator orchestrator;

    @Test
    @DisplayName("Server context starts with no engine bean; MonitorEngine port empty")
    void startsWithoutEngine() {
        assertThat(ctx.getBeanNamesForType(MonitorEngine.class)).isEmpty();
    }

    @Test
    @DisplayName("Asset queries served from AssetStore with engine absent")
    void assetStoreQueriesWork() {
        assertThat(ctx.getBean(AssetStore.class)).isNotNull();
    }

    @Test
    @DisplayName("Type registries and seeded assets loaded without engine")
    void storePopulatedAtStartup() {
        assertThat(store.getServiceTypes().size()).isGreaterThan(0);
        assertThat(store.findAsset(110)).isNotNull();   // simulator Modbus service from dev seed
    }

    @Test
    @DisplayName("Asset create/delete round-trip works without engine")
    void createAndDeleteSyncStore() {
        var created = orchestrator.createAsset(new AssetCreateRequest(
                "DEVICE", 0, "engine_absent_dev", "Engine-absent test device",
                "GenericDevice", Map.of(), Map.of()));
        assertThat(store.findAsset(created.runtimeId())).isNotNull();

        orchestrator.deleteAsset(created.runtimeId(), AssetKind.DEVICE);
        assertThat(store.findAsset(created.runtimeId())).isNull();
    }
}
