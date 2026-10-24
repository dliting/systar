package com.systar.server.service;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.drivers.modbus.ModbusService;
import com.systar.server.dto.AssetCreateRequest;
import com.systar.server.dto.AssetUpdateRequest;
import com.systar.server.repository.AssetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extension attributes ({@code t_asset_attribute}) must reach driver setters
 * on every service path, symmetric with probe/control:
 * <ul>
 *   <li>{@code findServiceById} used to skip loadAttributes + bindProperties,
 *       so UI-created services entered the runtime store with Java-initial
 *       field values (only ModbusService masked the gap via resolveConfig);</li>
 *   <li>{@code updateAsset} used to persist attributes AFTER the per-kind
 *       update had already re-read the asset, so UPDATED events carried
 *       stale attribute values for all kinds until restart.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("Asset extension-attribute binding through create/update paths")
class AssetAttributeBindingTest {

    private static final int ROOT_PARENT_ID = 0;

    @Autowired
    private AssetOrchestrator orchestrator;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private AssetRepository repository;

    /** Ids created this test — the DB rolls back, but the singleton store does not. */
    private final List<Integer> createdIds = new ArrayList<>();

    @AfterEach
    void removeFromStore() {
        for (int id : createdIds) {
            assetStore.removeAsset(id);
        }
        createdIds.clear();
    }

    private int createService(String name, Map<String, String> attributes) {
        int id = orchestrator.createAsset(new AssetCreateRequest(
                "SERVICE", ROOT_PARENT_ID, name, name, "ModbusTcpMaster",
                Map.of("mode", "ACTIVE"), attributes)).runtimeId();
        createdIds.add(id);
        return id;
    }

    @Test
    @DisplayName("create binds extension attributes to driver setters (runtime store and repository)")
    void createServiceBindsExtensionAttributes() {
        int id = createService("bind_svc", Map.of(
                "Host", "192.168.1.50",
                "Port", "55503",
                "UnitId", "123"));

        ModbusService runtime = (ModbusService) assetStore.findAsset(id);
        assertThat(runtime.getHost()).isEqualTo("192.168.1.50");
        assertThat(runtime.getPort()).isEqualTo(55503);
        assertThat(runtime.getUnitId()).isEqualTo(123);

        ModbusService fromRepo = (ModbusService) repository.findServiceById(id);
        assertThat(fromRepo.getHost()).isEqualTo("192.168.1.50");
        assertThat(fromRepo.getPort()).isEqualTo(55503);
        assertThat(fromRepo.getUnitId()).isEqualTo(123);
    }

    @Test
    @DisplayName("create without attributes falls back to type defaults (Host=127.0.0.1)")
    void createServiceBindsTypeDefaults() {
        int id = createService("default_svc", Map.of());

        ModbusService runtime = (ModbusService) assetStore.findAsset(id);
        assertThat(runtime.getHost()).isEqualTo("127.0.0.1");
        assertThat(runtime.getPort()).isEqualTo(502);
        assertThat(runtime.getUnitId()).isEqualTo(1);
    }

    @Test
    @DisplayName("update re-binds changed service attributes into the runtime store")
    void updateServiceRebindsAttributes() {
        int id = createService("update_svc", Map.of(
                "Host", "192.168.1.50",
                "Port", "55503",
                "UnitId", "123"));

        orchestrator.updateAsset(id, AssetKind.SERVICE, new AssetUpdateRequest(
                null, null, null, Map.of(),
                Map.of("Host", "10.0.0.9", "Port", "502", "UnitId", "77")));

        ModbusService runtime = (ModbusService) assetStore.findAsset(id);
        assertThat(runtime.getHost()).isEqualTo("10.0.0.9");
        assertThat(runtime.getPort()).isEqualTo(502);
        assertThat(runtime.getUnitId()).isEqualTo(77);
    }

    @Test
    @DisplayName("seed Modbus service binds Host/Port/UnitId and column max_connections beats XML default")
    void seedModbusServiceBindsAttributesAndColumn() {
        ModbusService svc = (ModbusService) repository.findServiceById(110);

        assertThat(svc.getHost()).isEqualTo("localhost");
        assertThat(svc.getPort()).isEqualTo(55502);
        assertThat(svc.getUnitId()).isEqualTo(1);
        // t_service.max_connections column (5) outranks the type default (10)
        assertThat(svc.getMaxConnections()).isEqualTo(5);
    }

    @Test
    @DisplayName("create binds per-instance Timeout override; no attribute falls back to type default")
    void createServiceBindsTimeoutOverride() {
        int id = orchestrator.createAsset(new AssetCreateRequest(
                "SERVICE", ROOT_PARENT_ID, "timeout_svc", "timeout_svc", "ModbusTcpMaster",
                Map.of("mode", "ACTIVE"), Map.of("Timeout", "8000"))).runtimeId();
        createdIds.add(id);

        ModbusService overridden = (ModbusService) assetStore.findAsset(id);
        assertThat(overridden.getTimeout()).isEqualTo(8000);

        int plainId = orchestrator.createAsset(new AssetCreateRequest(
                "SERVICE", ROOT_PARENT_ID, "timeout_default_svc", "timeout_default_svc",
                "ModbusTcpMaster", Map.of("mode", "ACTIVE"), Map.of())).runtimeId();
        createdIds.add(plainId);

        ModbusService withDefault = (ModbusService) assetStore.findAsset(plainId);
        assertThat(withDefault.getTimeout()).isEqualTo(5000);
    }

    @Test
    @DisplayName("update re-binds changed probe attributes (RegisterAddr) into the runtime store")
    void updateProbeRebindsAttributes() {
        int id = orchestrator.createAsset(new AssetCreateRequest(
                "PROBE", ROOT_PARENT_ID, "update_probe", "update_probe", "ModbusFloatFC3",
                Map.of("unit", "V"), Map.of("RegisterAddr", "100"))).runtimeId();
        createdIds.add(id);

        orchestrator.updateAsset(id, AssetKind.PROBE, new AssetUpdateRequest(
                null, null, null, Map.of(), Map.of("RegisterAddr", "200")));

        // ModbusProbe reads RegisterAddr from metadata at detect time — the
        // runtime instance must reflect the updated value without a restart.
        Object registerAddr = assetStore.findAsset(id).getMetadata("RegisterAddr");
        assertThat(registerAddr).isEqualTo("200");
    }
}
