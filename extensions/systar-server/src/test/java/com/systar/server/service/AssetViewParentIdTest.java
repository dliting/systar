package com.systar.server.service;

import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.dto.AssetCreateRequest;
import com.systar.server.repository.GroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code t_asset.parent_id} must store the parent's t_asset ROW id (seed
 * convention, docs/design/ops-statistics-design.md §3) — not the parent's
 * per-kind runtime id that the create API receives from the asset tree.
 * Regression guard: UI-created assets silently dropped out of statistics
 * when parent_id held the runtime id.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("Asset view parent_id id domain")
class AssetViewParentIdTest {

    /** Seed temperature/humidity sensor from 01-init.sql (t_asset row 20). */
    private static final int SEED_SENSOR_DEVICE_ID = 1001;
    private static final int ROOT_PARENT_ID        = 0;

    @Autowired
    private AssetOrchestrator orchestrator;

    @Autowired
    private MonitorServer monitorServer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupRepository groupRepo;

    /** Ids created this test — the DB rolls back, but the singleton store does not. */
    private final List<Integer> createdIds = new ArrayList<>();

    @AfterEach
    void removeFromStore() {
        for (int id : createdIds) {
            monitorServer.removeAsset(id);
        }
        createdIds.clear();
    }

    private int create(String kind, int parentId, String name, Map<String, Object> properties) {
        int id = orchestrator.createAsset(new AssetCreateRequest(
                kind, parentId, name, name, null, properties, Map.of())).runtimeId();
        createdIds.add(id);
        return id;
    }

    @Test
    @DisplayName("root asset keeps parent_id=0")
    void rootAsset() {
        int deviceId = create("DEVICE", ROOT_PARENT_ID, "view_dev_root", Map.of());

        assertThat(viewParentId("kind=1 AND device_id=?", deviceId))
                .isEqualTo((long) Asset.INVALID_ID);
    }

    @Test
    @DisplayName("create response's assetRowId bridges back to the runtime asset in the store")
    void createResponseRowIdBridgesToRuntime() {
        var result = orchestrator.createAsset(new AssetCreateRequest(
                "DEVICE", ROOT_PARENT_ID, "bridge_dev", "bridge_dev", null, Map.of(), Map.of()));
        createdIds.add(result.runtimeId());

        assertThat(monitorServer.findAsset(result.runtimeId())).isNotNull();
        assertThat(groupRepo.findAssetRefs(List.of(result.assetRowId())))
                .containsEntry(result.assetRowId(),
                        new GroupRepository.AssetRef(AssetKind.DEVICE, result.runtimeId()));
    }

    @Test
    @DisplayName("probe view row hangs under an API-created device's asset row")
    void probeUnderCreatedDevice() {
        int deviceId = create("DEVICE", ROOT_PARENT_ID, "view_dev", Map.of());
        Long deviceRowId = assetViewRowId("kind=1 AND device_id=?", deviceId);

        int probeId = create("PROBE", deviceId, "view_probe", Map.of("unit", "V"));

        assertThat(viewParentId("kind=3 AND probe_id=?", probeId)).isEqualTo(deviceRowId);
    }

    @Test
    @DisplayName("probe view row hangs under the parent device's asset row")
    void probeUnderDevice() {
        Long sensorRowId = assetViewRowId("kind=1 AND device_id=?", SEED_SENSOR_DEVICE_ID);

        int probeId = create("PROBE", SEED_SENSOR_DEVICE_ID, "view_probe", Map.of("unit", "V"));

        assertThat(viewParentId("kind=3 AND probe_id=?", probeId)).isEqualTo(sensorRowId);
    }

    @Test
    @DisplayName("probe falls back to parent_id=0 when the parent has no view row")
    void probeUnderDeviceWithoutViewRow() {
        int deviceId = create("DEVICE", ROOT_PARENT_ID, "orphan_dev", Map.of());
        jdbcTemplate.update("DELETE FROM t_asset WHERE kind=1 AND device_id=?", deviceId);

        int probeId = create("PROBE", deviceId, "orphan_probe", Map.of("unit", "V"));

        assertThat(viewParentId("kind=3 AND probe_id=?", probeId))
                .isEqualTo((long) Asset.INVALID_ID);
    }

    @Test
    @DisplayName("deleting an asset removes its group memberships")
    void deleteAssetRemovesGroupRels() {
        groupRepo.insertTree("rel_cascade_test", "级联清理", 1);
        GroupRepository.GroupTreeRow tree = groupRepo.findTreeByName("rel_cascade_test").orElseThrow();
        groupRepo.insertGroup(tree.id(), "g", "组", GroupRepository.TOP_LEVEL_PARENT, 1, 1);
        GroupRepository.GroupRow group = groupRepo.findGroupByName(tree.id(), "g").orElseThrow();

        int deviceId    = create("DEVICE", ROOT_PARENT_ID, "rel_dev", Map.of());
        long assetRowId = assetViewRowId("kind=1 AND device_id=?", deviceId);
        groupRepo.insertRel(assetRowId, group.id());
        assertThat(countGroupRels(assetRowId)).isEqualTo(1L);

        orchestrator.deleteAsset(deviceId, AssetKind.DEVICE);

        assertThat(countGroupRels(assetRowId)).isZero();
    }

    private long countGroupRels(long assetRowId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_asset_group_rel WHERE asset_id=?", Long.class, assetRowId);
        return count != null ? count : 0L;
    }

    private Long assetViewRowId(String condition, int perKindId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_asset WHERE " + condition, Long.class, perKindId);
    }

    private Long viewParentId(String condition, int perKindId) {
        return jdbcTemplate.queryForObject(
                "SELECT parent_id FROM t_asset WHERE " + condition, Long.class, perKindId);
    }
}
