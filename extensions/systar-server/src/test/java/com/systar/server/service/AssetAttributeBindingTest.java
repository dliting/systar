package com.systar.server.service;

import com.systar.monitor.drivers.bacnet.BacnetService;
import com.systar.monitor.drivers.modbus.ModbusService;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.dto.AssetCreateRequest;
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
 * on the service path, symmetric with probe/control: {@code findServiceById}
 * used to skip loadAttributes + bindProperties, so UI-created services
 * entered the runtime store with Java-initial field values (only
 * ModbusService masked the gap via resolveConfig).
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
    private MonitorServer monitorServer;

    @Autowired
    private AssetRepository repository;

    /** Ids created this test — the DB rolls back, but the singleton store does not. */
    private final List<Integer> createdIds = new ArrayList<>();

    @AfterEach
    void removeFromStore() {
        for (int id : createdIds) {
            monitorServer.removeAsset(id);
        }
        createdIds.clear();
    }

    private int createService(String name, Map<String, String> attributes) {
        int id = orchestrator.createAsset(new AssetCreateRequest(
                "SERVICE", ROOT_PARENT_ID, name, name, "BACnetService",
                Map.of("mode", "ACTIVE"), attributes));
        createdIds.add(id);
        return id;
    }

    @Test
    @DisplayName("create without attributes falls back to type defaults (Remotehost=127.0.0.1)")
    void createServiceBindsTypeDefaults() {
        int id = createService("default_svc", Map.of());

        BacnetService runtime = (BacnetService) monitorServer.findAsset(id);
        assertThat(runtime.getRemoteHost()).isEqualTo("127.0.0.1");
        assertThat(runtime.getRemotePort()).isEqualTo(47808);
        assertThat(runtime.getDeviceId()).isZero();
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

}
