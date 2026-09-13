package com.systar.server.controller;

import com.systar.server.dto.GroupRequest;
import com.systar.server.repository.GroupRepository;
import com.systar.server.repository.GroupRepository.GroupRow;
import com.systar.server.repository.GroupRepository.GroupTreeRow;
import com.systar.server.security.SystarSecurityContext;
import com.systar.server.security.SystarUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The PUT /groups/{id} contract with the REAL service against the dev H2 schema:
 * a combined move+rename request must be atomic — when the rename is rejected,
 * the already-executed move must roll back with it. Deliberately NOT
 * {@code @Transactional} at the test level: each service call must run in its
 * own transaction for the commit/rollback split to be observable.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("PUT /groups/{id} move+rename atomicity (real service, dev H2)")
class GroupUpdateAtomicityTest {

    private static final String TREE_NAME = "atomicity_tree";

    @Autowired
    private GroupController controller;

    @Autowired
    private GroupRepository repo;

    private long treeId;
    private long groupBId;
    private long groupCId;

    @BeforeEach
    void seedTree() {
        // The PermissionAspect on PUT /groups/{id} short-circuits without a user.
        SystarSecurityContext.set(new SystarUser(1L, "atomicity-tester", "*"));
        repo.insertTree(TREE_NAME, "原子性树", 1);
        GroupTreeRow tree = repo.findTreeByName(TREE_NAME).orElseThrow();
        treeId = tree.id();

        // A("X") top-level; C top-level; B under C; D top-level owns the conflicting name.
        repo.insertGroup(treeId, "X", "X", GroupRepository.TOP_LEVEL_PARENT, 1, 1);
        repo.insertGroup(treeId, "C", "C", GroupRepository.TOP_LEVEL_PARENT, 1, 2);
        groupCId = repo.findGroupByName(treeId, "C").orElseThrow().id();
        repo.insertGroup(treeId, "B", "B", groupCId, 2, 1);
        groupBId = repo.findGroupByName(treeId, "B").orElseThrow().id();
        repo.insertGroup(treeId, "D", "D", GroupRepository.TOP_LEVEL_PARENT, 1, 3);
    }

    @AfterEach
    void cleanup() {
        SystarSecurityContext.clear();
        repo.findTreeByName(TREE_NAME).ifPresent(tree -> {
            List<GroupRow> remaining = repo.findAllGroups(tree.id());
            while (!remaining.isEmpty()) {
                // Leaf-first pass: never mask a test failure with a cleanup error.
                List<GroupRow> current = remaining;
                List<GroupRow> leaves = current.stream()
                        .filter(g -> current.stream().noneMatch(o -> o.parent() == g.id()))
                        .toList();
                if (leaves.isEmpty()) {
                    break; // corrupt state — leave it visible instead of looping forever
                }
                leaves.forEach(g -> repo.deleteGroup(g.id()));
                remaining = repo.findAllGroups(tree.id());
            }
            repo.deleteTree(tree.id());
        });
    }

    @Test
    @DisplayName("a combined move+rename rolls back the move when the rename conflicts")
    void renameConflictRollsBackMove() {
        assertThatThrownBy(() -> controller.updateGroup(groupBId,
                new GroupRequest(treeId, "D", null, GroupRepository.TOP_LEVEL_PARENT, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        assertThat(repo.findGroupById(groupBId).orElseThrow().parent())
                .as("the move must roll back together with the failed rename")
                .isEqualTo(groupCId);
    }

    @Test
    @DisplayName("a legal combined move+rename persists both changes in the one transaction")
    void legalMoveAndRenamePersistsBoth() {
        controller.updateGroup(groupBId,
                new GroupRequest(treeId, "E", null, GroupRepository.TOP_LEVEL_PARENT, null));

        GroupRow moved = repo.findGroupById(groupBId).orElseThrow();
        assertThat(moved.parent())
                .as("the move must be committed")
                .isEqualTo(GroupRepository.TOP_LEVEL_PARENT);
        assertThat(moved.name())
                .as("the rename must be committed alongside the move")
                .isEqualTo("E");
    }
}
