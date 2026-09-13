package com.systar.server.service;

import com.systar.common.api.Result;
import com.systar.server.controller.GroupController;
import com.systar.server.dto.GroupReorderRequest;
import com.systar.server.repository.GroupRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Write-invariant coverage that needs the REAL service against the dev H2
 * schema: the tree row lock must serialize concurrent grouped writes, the
 * way concurrent API clients (no single-admin UI serialization) would race.
 * Deliberately NOT {@code @Transactional} at the test level — every service
 * call must run in its own transaction, exactly like separate HTTP requests.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("group write serialization (real service, dev H2)")
class GroupServiceWriteIntegrityTest {

    private static final String TREE_NAME = "write_integrity_tree";
    private static final int    THREADS   = 8;

    @Autowired
    private GroupService service;

    @Autowired
    private GroupController controller;

    @Autowired
    private GroupRepository repo;

    private long treeId;

    @BeforeEach
    void seedTree() {
        repo.insertTree(TREE_NAME, "写不变量树", 1);
        treeId = repo.findTreeByName(TREE_NAME).orElseThrow().id();
    }

    @AfterEach
    void cleanup() {
        SystarSecurityContext.clear();
        repo.findTreeByName(TREE_NAME).ifPresent(tree -> {
            // Repo-level deletes have no ordering constraint (t_group carries no
            // parent/tree_id foreign keys, only the PK) — a flat delete-all is safe.
            repo.findAllGroups(tree.id()).forEach(g -> repo.deleteGroup(g.id()));
            repo.deleteTree(tree.id());
        });
    }

    private long insertGroup(String name, int sequence) {
        repo.insertGroup(treeId, name, name, GroupRepository.TOP_LEVEL_PARENT, 1, sequence);
        return repo.findGroupByName(treeId, name).orElseThrow().id();
    }

    /** findAllGroups orders by sequence, id — the persisted sibling order. */
    private List<String> siblingOrder() {
        return repo.findAllGroups(treeId).stream()
                .map(GroupRepository.GroupRow::name)
                .toList();
    }

    @Test
    @DisplayName("concurrent same-name creates: exactly one wins, the losers get the service's own message")
    void createGroupSameNameConcurrentlyExactlyOneSucceeds() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<String>> outcomes = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                outcomes.add(pool.submit(() -> {
                    barrier.await();
                    try {
                        service.createGroup(treeId, "same_name", "同名",
                                GroupRepository.TOP_LEVEL_PARENT, 1);
                        return "OK";
                    } catch (IllegalArgumentException e) {
                        return "IAE:" + e.getMessage();
                    } catch (Exception e) {
                        // A raw DB error here means the unique-name invariant was left
                        // to the storage layer instead of being enforced under the lock.
                        return "OTHER:" + e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }));
            }

            int ok = 0, rejected = 0;
            List<String> unexpected = new ArrayList<>();
            for (Future<String> outcome : outcomes) {
                String result = outcome.get(30, TimeUnit.SECONDS);
                if ("OK".equals(result)) {
                    ok++;
                } else if (result.startsWith("IAE:") && result.contains("already exists in this tree")) {
                    rejected++;
                } else {
                    unexpected.add(result);
                }
            }

            assertThat(unexpected)
                    .as("every loser must be rejected by the service under the tree lock")
                    .isEmpty();
            assertThat(ok).isEqualTo(1);
            assertThat(rejected).isEqualTo(THREADS - 1);

            long rows = repo.findAllGroups(treeId).stream()
                    .filter(g -> "same_name".equals(g.name()))
                    .count();
            assertThat(rows).as("exactly one row carries the raced name").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("reorder endpoint rewrites sequence 1..N atomically and replays idempotently")
    void reorderEndpointRewritesSequenceIdempotently() {
        // The PermissionAspect on the reorder route short-circuits without a user.
        SystarSecurityContext.set(new SystarUser(1L, "reorder-tester", "*"));
        long a = insertGroup("a", 1);
        long b = insertGroup("b", 2);
        long c = insertGroup("c", 3);

        Result<Void> result = controller.reorderGroups(treeId,
                new GroupReorderRequest(null, List.of(c, a, b)));

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(siblingOrder()).containsExactly("c", "a", "b");

        // Replaying the same list leaves the stored order unchanged.
        controller.reorderGroups(treeId, new GroupReorderRequest(null, List.of(c, a, b)));
        assertThat(siblingOrder()).containsExactly("c", "a", "b");

        // The new order is the basis for the NEXT reorder (sequences were rewritten 1..N).
        controller.reorderGroups(treeId, new GroupReorderRequest(null, List.of(a, b, c)));
        assertThat(siblingOrder()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("reorder endpoint rejects an incomplete list and writes nothing")
    void reorderEndpointRejectsMissingChild() {
        SystarSecurityContext.set(new SystarUser(1L, "reorder-tester", "*"));
        long aId = insertGroup("a", 1);
        long bId = insertGroup("b", 2);

        assertThatThrownBy(() -> controller.reorderGroups(treeId,
                new GroupReorderRequest(null, List.of(aId))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining(String.valueOf(bId));

        assertThat(siblingOrder()).containsExactly("a", "b");
    }
}
