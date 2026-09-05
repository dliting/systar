package com.systar.server.service;

import com.systar.monitor.asset.Asset;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.dto.AssetCreateRequest;
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
                kind, parentId, name, name, null, properties, Map.of()));
        createdIds.add(id);
        return id;
    }

    @Test
    @DisplayName("root asset keeps parent_id=0")
    void rootAsset() {
        int spaceId = create("SPACE", ROOT_PARENT_ID, "view_space", Map.of());

        assertThat(viewParentId("kind=0 AND space_id=?", spaceId))
                .isEqualTo((long) Asset.INVALID_ID);
    }

    @Test
    @DisplayName("device view row hangs under the parent space's asset row")
    void deviceUnderSpace() {
        int spaceId  = create("SPACE", ROOT_PARENT_ID, "view_space", Map.of());
        Long spaceRowId = assetViewRowId("kind=0 AND space_id=?", spaceId);

        int deviceId = create("DEVICE", spaceId, "view_dev", Map.of());

        assertThat(viewParentId("kind=1 AND device_id=?", deviceId)).isEqualTo(spaceRowId);
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

    private Long assetViewRowId(String condition, int perKindId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_asset WHERE " + condition, Long.class, perKindId);
    }

    private Long viewParentId(String condition, int perKindId) {
        return jdbcTemplate.queryForObject(
                "SELECT parent_id FROM t_asset WHERE " + condition, Long.class, perKindId);
    }
}
