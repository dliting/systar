package com.systar.server.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.systar.monitor.asset.AssetKind;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip coverage for the asset-group tables ({@code t_group_tree},
 * {@code t_group}, {@code t_asset_group_rel}) against the dev H2 schema.
 * Rel asset ids reference {@code t_asset.id} rows from 01-init.sql.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("Group repository CRUD and rel membership")
class GroupRepositoryTest {

    /** Seed t_asset row ids from 01-init.sql. */
    private static final long SEED_ELEC_METER_ROW_ID     = 22L;
    private static final long SEED_UPS_ROW_ID            = 23L;
    private static final long SEED_TH_SENSOR_ROW_ID      = 20L;
    private static final long SEED_SERVICE_SVC_ROW_ID    = 10L;
    private static final long SEED_PROBE_TEMP_ROW_ID     = 30L;
    private static final long SEED_CONTROL_SWITCH_ROW_ID = 40L;
    /** No t_asset row carries this id. */
    private static final long MISSING_ASSET_ROW_ID       = 999_999_999L;
    /** Runtime device id outside 01-init, used to stage duplicate view rows. */
    private static final int  DUPLICATE_DEVICE_RUNTIME_ID = 770_001;

    @Autowired
    private GroupRepository groupRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("tree and group CRUD round-trip with rel replacement")
    void treeGroupRoundTrip() {
        groupRepo.insertTree("region", "按区域", 1);
        GroupRepository.GroupTreeRow tree = groupRepo.findTreeByName("region").orElseThrow();

        groupRepo.insertGroup(tree.id(), "machine_room", "机房",
                GroupRepository.TOP_LEVEL_PARENT, 1, 1);
        GroupRepository.GroupRow group = groupRepo.findGroupByName(tree.id(), "machine_room").orElseThrow();
        assertThat(group.level()).isEqualTo(1);

        groupRepo.insertRel(SEED_ELEC_METER_ROW_ID, group.id());
        groupRepo.insertRel(SEED_UPS_ROW_ID, group.id());
        assertThat(groupRepo.findMembersByTree(tree.id()))
                .containsEntry(group.id(), List.of(SEED_ELEC_METER_ROW_ID, SEED_UPS_ROW_ID));

        groupRepo.deleteRelsByGroup(group.id());
        assertThat(groupRepo.findMembersByTree(tree.id())).doesNotContainKey(group.id());

        groupRepo.deleteGroup(group.id());
        groupRepo.deleteTree(tree.id());
        assertThat(groupRepo.findTreeByName("region")).isEmpty();
    }

    @Test
    @DisplayName("countGroupsInTree and deleteRelsByAsset")
    void countsAndAssetCleanup() {
        groupRepo.insertTree("t2", "树2", 2);
        GroupRepository.GroupTreeRow tree = groupRepo.findTreeByName("t2").orElseThrow();
        groupRepo.insertGroup(tree.id(), "g1", "组1", GroupRepository.TOP_LEVEL_PARENT, 1, 1);
        groupRepo.insertGroup(tree.id(), "g2", "组2", GroupRepository.TOP_LEVEL_PARENT, 2, 2);
        assertThat(groupRepo.countGroupsInTree(tree.id())).isEqualTo(2);

        GroupRepository.GroupRow g1 = groupRepo.findGroupByName(tree.id(), "g1").orElseThrow();
        groupRepo.insertRel(SEED_TH_SENSOR_ROW_ID, g1.id());
        assertThat(groupRepo.findMembersByTree(tree.id())).containsKey(g1.id());
        groupRepo.deleteRelsByAsset(SEED_TH_SENSOR_ROW_ID);
        assertThat(groupRepo.findMembersByTree(tree.id())).doesNotContainKey(g1.id());
    }

