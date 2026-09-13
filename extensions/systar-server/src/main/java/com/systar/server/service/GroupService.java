package com.systar.server.service;

import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.CompoundAsset;
import com.systar.server.controller.vo.GroupVO;
import com.systar.server.controller.vo.TreeNodeVO;
import com.systar.server.repository.GroupRepository;
import com.systar.server.repository.GroupRepository.AssetRef;
import com.systar.server.repository.GroupRepository.GroupRow;
import com.systar.server.repository.GroupRepository.GroupTreeRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Group-tree management: validation, level maintenance and delete constraints
 * for user-defined organizational trees. Grouping is a pure add-on in
 * systar-server — monitor-core is unaware of it.
 * <p>
 * Invariants enforced here (all fail-fast with clear messages):
 * <ul>
 *   <li>tree names are globally unique; group names unique within a tree</li>
 *   <li>a group's new parent must not be inside the moved group's own subtree (cycle guard)</li>
 *   <li>{@code level} is maintained recursively on create/move (top = 1)</li>
 *   <li>deleting a tree requires zero groups; deleting a group requires no child
 *       groups and no attached members; members are validated against t_asset</li>
 * </ul>
 */
@Service
public class GroupService {

    /** Sentinel "no self id" for uniqueness checks on create (no existing row to exclude). */
    private static final long NO_GROUP_ID = -1L;

    /** Level assigned to top-level groups (parent = {@link GroupRepository#TOP_LEVEL_PARENT}). */
    private static final int TOP_LEVEL_LEVEL = 1;

    /** Asset kinds that may join a group; monitor-kind rel rows would never render. */
    private static final List<AssetKind> GROUP_MEMBER_KINDS = List.of(AssetKind.DEVICE, AssetKind.SERVICE);

    /** "DEVICE/SERVICE" — derived once from {@link #GROUP_MEMBER_KINDS} for rejection messages. */
    private static final String GROUP_MEMBER_KIND_LIST = GROUP_MEMBER_KINDS.stream()
            .map(AssetKind::name)
            .collect(Collectors.joining("/"));

    /** Captions of the synthetic kind-tree roots, orphan buckets and ungrouped bucket. */
    private static final String SERVICE_ROOT_CAPTION   = "服务";
    private static final String DEVICE_ROOT_CAPTION    = "设备";
    private static final String ORPHAN_PROBE_CAPTION   = "孤儿监测器";
    private static final String ORPHAN_CONTROL_CAPTION = "孤儿控制器";
    private static final String UNGROUPED_CAPTION      = "未分组";

    /** Live asset lookup for /asset-tree building. */
    private final AssetStore store;
    private final GroupRepository repo;

    public GroupService(AssetStore store, GroupRepository repo) {
        this.store = store;
        this.repo  = repo;
    }

    // ======================== tree CRUD ========================

    public List<GroupTreeRow> listTrees() {
        return repo.findAllTrees();
    }

    public void createTree(String name, String caption, int sequence) {
        String trimmed = requireText(name, "Tree name");
        if (repo.findTreeByName(trimmed).isPresent()) {
            throw new IllegalArgumentException("Tree name already exists: " + trimmed);
        }
        repo.insertTree(trimmed, caption == null ? trimmed : caption, sequence);
    }

