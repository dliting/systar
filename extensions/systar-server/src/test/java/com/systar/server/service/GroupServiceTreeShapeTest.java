package com.systar.server.service;

import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.DeviceType;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.server.controller.vo.TreeNodeVO;
import com.systar.server.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class GroupServiceTreeShapeTest {

    private GroupRepository repo;
    private AssetStore      store;
    private GroupService    service;

    @BeforeEach
    void setUp() {
        repo    = mock(GroupRepository.class);
        store   = new AssetStore();
        service = new GroupService(store, repo);
    }

    private Device addDevice(int id, String name) {
        Device dev = new Device();
        dev.init(new DeviceType("dt" + id), id, name);
        store.addAsset(dev);
        return dev;
    }

    private void addProbe(int id, String name, int parentId) {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt" + id), id, name);
        probe.setParentId(parentId);
        store.addAsset(probe);
    }

    @Test
    @DisplayName("kind tree: synthetic roots, devices carry probe children, no anchor leak")
    void kindTreeShape() {
        // Reverse map: runtime id -> t_asset row id (deliberately misaligned, as in 01-init).
        when(repo.findRowIdsByRuntimeId()).thenReturn(Map.of(10, 7L, 30, 27L));
        addDevice(10, "elec_meter_001");
        addProbe(30, "voltage", 10);

        List<TreeNodeVO> forest = service.buildAssetTree("kind");

        assertThat(forest).hasSize(2);
        TreeNodeVO serviceRoot = forest.get(0);
        assertThat(serviceRoot.key()).isEqualTo("KIND:SERVICE");
        assertThat(serviceRoot.nodeKind()).isEqualTo("ASSET");
        assertThat(serviceRoot.assetKind()).isEqualTo("SERVICE");
        assertThat(serviceRoot.id()).isNull();
        assertThat(serviceRoot.assetRowId()).isNull();

        TreeNodeVO deviceRoot = forest.get(1);
        assertThat(deviceRoot.key()).isEqualTo("KIND:DEVICE");
        assertThat(deviceRoot.assetRowId()).isNull();
        assertThat(deviceRoot.children()).hasSize(1);
        assertThat(deviceRoot.children().get(0).key()).isEqualTo("ASSET:10");
        assertThat(deviceRoot.children().get(0).assetRowId()).isEqualTo(7L);
        assertThat(deviceRoot.children().get(0).children().get(0).key()).isEqualTo("ASSET:30");
        assertThat(deviceRoot.children().get(0).children().get(0).assetRowId()).isEqualTo(27L);
        // anchor never appears
        assertThat(forest.stream().flatMap(r -> r.children().stream())
                .noneMatch(n -> n.id() != null && n.id() < 0)).isTrue();
    }

    @Test
    @DisplayName("kind tree: orphan probes (parent = neutral anchor) get their own KIND:PROBE root")
    void orphanProbeGetsOwnRoot() {
        addProbe(31, "loose_probe", Asset.INVALID_ID);

        List<TreeNodeVO> forest = service.buildAssetTree("kind");

        assertThat(forest).extracting(TreeNodeVO::key)
                .containsExactly("KIND:SERVICE", "KIND:DEVICE", "KIND:PROBE");
        TreeNodeVO orphanRoot = forest.get(2);
        assertThat(orphanRoot.caption()).isEqualTo("孤儿监测器");
        assertThat(orphanRoot.children()).extracting(TreeNodeVO::key)
                .containsExactly("ASSET:31");
    }

    @Test
    @DisplayName("group tree: rel row ids bridge to runtime ids; members, subtrees and ungrouped")
    void groupTreeShape() {
        when(repo.findTreeById(1L)).thenReturn(Optional.of(
                new GroupRepository.GroupTreeRow(1L, "region", "按区域", 1)));
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "machine_room", "机房", 0L, 1, 1),
                new GroupRepository.GroupRow(2L, 1L, "public", "公共", 0L, 1, 2)));
        when(repo.findMembersByTree(1L)).thenReturn(Map.of(
                1L, List.of(22L, 23L),
                2L, List.of(21L)));
        // t_asset row ids (22/23/21) differ from runtime ids (1003/1004/1002), as in 01-init.
        when(repo.findAssetRef(22L))
                .thenReturn(Optional.of(new GroupRepository.AssetRef(AssetKind.DEVICE, 1003)));
        when(repo.findAssetRef(23L))
                .thenReturn(Optional.of(new GroupRepository.AssetRef(AssetKind.DEVICE, 1004)));
        when(repo.findAssetRef(21L))
                .thenReturn(Optional.of(new GroupRepository.AssetRef(AssetKind.DEVICE, 1002)));
        // Reverse map: runtime id -> t_asset row id; runtime 1010 has no view row (defensive null).
        when(repo.findRowIdsByRuntimeId())
                .thenReturn(Map.of(1002, 21L, 1003, 22L, 1004, 23L, 2008, 26L));

        addDevice(1010, "device_1010");    // ungrouped
        addDevice(1002, "th_sensor_001");
        addDevice(1003, "ups_001");
        addDevice(1004, "elec_meter_001");
        addProbe(2008, "battery", 1003);

        List<TreeNodeVO> forest = service.buildAssetTree("1");

        assertThat(forest).extracting(TreeNodeVO::key)
                .containsExactly("GROUP:1", "GROUP:2", "UNGROUPED");
        TreeNodeVO machineRoom = forest.get(0);
        assertThat(machineRoom.nodeKind()).isEqualTo("GROUP");
        assertThat(machineRoom.caption()).isEqualTo("机房");
        assertThat(machineRoom.assetRowId()).isNull();
        assertThat(machineRoom.children()).extracting(TreeNodeVO::key)
                .containsExactly("ASSET:1003", "ASSET:1004");
        assertThat(machineRoom.children()).extracting(TreeNodeVO::assetRowId)
                .containsExactly(22L, 23L);
        assertThat(machineRoom.children().get(0).children().get(0).key()).isEqualTo("ASSET:2008");
        assertThat(machineRoom.children().get(0).children().get(0).assetRowId()).isEqualTo(26L);

        TreeNodeVO publicGroup = forest.get(1);
        assertThat(publicGroup.children()).extracting(TreeNodeVO::key)
                .containsExactly("ASSET:1002");
        assertThat(publicGroup.children()).extracting(TreeNodeVO::assetRowId)
                .containsExactly(21L);

        TreeNodeVO ungrouped = forest.get(2);
        assertThat(ungrouped.assetRowId()).isNull();
        assertThat(ungrouped.children()).extracting(TreeNodeVO::key)
                .containsExactly("ASSET:1010");
        assertThat(ungrouped.children()).extracting(TreeNodeVO::assetRowId)
                .containsExactly((Long) null);
    }

    @Test
    @DisplayName("group tree silently skips members missing from t_asset or the live store")
    void groupTreeSkipsMissingMembers() {
        when(repo.findTreeById(1L)).thenReturn(Optional.of(
                new GroupRepository.GroupTreeRow(1L, "region", "按区域", 1)));
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "machine_room", "机房", 0L, 1, 1)));
        // 999L is a stale rel row: no t_asset row carries that id (findAssetRef -> empty).
        // 888L bridges to a live-kind asset whose runtime counterpart is absent from
        // the store (findAssetRef -> Device 9999, never addDevice) — the store-miss skip.
        when(repo.findMembersByTree(1L)).thenReturn(Map.of(1L, List.of(999L, 888L, 22L)));
        when(repo.findAssetRef(22L))
                .thenReturn(Optional.of(new GroupRepository.AssetRef(AssetKind.DEVICE, 1003)));
        when(repo.findAssetRef(888L))
                .thenReturn(Optional.of(new GroupRepository.AssetRef(AssetKind.DEVICE, 9999)));

        addDevice(1003, "ups_001");

        List<TreeNodeVO> forest = service.buildAssetTree("1");

        List<String> keys = new ArrayList<>();
        collectKeys(forest, keys);
        assertThat(keys).contains("ASSET:1003").doesNotContain("ASSET:999", "ASSET:9999");
    }

    @Test
    @DisplayName("group tree skips monitor-kind members even when the asset is live")
    void groupTreeSkipsMonitorKindMembers() {
        when(repo.findTreeById(1L)).thenReturn(Optional.of(
                new GroupRepository.GroupTreeRow(1L, "region", "按区域", 1)));
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "machine_room", "机房", 0L, 1, 1)));
        // A stale PROBE rel row must not render, while its probe stays under its device.
        when(repo.findMembersByTree(1L)).thenReturn(Map.of(1L, List.of(30L)));
        when(repo.findAssetRef(30L))
                .thenReturn(Optional.of(new GroupRepository.AssetRef(AssetKind.PROBE, 2001)));

        addDevice(1001, "th_sensor_001");
        addProbe(2001, "temp_101", 1001);

        List<TreeNodeVO> forest = service.buildAssetTree("1");

        TreeNodeVO machineRoom = forest.get(0);
        assertThat(machineRoom.children()).isEmpty();
        List<String> keys = new ArrayList<>();
        collectKeys(forest, keys);
        assertThat(keys).contains("ASSET:2001");
    }

    private static void collectKeys(List<TreeNodeVO> nodes, List<String> keys) {
        for (TreeNodeVO node : nodes) {
            keys.add(node.key());
            collectKeys(node.children(), keys);
        }
    }

    @Test
    @DisplayName("unknown tree selector returns error")
    void unknownTree() {
        when(repo.findTreeById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buildAssetTree("99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree not found");
    }

    @Test
    @DisplayName("non-numeric tree selector returns error")
    void invalidTreeSelector() {
        assertThatThrownBy(() -> service.buildAssetTree("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tree selector");
    }
}
