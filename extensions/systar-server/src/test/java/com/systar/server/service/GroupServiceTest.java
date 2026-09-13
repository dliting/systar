package com.systar.server.service;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.server.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class GroupServiceTest {

    private GroupRepository repo;
    private AssetStore store;
    private GroupService service;

    private static final GroupRepository.GroupTreeRow TREE =
            new GroupRepository.GroupTreeRow(1L, "region", "按区域", 1);

    @BeforeEach
    void setUp() {
        repo    = mock(GroupRepository.class);
        store   = new AssetStore();
        service = new GroupService(store, repo);
        when(repo.findTreeById(1L)).thenReturn(Optional.of(TREE));
        when(repo.lockTree(1L)).thenReturn(Optional.of(TREE));
        when(repo.findAllGroups(1L)).thenReturn(List.of());
        when(repo.findMembersByTree(1L)).thenReturn(Map.of());
    }

    // ---- tree CRUD ----

    @Test
    @DisplayName("createTree rejects blank/duplicate name")
    void createTreeValidation() {
        assertThatThrownBy(() -> service.createTree("  ", "x", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        service.createTree("region2", "树2", 2);
        verify(repo).insertTree("region2", "树2", 2);
        when(repo.findTreeByName("region2")).thenReturn(Optional.of(
                new GroupRepository.GroupTreeRow(9L, "region2", "树2", 2)));
        assertThatThrownBy(() -> service.createTree("region2", "树2b", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("deleteTree requires the tree to have no groups")
    void deleteTreeRequiresEmpty() {
        when(repo.countGroupsInTree(1L)).thenReturn(2);
        assertThatThrownBy(() -> service.deleteTree(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("still contains 2 group");
        verify(repo, never()).deleteTree(anyLong());

        when(repo.countGroupsInTree(1L)).thenReturn(0);
        service.deleteTree(1L);
        verify(repo).deleteTree(1L);
    }

    @Test
    @DisplayName("updateTree validates, trims and persists the rename")
    void updateTreeValidation() {
        // happy path: trimmed name passes through
        service.updateTree(1L, "  region  ", "按区域2", 3);
        verify(repo).updateTree(1L, "region", "按区域2", 3);

        // rename to a name owned by ANOTHER tree is rejected
        when(repo.findTreeByName("region2")).thenReturn(Optional.of(
                new GroupRepository.GroupTreeRow(9L, "region2", "树2", 2)));
        assertThatThrownBy(() -> service.updateTree(1L, "region2", "x", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        // rename to its OWN name is allowed (self-exclusion)
        when(repo.findTreeByName("region")).thenReturn(Optional.of(TREE));
        service.updateTree(1L, "region", "按区域", 1);
        verify(repo).updateTree(1L, "region", "按区域", 1);

        // unknown id rejected
        assertThatThrownBy(() -> service.updateTree(2L, "x", "x", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree not found");
    }

    // ---- group CRUD ----

    @Test
    @DisplayName("createGroup validates parent and computes level")
    void createGroupLevel() {
        when(repo.findGroupById(5L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(5L, 1L, "parent", "父", 0L, 1, 1)));

        service.createGroup(1L, "child", "子", 5L, 1);

        verify(repo).insertGroup(1L, "child", "子", 5L, 2, 1);
    }

    @Test
    @DisplayName("createGroup rejects a parent from another tree")
    void createGroupRejectsForeignParent() {
        when(repo.findGroupById(6L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(6L, 2L, "foreign", "F", 0L, 1, 1)));

        assertThatThrownBy(() -> service.createGroup(1L, "child", "子", 6L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to tree");
        verify(repo, never()).insertGroup(anyLong(), anyString(), anyString(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("renameGroup validates name and keeps hierarchy fields")
    void renameGroupKeepsHierarchy() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 5L, 2, 3)));

        // happy path: name/caption/sequence updated, parent and level untouched
        service.renameGroup(7L, "  g2  ", "G2", 4);
        verify(repo).updateGroup(7L, "g2", "G2", 5L, 2, 4);

        // rename to a sibling's name is rejected
        when(repo.findGroupByName(1L, "taken")).thenReturn(Optional.of(
                new GroupRepository.GroupRow(8L, 1L, "taken", "T", 0L, 1, 1)));
        assertThatThrownBy(() -> service.renameGroup(7L, "taken", "x", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        // rename to its OWN name is allowed (self-exclusion)
        when(repo.findGroupByName(1L, "g2")).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g2", "G2", 5L, 2, 4)));
        service.renameGroup(7L, "g2", "G2", 4);
        verify(repo, times(2)).updateGroup(7L, "g2", "G2", 5L, 2, 4);
    }

    @Test
    @DisplayName("renameGroup with an omitted sequence keeps the stored sibling order")
    void renameGroupKeepsSequenceWhenOmitted() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 5L, 2, 3)));

        service.renameGroup(7L, "g2", "G2", null);

        // sequence 3 is the stored value — a rename must never reset it to 0
        verify(repo).updateGroup(7L, "g2", "G2", 5L, 2, 3);
    }

    @Test
    @DisplayName("updateTree with an omitted sequence keeps the stored order")
    void updateTreeKeepsSequenceWhenOmitted() {
        // TREE carries sequence 1; an update without sequence must not reset it
        service.updateTree(1L, "region2", "树2新名", null);

        verify(repo).updateTree(1L, "region2", "树2新名", 1);
    }

    @Test
    @DisplayName("moveGroup rejects cycles and maintains subtree levels")
    void moveGroupCycleAndLevel() {
        // tree: root -> a(1) -> b(2); move a under b must fail
        GroupRepository.GroupRow a = new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1);
        GroupRepository.GroupRow b = new GroupRepository.GroupRow(2L, 1L, "b", "B", 1L, 2, 1);
        when(repo.findAllGroups(1L)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.moveGroup(1L, 1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtree");

        // legit move: b to top-level → level 1
        service.moveGroup(1L, 2L, GroupRepository.TOP_LEVEL_PARENT);
        verify(repo).updateGroup(eq(2L), eq("b"), eq("B"), eq(0L), eq(1), eq(1));
    }

    @Test
    @DisplayName("updateGroup rejects a request with neither name nor parent+treeId")
    void updateGroupNothingToUpdate() {
        assertThatThrownBy(() -> service.updateGroup(7L, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing to update");
        verify(repo, never()).updateGroup(anyLong(), anyString(), anyString(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("updateGroup rejects a half-specified move: treeId without parent")
    void updateGroupRejectsTreeIdWithoutParent() {
        assertThatThrownBy(() -> service.updateGroup(7L, 1L, null, "g2", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incomplete move");
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("updateGroup rejects a half-specified move: parent without treeId")
    void updateGroupRejectsParentWithoutTreeId() {
        assertThatThrownBy(() -> service.updateGroup(7L, null, 5L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incomplete move");
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("updateGroup writes the move before rejecting the conflicting rename (rollback job of @Transactional)")
    void updateGroupMovesThenRejectsConflictingRename() {
        GroupRepository.GroupRow g = new GroupRepository.GroupRow(7L, 1L, "g", "G", 5L, 2, 3);
        when(repo.findGroupById(7L)).thenReturn(Optional.of(g));
        when(repo.findAllGroups(1L)).thenReturn(List.of(g));
        when(repo.findGroupByName(1L, "taken")).thenReturn(Optional.of(
                new GroupRepository.GroupRow(8L, 1L, "taken", "T", 0L, 1, 1)));

        assertThatThrownBy(() -> service.updateGroup(7L, 1L, GroupRepository.TOP_LEVEL_PARENT, "taken", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        // The move write happened; the rename write must not — the transactional
        // boundary around updateGroup undoes the move on the propagated exception.
        verify(repo).updateGroup(7L, "g", "G", GroupRepository.TOP_LEVEL_PARENT, 1, 3);
        verify(repo, never()).updateGroup(eq(7L), eq("taken"), eq("taken"), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("updateGroup with move only keeps the stored name")
    void updateGroupMoveOnlyKeepsName() {
        GroupRepository.GroupRow g = new GroupRepository.GroupRow(7L, 1L, "g", "G", 5L, 2, 3);
        when(repo.findGroupById(7L)).thenReturn(Optional.of(g));
        when(repo.findAllGroups(1L)).thenReturn(List.of(g));

        service.updateGroup(7L, 1L, GroupRepository.TOP_LEVEL_PARENT, null, null, null);

        verify(repo).updateGroup(7L, "g", "G", GroupRepository.TOP_LEVEL_PARENT, 1, 3);
    }

    @Test
    @DisplayName("updateGroup with rename only keeps the location; null caption falls back to the name")
    void updateGroupRenameOnlyKeepsLocationAndCaptionFallback() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 5L, 2, 3)));

        service.updateGroup(7L, null, null, "g2", null, null);

        verify(repo).updateGroup(7L, "g2", "g2", 5L, 2, 3);
    }

    @Test
    @DisplayName("moveGroup shifts descendant levels recursively")
    void moveGroupShiftsDescendantLevels() {
        GroupRepository.GroupRow a = new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1);
        GroupRepository.GroupRow b = new GroupRepository.GroupRow(2L, 1L, "b", "B", 1L, 2, 1);
        GroupRepository.GroupRow c = new GroupRepository.GroupRow(3L, 1L, "c", "C", 2L, 3, 1);
        when(repo.findAllGroups(1L)).thenReturn(List.of(a, b, c));

        service.moveGroup(1L, 2L, GroupRepository.TOP_LEVEL_PARENT);

        verify(repo).updateGroup(2L, "b", "B", 0L, 1, 1);
        verify(repo).updateGroupLevel(3L, 2);
    }

    @Test
    @DisplayName("deleteGroup requires no children and no members")
    void deleteGroupConstraints() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1),
                new GroupRepository.GroupRow(8L, 1L, "sub", "S", 7L, 2, 1)));
        assertThatThrownBy(() -> service.deleteGroup(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("child group");

        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));
        when(repo.findMembersByTree(1L)).thenReturn(Map.of(7L, List.of(22L)));
        assertThatThrownBy(() -> service.deleteGroup(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attached");

        when(repo.findMembersByTree(1L)).thenReturn(Map.of());
        service.deleteGroup(7L);
        verify(repo).deleteGroup(7L);
    }

    @Test
    @DisplayName("replaceGroupAssets accepts DEVICE/SERVICE members, then swaps rels")
    void replaceGroupAssets() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));
        // 99L resolves to no t_asset row — absent from the batch map.
        when(repo.findAssetRefs(anyCollection())).thenReturn(Map.of(
                22L, new GroupRepository.AssetRef(AssetKind.DEVICE, 1003),
                10L, new GroupRepository.AssetRef(AssetKind.SERVICE, 100)));

        assertThatThrownBy(() -> service.replaceGroupAssets(7L, List.of(22L, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        service.replaceGroupAssets(7L, List.of(22L, 10L));
        verify(repo).deleteRelsByGroup(7L);
        verify(repo).insertRel(22L, 7L);
        verify(repo).insertRel(10L, 7L);
    }

    @Test
    @DisplayName("replaceGroupAssets with a null list clears all rels")
    void replaceGroupAssetsNullClears() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));

        service.replaceGroupAssets(7L, null);

        verify(repo).deleteRelsByGroup(7L);
        verify(repo, never()).insertRel(anyLong(), anyLong());
    }

    @Test
    @DisplayName("replaceGroupAssets rejects a null asset id")
    void replaceGroupAssetsRejectsNullElement() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));

        assertThatThrownBy(() -> service.replaceGroupAssets(7L, Arrays.asList(null, 22L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        verify(repo, never()).deleteRelsByGroup(anyLong());
    }

    @Test
    @DisplayName("replaceGroupAssets rejects duplicate asset ids")
    void replaceGroupAssetsRejectsDuplicates() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));
        // Duplicates are rejected by the in-memory check before any query runs.

        assertThatThrownBy(() -> service.replaceGroupAssets(7L, List.of(22L, 22L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate asset")
                .hasMessageContaining("22");
        verify(repo, never()).deleteRelsByGroup(anyLong());
        verify(repo, never()).insertRel(anyLong(), anyLong());
    }

    @Test
    @DisplayName("replaceGroupAssets rejects monitor-kind members (PROBE/CONTROL)")
    void replaceGroupAssetsRejectsMonitorKind() {
        when(repo.findGroupById(7L)).thenReturn(Optional.of(
                new GroupRepository.GroupRow(7L, 1L, "g", "G", 0L, 1, 1)));
        when(repo.findAssetRefs(anyCollection())).thenReturn(Map.of(
                22L, new GroupRepository.AssetRef(AssetKind.PROBE, 2001),
                40L, new GroupRepository.AssetRef(AssetKind.CONTROL, 3001)));

        assertThatThrownBy(() -> service.replaceGroupAssets(7L, List.of(22L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("22")
                .hasMessageContaining("PROBE")
                .hasMessageContaining("DEVICE/SERVICE");
        verify(repo, never()).deleteRelsByGroup(anyLong());
        verify(repo, never()).insertRel(anyLong(), anyLong());

        assertThatThrownBy(() -> service.replaceGroupAssets(7L, List.of(40L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("40")
                .hasMessageContaining("CONTROL");
        verify(repo, never()).deleteRelsByGroup(anyLong());
        verify(repo, never()).insertRel(anyLong(), anyLong());
    }

    // ---- sibling reorder ----

    @Test
    @DisplayName("reorderSiblings rewrites sequence 1..N in the given order (top level)")
    void reorderSiblingsRewritesSequences() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1),
                new GroupRepository.GroupRow(2L, 1L, "b", "B", 0L, 1, 2)));

        service.reorderSiblings(1L, GroupRepository.TOP_LEVEL_PARENT, List.of(2L, 1L));

        verify(repo).updateGroupSequence(2L, 1);
        verify(repo).updateGroupSequence(1L, 2);
    }

    @Test
    @DisplayName("reorderSiblings reorders the children of a nested parent group")
    void reorderSiblingsNestedParent() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1),
                new GroupRepository.GroupRow(2L, 1L, "deep", "D", 1L, 2, 1)));

        service.reorderSiblings(1L, 1L, List.of(2L));

        verify(repo).updateGroupSequence(2L, 1);
    }

    @Test
    @DisplayName("reorderSiblings rejects a list that misses an existing child")
    void reorderSiblingsRejectsMissingChild() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1),
                new GroupRepository.GroupRow(2L, 1L, "b", "B", 0L, 1, 2)));

        assertThatThrownBy(() -> service.reorderSiblings(
                1L, GroupRepository.TOP_LEVEL_PARENT, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("2");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings rejects an id that lives under a different parent")
    void reorderSiblingsRejectsOtherParentsChild() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1),
                new GroupRepository.GroupRow(2L, 1L, "deep", "D", 1L, 2, 1)));

        // 2 is a child of group 1, not of the top level
        assertThatThrownBy(() -> service.reorderSiblings(
                1L, GroupRepository.TOP_LEVEL_PARENT, List.of(1L, 2L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent")
                .hasMessageContaining("2");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings rejects an id from another tree (absent from this tree's listing)")
    void reorderSiblingsRejectsCrossTreeId() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1)));

        assertThatThrownBy(() -> service.reorderSiblings(
                1L, GroupRepository.TOP_LEVEL_PARENT, List.of(1L, 77L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("77")
                .hasMessageContaining("tree");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings fails fast when the parent group itself is unknown to the tree")
    void reorderSiblingsRejectsUnknownParent() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1)));

        assertThatThrownBy(() -> service.reorderSiblings(1L, 99L, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Group not found")
                .hasMessageContaining("99");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings rejects duplicate ids")
    void reorderSiblingsRejectsDuplicates() {
        when(repo.findAllGroups(1L)).thenReturn(List.of(
                new GroupRepository.GroupRow(1L, 1L, "a", "A", 0L, 1, 1),
                new GroupRepository.GroupRow(2L, 1L, "b", "B", 0L, 1, 2)));

        assertThatThrownBy(() -> service.reorderSiblings(
                1L, GroupRepository.TOP_LEVEL_PARENT, List.of(1L, 2L, 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("1");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings rejects a null id element")
    void reorderSiblingsRejectsNullElement() {
        assertThatThrownBy(() -> service.reorderSiblings(
                1L, GroupRepository.TOP_LEVEL_PARENT, Arrays.asList(1L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings accepts an empty list when the parent has no children")
    void reorderSiblingsEmptyListWithNoChildren() {
        when(repo.findAllGroups(1L)).thenReturn(List.of());

        service.reorderSiblings(1L, GroupRepository.TOP_LEVEL_PARENT, List.of());

        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    @Test
    @DisplayName("reorderSiblings fails fast on an unknown tree")
    void reorderSiblingsUnknownTree() {
        assertThatThrownBy(() -> service.reorderSiblings(2L, GroupRepository.TOP_LEVEL_PARENT, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree not found");
        verify(repo, never()).updateGroupSequence(anyLong(), anyInt());
    }

    // ---- write-transaction isolation contract ----

    /** Every public write entry point that takes the tree row lock. */
    private static final List<String> WRITE_METHOD_NAMES = List.of(
            "createGroup", "renameGroup", "moveGroup", "updateGroup",
            "deleteGroup", "replaceGroupAssets", "deleteTree", "reorderSiblings");

    /**
     * The lock invariant needs READ_COMMITTED isolation, not just the tree row
     * lock: under InnoDB's default REPEATABLE_READ the read view is taken by
     * the transaction's FIRST plain read (the pre-lock requireTree/requireGroup),
     * and a locking read does not refresh it — post-lock validation reads
     * (findAllGroups, uniqueness, delete constraints) would stay on the stale
     * snapshot even after waiting for and acquiring the lock. H2 (the test DB)
     * is READ_COMMITTED by default, so the behavior cannot be reproduced here;
     * this pins the annotation contract against accidental removal instead.
     */
    @Test
    @DisplayName("every grouped-write transaction pins READ_COMMITTED isolation")
    void writeTransactionsDeclareReadCommitted() {
        List<String> checked = new ArrayList<>();
        for (Method method : GroupService.class.getDeclaredMethods()) {
            if (!WRITE_METHOD_NAMES.contains(method.getName())) {
                continue;
            }
            checked.add(method.getName());
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).as(method.getName()).isNotNull();
            assertThat(tx.isolation()).as(method.getName()).isEqualTo(Isolation.READ_COMMITTED);
        }
        // Typo guard: all eight write entry points were actually inspected.
        assertThat(checked).containsExactlyInAnyOrderElementsOf(WRITE_METHOD_NAMES);
    }

    @Test
    @DisplayName("listTrees passes the repository listing through")
    void listTreesPassthrough() {
        GroupRepository.GroupTreeRow tree2 = new GroupRepository.GroupTreeRow(2L, "t2", "树2", 2);
        when(repo.findAllTrees()).thenReturn(List.of(TREE, tree2));
        assertThat(service.listTrees()).containsExactly(TREE, tree2);
    }

    // ---- tree building ----

    @Test
    @DisplayName("buildAssetTree rejects a non-numeric selector before the t_asset reverse scan")
    void invalidTreeSelectorFailsFastWithoutReverseScan() {
        assertThatThrownBy(() -> service.buildAssetTree("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tree selector");
        verify(repo, never()).findRowIdsByRuntimeId();
    }

    @Test
    @DisplayName("buildAssetTree rejects an unknown numeric tree before the t_asset reverse scan")
    void unknownNumericTreeSelectorFailsFastWithoutReverseScan() {
        when(repo.findTreeById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildAssetTree("42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree not found");
        verify(repo, never()).findRowIdsByRuntimeId();
    }

    @Test
    @DisplayName("buildAssetTree rejects an overflowing numeric tree before the t_asset reverse scan")
    void overlongNumericTreeSelectorFailsFastWithoutReverseScan() {
        assertThatThrownBy(() -> service.buildAssetTree("99999999999999999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tree selector");
        verify(repo, never()).findRowIdsByRuntimeId();
    }
}