    @Test
    @DisplayName("finders by id, updates and findAllTrees ordering")
    void findersUpdatesAndOrdering() {
        groupRepo.insertTree("late_tree", "晚建树", 9);
        groupRepo.insertTree("early_tree", "早建树", 1);
        GroupRepository.GroupTreeRow early = groupRepo.findTreeByName("early_tree").orElseThrow();
        GroupRepository.GroupTreeRow late  = groupRepo.findTreeByName("late_tree").orElseThrow();

        groupRepo.insertGroup(early.id(), "old_name", "旧组", GroupRepository.TOP_LEVEL_PARENT, 1, 2);
        groupRepo.insertGroup(early.id(), "kept",     "保持组", GroupRepository.TOP_LEVEL_PARENT, 1, 1);
        GroupRepository.GroupRow group = groupRepo.findGroupByName(early.id(), "old_name").orElseThrow();
        assertThat(groupRepo.findAllGroups(early.id()))
                .extracting(GroupRepository.GroupRow::name)
                .containsExactly("kept", "old_name");

        // findAllTrees orders by sequence (early_tree before late_tree despite insert order)
        assertThat(groupRepo.findAllTrees())
                .extracting(GroupRepository.GroupTreeRow::name)
                .containsExactly("early_tree", "late_tree");

        groupRepo.updateTree(early.id(), "renamed_tree", "改名树", 5);
        GroupRepository.GroupTreeRow updatedTree = groupRepo.findTreeById(early.id()).orElseThrow();
        assertThat(updatedTree.name()).isEqualTo("renamed_tree");
        assertThat(updatedTree.caption()).isEqualTo("改名树");
        assertThat(updatedTree.sequence()).isEqualTo(5);

        groupRepo.updateGroup(group.id(), "new_name", "新组", 3L, 2, 7);
        groupRepo.updateGroupLevel(group.id(), 9);
        GroupRepository.GroupRow updatedGroup = groupRepo.findGroupById(group.id()).orElseThrow();
        assertThat(updatedGroup.name()).isEqualTo("new_name");
        assertThat(updatedGroup.caption()).isEqualTo("新组");
        assertThat(updatedGroup.parent()).isEqualTo(3L);
        assertThat(updatedGroup.level()).isEqualTo(9);
        assertThat(updatedGroup.sequence()).isEqualTo(7);
    }

    @Test
    @DisplayName("findAssetRef bridges t_asset row ids to kind and runtime id")
    void findAssetRefReadsKindAndRuntimeId() {
        // 01-init: t_asset.id 22/10/30/40 carry device_id/service_id/probe_id/control_id 1003/100/2001/3001.
        assertThat(groupRepo.findAssetRef(SEED_ELEC_METER_ROW_ID))
                .contains(new GroupRepository.AssetRef(AssetKind.DEVICE, 1003));
        assertThat(groupRepo.findAssetRef(SEED_SERVICE_SVC_ROW_ID))
                .contains(new GroupRepository.AssetRef(AssetKind.SERVICE, 100));
        assertThat(groupRepo.findAssetRef(SEED_PROBE_TEMP_ROW_ID))
                .contains(new GroupRepository.AssetRef(AssetKind.PROBE, 2001));
        assertThat(groupRepo.findAssetRef(SEED_CONTROL_SWITCH_ROW_ID))
                .contains(new GroupRepository.AssetRef(AssetKind.CONTROL, 3001));
        assertThat(groupRepo.findAssetRef(MISSING_ASSET_ROW_ID)).isEmpty();
    }

    @Test
    @DisplayName("findRowIdsByRuntimeId reverse-maps every linked t_asset row; unknown kinds are skipped")
    void findRowIdsByRuntimeIdBridgesAllKinds() {
        // 01-init: runtime ids 1003/100/2001/3001 (device/service/probe/control) sit on rows 22/10/30/40.
        assertThat(groupRepo.findRowIdsByRuntimeId())
                .containsEntry(1003, SEED_ELEC_METER_ROW_ID)
                .containsEntry(100,  SEED_SERVICE_SVC_ROW_ID)
                .containsEntry(2001, SEED_PROBE_TEMP_ROW_ID)
                .containsEntry(3001, SEED_CONTROL_SWITCH_ROW_ID);

        // Defensive: a row with an unknown kind code never enters the reverse map.
        jdbc.update("INSERT INTO t_asset (name, caption, kind, parent_id, enabled) "
                + "VALUES ('ghost', 'ghost', 99, 0, 1)");
        Long ghostRowId = jdbc.queryForObject("SELECT id FROM t_asset WHERE kind=99", Long.class);
        assertThat(groupRepo.findRowIdsByRuntimeId()).doesNotContainValue(ghostRowId);
    }

    @Test
    @DisplayName("findRowIdsByRuntimeId resolves duplicate view rows to the newest row id")
    void findRowIdsByRuntimeIdPrefersNewestDuplicateRow() {
        // Polluted data: two t_asset rows bridged to the same runtime device —
        // the reverse map must deterministically keep the newest (largest) row
        // id, not whichever row the unordered SELECT happens to return last.
        jdbc.update("INSERT INTO t_asset (name, caption, kind, parent_id, enabled, device_id) "
                + "VALUES ('dup_a', 'dup_a', 1, 0, 1, ?)", DUPLICATE_DEVICE_RUNTIME_ID);
        jdbc.update("INSERT INTO t_asset (name, caption, kind, parent_id, enabled, device_id) "
                + "VALUES ('dup_b', 'dup_b', 1, 0, 1, ?)", DUPLICATE_DEVICE_RUNTIME_ID);
        Long newestRowId = jdbc.queryForObject(
                "SELECT MAX(id) FROM t_asset WHERE device_id=?", Long.class, DUPLICATE_DEVICE_RUNTIME_ID);

        assertThat(groupRepo.findRowIdsByRuntimeId())
                .containsEntry(DUPLICATE_DEVICE_RUNTIME_ID, newestRowId);
    }
}