    /** Updates a tree; an omitted {@code sequence} keeps the stored sibling order. */
    public void updateTree(long id, String name, String caption, Integer sequence) {
        GroupTreeRow tree = repo.findTreeById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tree not found: " + id));
        String trimmed = requireText(name, "Tree name");
        repo.findTreeByName(trimmed)
                .filter(other -> other.id() != id)
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Tree name already exists: " + trimmed);
                });
        repo.updateTree(id, trimmed, caption, sequence != null ? sequence : tree.sequence());
    }

    public void deleteTree(long id) {
        GroupTreeRow tree = repo.findTreeById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tree not found: " + id));
        int groups = repo.countGroupsInTree(id);
        if (groups > 0) {
            throw new IllegalArgumentException(
                    "Tree '%s' still contains %d group(s); delete them first."
                            .formatted(tree.name(), groups));
        }
        repo.deleteTree(id);
    }

    // ======================== group CRUD ========================

    /** Groups of one tree with their member asset row ids, for the management UI. */
    public List<GroupVO> listGroupVOs(long treeId) {
        requireTree(treeId);
        Map<Long, List<Long>> members = repo.findMembersByTree(treeId);
        return repo.findAllGroups(treeId).stream()
                .map(g -> new GroupVO(
                        g.id(), g.treeId(), g.name(), g.caption(), g.parent(), g.level(),
                        g.sequence(), members.getOrDefault(g.id(), List.of())))
                .toList();
    }

    public void createGroup(long treeId, String name, String caption, long parentGroupId, int sequence) {
        requireTree(treeId);
        String trimmed = requireText(name, "Group name");
        requireUniqueGroupName(treeId, trimmed, NO_GROUP_ID);

        long parent = GroupRepository.TOP_LEVEL_PARENT;
        int  level  = TOP_LEVEL_LEVEL;
        if (parentGroupId != GroupRepository.TOP_LEVEL_PARENT) {
            GroupRow parentRow = requireGroupInTree(treeId, parentGroupId);
            parent = parentRow.id();
            level  = parentRow.level() + 1;
        }
        repo.insertGroup(treeId, trimmed, caption == null ? trimmed : caption, parent, level, sequence);
    }

    /** Renames a group; an omitted {@code sequence} keeps the stored sibling order. */
    public void renameGroup(long groupId, String name, String caption, Integer sequence) {
        GroupRow group = requireGroup(groupId);
        String trimmed = requireText(name, "Group name");
        requireUniqueGroupName(group.treeId(), trimmed, groupId);
        repo.updateGroup(groupId, trimmed, caption, group.parent(), group.level(),
                sequence != null ? sequence : group.sequence());
    }

    /**
     * Moves a group under a new parent (0 = top-level), maintaining subtree levels.
     * Resolves both endpoints from the tree's in-memory group list — the move needs
     * the whole list anyway for the cycle guard and the level shift.
     * <p>
     * Runs in the caller's transaction when composed by {@link #updateGroup} via
     * self-invocation (the proxy is bypassed); the {@code @Transactional} here
     * covers direct external calls only.
     */
    @Transactional
    public void moveGroup(long treeId, long groupId, long newParentGroupId) {
        requireTree(treeId);
        List<GroupRow> all = repo.findAllGroups(treeId);
        GroupRow group = requireGroupIn(all, groupId);

        long newParent = GroupRepository.TOP_LEVEL_PARENT;
        int  newLevel  = TOP_LEVEL_LEVEL;
        if (newParentGroupId != GroupRepository.TOP_LEVEL_PARENT) {
            GroupRow parentRow = requireGroupIn(all, newParentGroupId);
            if (isInSubtree(parentRow.id(), group.id(), all)) {
                throw new IllegalArgumentException(
                        "Cannot move group '%s' under its own subtree.".formatted(group.name()));
            }
            newParent = parentRow.id();
            newLevel  = parentRow.level() + 1;
        }
        repo.updateGroup(group.id(), group.name(), group.caption(), newParent, newLevel, group.sequence());

        int delta = newLevel - group.level();
        if (delta != 0) {
            shiftSubtreeLevels(all, group.id(), delta);
        }
    }

    /**
     * Atomically moves and/or renames a group — one transaction for the whole
     * PUT /groups/{id} request. An omitted treeId+parent pair keeps the location,
     * an omitted name keeps the name; caption/sequence follow renameGroup
     * semantics. Replaces the controller's two-call sequence whose completed
     * move stayed committed when the rename failed.
     * <p>
     * The move endpoint must be self-consistent: {@code treeId} and {@code parent}
     * are accepted together or not at all — a half-specified move is rejected
     * instead of silently degrading to a rename.
     */
    @Transactional
    public void updateGroup(long groupId, Long treeId, Long parent, String name, String caption, Integer sequence) {
        boolean hasTreeId  = treeId != null;
        boolean hasParent  = parent != null;
        boolean hasRename  = name != null;
        if (!hasTreeId && !hasParent && !hasRename) {
            throw new IllegalArgumentException("Nothing to update: provide name and/or parent+treeId.");
        }
        if (hasTreeId != hasParent) {
            throw new IllegalArgumentException("Incomplete move: provide both treeId and parent.");
        }
        if (hasTreeId) {
            moveGroup(treeId, groupId, parent);
        }
        if (hasRename) {
            // Update caption is optional: null falls back to the name, symmetric with create semantics.
            renameGroup(groupId, name, caption == null ? name : caption, sequence);
        }
    }

    public void deleteGroup(long groupId) {
        GroupRow group = requireGroup(groupId);
        for (GroupRow row : repo.findAllGroups(group.treeId())) {
            if (row.parent() == groupId) {
                throw new IllegalArgumentException(
                        "Group '%s' still has child group '%s'; delete it first."
                                .formatted(group.name(), row.name()));
            }
        }
        List<Long> members = repo.findMembersByTree(group.treeId()).get(groupId);
        if (members != null && !members.isEmpty()) {
            throw new IllegalArgumentException(
                    "Group '%s' still has %d attached asset(s); detach them first."
                            .formatted(group.name(), members.size()));
        }
        repo.deleteGroup(groupId);
    }

    @Transactional
    public void replaceGroupAssets(long groupId, List<Long> assetRowIds) {
        GroupRow group = requireGroup(groupId);
        if (assetRowIds != null) {
            Set<Long> seen = new HashSet<>();
            for (Long assetRowId : assetRowIds) {
                if (assetRowId == null) {
                    throw new IllegalArgumentException("Asset row id must not be null.");
                }
                if (!seen.add(assetRowId)) {
                    throw new IllegalArgumentException("Duplicate asset row id: " + assetRowId);
                }
                Optional<AssetRef> ref = repo.findAssetRef(assetRowId);
                if (ref.isEmpty()) {
                    throw new IllegalArgumentException("Asset row not found: " + assetRowId);
                }
                if (!GROUP_MEMBER_KINDS.contains(ref.get().kind())) {
                    throw new IllegalArgumentException(
                            ("Asset row %d is a %s; only %s assets may join groups.")
                                    .formatted(assetRowId, ref.get().kind(), GROUP_MEMBER_KIND_LIST));
                }
            }
        }
        repo.deleteRelsByGroup(group.id());
        if (assetRowIds != null) {
            for (Long assetRowId : assetRowIds) {
                repo.insertRel(assetRowId, group.id());
            }
        }
    }

    // ======================== tree building ========================

    /** Selector of the built-in virtual tree. */
    public static final String KIND_TREE = "kind";

    /**
     * Builds the forest for {@code GET /asset-tree?tree=kind|<treeId>}.
     * <p>
     * The selector is parsed and the tree's existence checked before the t_asset
     * reverse scan, so an invalid selector or an unknown tree id fails fast
     * without paying for the full-table lookup; each valid request performs
     * exactly one reverse scan.
     */
    public List<TreeNodeVO> buildAssetTree(String tree) {
        if (tree == null || tree.isBlank() || KIND_TREE.equals(tree)) {
            // One reverse map per request: every ASSET node gets its t_asset row id
            // (the group-membership id the frontend mount/unmount writes must use).
            return buildKindTree(repo.findRowIdsByRuntimeId());
        }
        long treeId;
        try {
            treeId = Long.parseLong(tree);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid tree selector: " + tree);
        }
        repo.findTreeById(treeId)
                .orElseThrow(() -> new IllegalArgumentException("Tree not found: " + treeId));
        return buildGroupTree(treeId, repo.findRowIdsByRuntimeId());
    }

    /** Virtual "by kind" tree: synthetic SERVICE / DEVICE roots, probes/controls under devices. */
    private List<TreeNodeVO> buildKindTree(Map<Integer, Long> rowIdsByRuntimeId) {
        List<TreeNodeVO> serviceChildren = new ArrayList<>();
        for (var asset : store.getAssetsByKind(AssetKind.SERVICE)) {
            serviceChildren.add(assetNode(asset, rowIdsByRuntimeId));
        }
        List<TreeNodeVO> deviceChildren = new ArrayList<>();
        for (var asset : store.getAssetsByKind(AssetKind.DEVICE)) {
            deviceChildren.add(assetNode(asset, rowIdsByRuntimeId));
        }
        List<TreeNodeVO> forest = new ArrayList<>();
        forest.add(new TreeNodeVO("KIND:SERVICE", "ASSET", AssetKind.SERVICE.name(),
                null, null, SERVICE_ROOT_CAPTION, SERVICE_ROOT_CAPTION, null, null, serviceChildren));
        forest.add(new TreeNodeVO("KIND:DEVICE", "ASSET", AssetKind.DEVICE.name(),
                null, null, DEVICE_ROOT_CAPTION, DEVICE_ROOT_CAPTION, null, null, deviceChildren));

        // Defensive: orphan monitors (parent is the neutral anchor) get their own top groups.
        appendOrphanGroup(forest, AssetKind.PROBE, ORPHAN_PROBE_CAPTION, rowIdsByRuntimeId);
        appendOrphanGroup(forest, AssetKind.CONTROL, ORPHAN_CONTROL_CAPTION, rowIdsByRuntimeId);
        return forest;
    }

    private void appendOrphanGroup(List<TreeNodeVO> forest, AssetKind kind, String caption,
                                   Map<Integer, Long> rowIdsByRuntimeId) {
        List<TreeNodeVO> orphans = new ArrayList<>();
        for (var asset : store.getAssetsByKind(kind)) {
            if (asset.getParent() != null && asset.getParent().getKind() == null) {
                orphans.add(assetNode(asset, rowIdsByRuntimeId));
            }
        }
        if (!orphans.isEmpty()) {
            forest.add(new TreeNodeVO("KIND:" + kind.name(), "ASSET", kind.name(),
                    null, null, caption, caption, null, null, orphans));
        }
    }

    /**
     * Custom tree: group nodes + member assets; unattached devices/services go to
     * 未分组. The caller has already validated that {@code treeId} exists, so this
     * method only reads the tree contents.
     */
    private List<TreeNodeVO> buildGroupTree(long treeId, Map<Integer, Long> rowIdsByRuntimeId) {
        List<GroupRow> groups = repo.findAllGroups(treeId);
        Map<Long, List<Long>> membersByGroup = repo.findMembersByTree(treeId);

        Map<Long, TreeNodeVO> groupNodes = new HashMap<>();
        for (GroupRow group : groups) {
            groupNodes.put(group.id(), new TreeNodeVO("GROUP:" + group.id(), "GROUP", null,
                    group.id(), null, group.name(), group.caption(), null, null, new ArrayList<>()));
        }
        Map<Long, List<GroupRow>> childrenByParent = new HashMap<>();
        for (GroupRow group : groups) {
            childrenByParent.computeIfAbsent(group.parent(), k -> new ArrayList<>()).add(group);
        }

        // Attach member assets (devices/services) and group children.
        Set<Long> attached = new HashSet<>();
        for (GroupRow group : groups) {
            TreeNodeVO node = groupNodes.get(group.id());
            for (Long assetRowId : membersByGroup.getOrDefault(group.id(), List.of())) {
                Optional<AssetRef> ref = repo.findAssetRef(assetRowId);
                if (ref.isEmpty() || !GROUP_MEMBER_KINDS.contains(ref.get().kind())) {
                    continue; // rel rows pointing at deleted/monitor-kind assets are ignored
                }
                Asset<?> asset = store.findAsset(ref.get().runtimeId());
                if (asset == null) {
                    continue; // t_asset row without a live runtime counterpart
                }
                attached.add((long) ref.get().runtimeId());
                node.children().add(assetNode(asset, rowIdsByRuntimeId));
            }
            for (GroupRow child : childrenByParent.getOrDefault(group.id(), List.of())) {
                node.children().add(groupNodes.get(child.id()));
            }
        }

        List<TreeNodeVO> forest = new ArrayList<>();
        for (GroupRow group : groups) {
            if (group.parent() == GroupRepository.TOP_LEVEL_PARENT) {
                forest.add(groupNodes.get(group.id()));
            }
        }

        // Ungrouped: devices/services attached to no group in THIS tree.
        List<TreeNodeVO> ungrouped = new ArrayList<>();
        for (var asset : store.getAssetsByKind(AssetKind.DEVICE)) {
            if (!attached.contains((long) asset.getId())) {
                ungrouped.add(assetNode(asset, rowIdsByRuntimeId));
            }
        }
        for (var asset : store.getAssetsByKind(AssetKind.SERVICE)) {
            if (!attached.contains((long) asset.getId())) {
                ungrouped.add(assetNode(asset, rowIdsByRuntimeId));
            }
        }
        if (!ungrouped.isEmpty()) {
            forest.add(new TreeNodeVO("UNGROUPED", "GROUP", null, null, null,
                    UNGROUPED_CAPTION, UNGROUPED_CAPTION, null, null, ungrouped));
        }
        return forest;
    }

    /**
     * Live asset node; device nodes carry their probe/control subtree from the
     * runtime store. {@code assetRowId} comes from the reverse map — null when
     * the asset has no view row, which keeps rendering alive while making the
     * node ineligible for membership writes (the frontend no-ops on null).
     */
    private TreeNodeVO assetNode(Asset<?> asset, Map<Integer, Long> rowIdsByRuntimeId) {
        List<TreeNodeVO> children = new ArrayList<>();
        if (asset instanceof CompoundAsset<?> compound && compound.isCompound()) {
            for (Asset<?> child : compound.children()) {
                children.add(assetNode(child, rowIdsByRuntimeId));
            }
        }
        return new TreeNodeVO("ASSET:" + asset.getId(), "ASSET",
                asset.getKind().name(), (long) asset.getId(),
                rowIdsByRuntimeId.get(asset.getId()),
                asset.getName(), asset.getCaption(),
                asset.getState() != null ? asset.getState().name() : null,
                asset.isEnabled(), children);
    }

    // ======================== helpers ========================

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank.");
        }
        return value.trim();
    }

    private void requireTree(long treeId) {
        repo.findTreeById(treeId)
                .orElseThrow(() -> new IllegalArgumentException("Tree not found: " + treeId));
    }

    private GroupRow requireGroup(long groupId) {
        return repo.findGroupById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    private GroupRow requireGroupInTree(long treeId, long groupId) {
        GroupRow row = requireGroup(groupId);
        if (row.treeId() != treeId) {
            throw new IllegalArgumentException(
                    "Group %d does not belong to tree %d.".formatted(groupId, treeId));
        }
        return row;
    }

    private void requireUniqueGroupName(long treeId, String name, long selfId) {
        repo.findGroupByName(treeId, name)
                .filter(existing -> existing.id() != selfId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Group name already exists in this tree: " + name);
                });
    }

    /** Resolves a group by id from an already-loaded tree listing. */
    private static GroupRow requireGroupIn(List<GroupRow> all, long groupId) {
        return all.stream()
                .filter(row -> row.id() == groupId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    /**
     * Whether {@code candidateId} is the subtree root itself or one of its descendants.
     * Walks the parent chain bounded by the group count — a valid tree never exceeds
     * that depth, so a longer walk means corrupt data (parent cycle) and stops there.
     */
    private static boolean isInSubtree(long candidateId, long rootId, List<GroupRow> all) {
        long current = candidateId;
        for (int depth = 0; depth <= all.size(); depth++) {
            if (current == rootId) {
                return true;
            }
            if (current == GroupRepository.TOP_LEVEL_PARENT) {
                return false;
            }
            current = requireGroupIn(all, current).parent();
        }
        return false;
    }

    /** Applies {@code delta} to every member of the subtree rooted at {@code rootId} (root itself already updated). */
    private void shiftSubtreeLevels(List<GroupRow> all, long rootId, int delta) {
        for (GroupRow row : all) {
            if (row.id() != rootId && isInSubtree(row.id(), rootId, all)) {
                repo.updateGroupLevel(row.id(), row.level() + delta);
            }
        }
    }
}
