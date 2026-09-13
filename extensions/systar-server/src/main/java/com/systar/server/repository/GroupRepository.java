package com.systar.server.repository;

import com.systar.monitor.asset.AssetKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JdbcTemplate data access for the asset-group tables
 * ({@code t_group_tree}, {@code t_group}, {@code t_asset_group_rel}).
 * <p>
 * No recursive SQL: hierarchy walks happen in {@code GroupService} over the
 * in-memory group list. SQL exceptions propagate (fail-fast, no swallow).
 */
@Repository
public class GroupRepository {

    private static final Logger log = LoggerFactory.getLogger(GroupRepository.class);

    /** Sentinel parent id meaning "top-level within the tree" (aligns t_group.parent convention). */
    public static final long TOP_LEVEL_PARENT = 0L;

    private final JdbcTemplate jdbc;

    public GroupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record GroupTreeRow(long id, String name, String caption, int sequence) {}

    public record GroupRow(long id, long treeId, String name, String caption,
                           long parent, int level, int sequence) {}

    // ======================== Row Mappers (ResultSet → Record) ========================

    private static GroupTreeRow mapTreeRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GroupTreeRow(
                rs.getLong("id"), rs.getString("name"),
                rs.getString("caption"), rs.getInt("sequence"));
    }

    private static GroupRow mapGroupRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GroupRow(
                rs.getLong("id"), rs.getLong("tree_id"), rs.getString("name"),
                rs.getString("caption"), rs.getLong("parent"),
                rs.getInt("level"), rs.getInt("sequence"));
    }

    // ======================== t_group_tree ========================

    public List<GroupTreeRow> findAllTrees() {
        return jdbc.query(
                "SELECT id, name, caption, sequence FROM t_group_tree ORDER BY sequence, id",
                (rs, i) -> mapTreeRow(rs));
    }

    public Optional<GroupTreeRow> findTreeById(long id) {
        List<GroupTreeRow> rows = jdbc.query(
                "SELECT id, name, caption, sequence FROM t_group_tree WHERE id=?",
                (rs, i) -> mapTreeRow(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<GroupTreeRow> findTreeByName(String name) {
        List<GroupTreeRow> rows = jdbc.query(
                "SELECT id, name, caption, sequence FROM t_group_tree WHERE name=?",
                (rs, i) -> mapTreeRow(rs), name);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Locks a group tree's row for the rest of the transaction, serializing all
     * grouped writes on that tree (SELECT ... FOR UPDATE). Empty when the tree
     * does not exist — callers treat that as the fail-fast unknown-tree case.
     * <p>
     * Deadlock safety: every grouped write locks exactly one tree row and takes
     * it before any group/rel write, so concurrent writers on the same tree
     * simply queue up, and no write path ever holds two tree locks (a move's
     * target tree is the group's own tree — cross-tree moves do not exist).
     */
    public Optional<GroupTreeRow> lockTree(long id) {
        List<GroupTreeRow> rows = jdbc.query(
                "SELECT id, name, caption, sequence FROM t_group_tree WHERE id=? FOR UPDATE",
                (rs, i) -> mapTreeRow(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insertTree(String name, String caption, int sequence) {
        jdbc.update("INSERT INTO t_group_tree (name, caption, sequence) VALUES (?, ?, ?)",
                name, caption, sequence);
    }

    public void updateTree(long id, String name, String caption, int sequence) {
        jdbc.update("UPDATE t_group_tree SET name=?, caption=?, sequence=? WHERE id=?",
                name, caption, sequence, id);
    }

    public void deleteTree(long id) {
        jdbc.update("DELETE FROM t_group_tree WHERE id=?", id);
    }

    public int countGroupsInTree(long treeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_group WHERE tree_id=?", Integer.class, treeId);
        return count != null ? count : 0;
    }

    // ======================== t_group ========================

    public List<GroupRow> findAllGroups(long treeId) {
        return jdbc.query(
                "SELECT id, tree_id, name, caption, parent, level, sequence FROM t_group "
                        + "WHERE tree_id=? ORDER BY sequence, id",
                (rs, i) -> mapGroupRow(rs), treeId);
    }

    public Optional<GroupRow> findGroupById(long id) {
        List<GroupRow> rows = jdbc.query(
                "SELECT id, tree_id, name, caption, parent, level, sequence FROM t_group WHERE id=?",
                (rs, i) -> mapGroupRow(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<GroupRow> findGroupByName(long treeId, String name) {
        List<GroupRow> rows = jdbc.query(
                "SELECT id, tree_id, name, caption, parent, level, sequence FROM t_group "
                        + "WHERE tree_id=? AND name=?",
                (rs, i) -> mapGroupRow(rs), treeId, name);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insertGroup(long treeId, String name, String caption, long parent, int level, int sequence) {
        jdbc.update(
                "INSERT INTO t_group (tree_id, name, caption, parent, level, sequence) VALUES (?, ?, ?, ?, ?, ?)",
                treeId, name, caption, parent, level, sequence);
    }

    public void updateGroup(long id, String name, String caption, long parent, int level, int sequence) {
        jdbc.update(
                "UPDATE t_group SET name=?, caption=?, parent=?, level=?, sequence=? WHERE id=?",
                name, caption, parent, level, sequence, id);
    }

    public void updateGroupLevel(long id, int level) {
        jdbc.update("UPDATE t_group SET level=? WHERE id=?", level, id);
    }

    /** Lightweight sibling-order write for the atomic reorder endpoint. */
    public void updateGroupSequence(long id, int sequence) {
        jdbc.update("UPDATE t_group SET sequence=? WHERE id=?", sequence, id);
    }

    public void deleteGroup(long id) {
        jdbc.update("DELETE FROM t_group WHERE id=?", id);
    }

    // ======================== t_asset_group_rel ========================

    /** Returns group id -> direct member asset row ids (t_asset.id) for one tree. */
    public Map<Long, List<Long>> findMembersByTree(long treeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.group_id, r.asset_id FROM t_asset_group_rel r "
                        + "JOIN t_group g ON g.id = r.group_id WHERE g.tree_id=? "
                        + "ORDER BY r.group_id, r.asset_id", treeId);
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.computeIfAbsent(((Number) row.get("group_id")).longValue(), k -> new ArrayList<>())
                    .add(((Number) row.get("asset_id")).longValue());
        }
        return result;
    }

    public void deleteRelsByGroup(long groupId) {
        jdbc.update("DELETE FROM t_asset_group_rel WHERE group_id=?", groupId);
    }

    public void deleteRelsByAsset(long assetRowId) {
        jdbc.update("DELETE FROM t_asset_group_rel WHERE asset_id=?", assetRowId);
    }

    public void insertRel(long assetRowId, long groupId) {
        jdbc.update("INSERT INTO t_asset_group_rel (asset_id, group_id) VALUES (?, ?)",
                assetRowId, groupId);
    }

    /** Kind and runtime id of a t_asset row (see {@link #findAssetRefs}). */
    public record AssetRef(AssetKind kind, int runtimeId) {}

    /** t_asset columns linking a view row to its per-kind runtime row. */
    private static final List<String> RUNTIME_ID_COLUMNS = List.of(
            "device_id", "service_id", "probe_id", "control_id");

    /**
     * Resolves t_asset row ids into kind + runtime id in ONE query.
     * <p>
     * {@code t_asset_group_rel.asset_id} and {@code t_asset.parent_id} live in the
     * t_asset row-id space, while the runtime {@code AssetStore} keys assets by the
     * per-kind row id ({@code t_device/t_service/t_probe/t_control.id}); the FK
     * columns bridge the two spaces. Ids that are missing, carry an unknown kind
     * code or link to no runtime asset are simply absent from the map (member
     * validation and rendering treat all three as "no groupable asset row").
     * Callers pass realistically bounded lists (one group's members or one
     * tree's member set), so a single IN(...) needs no chunking. Elements
     * must be non-null — {@code List.copyOf} enforces this fail-fast at the
     * repository boundary.
     */
    public Map<Long, AssetRef> findAssetRefs(Collection<Long> assetRowIds) {
        if (assetRowIds == null || assetRowIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(assetRowIds.size(), "?"));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, kind, device_id, service_id, probe_id, control_id FROM t_asset "
                        + "WHERE id IN (" + placeholders + ")",
                List.copyOf(assetRowIds).toArray());
        Map<Long, AssetRef> refsById = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            AssetKind kind = AssetKind.fromCode(((Number) row.get("kind")).intValue());
            int runtimeId  = firstRuntimeIdOfRow(row);
            if (kind == null || runtimeId <= 0) {
                continue; // same defensive skip as the single-row lookup
            }
            refsById.put(((Number) row.get("id")).longValue(), new AssetRef(kind, runtimeId));
        }
        return refsById;
    }

    /**
     * Reverse map runtime id -&gt; t_asset row id across all linked view rows, in one
     * query. Tree rendering uses it to annotate every ASSET node with its group
     * membership id ({@code t_asset_group_rel.asset_id}). Rows with an unknown
     * kind code or no per-kind link never enter the map (defensive — they have
     * no runtime counterpart to key on).
     * <p>
     * Polluted data may bridge two view rows to one runtime id; the newest
     * (largest) row id then wins — {@code ORDER BY id DESC} makes that
     * deterministic regardless of the engine's scan order, aligned with
     * {@code AssetRepository.recapturedAssetViewRowId} — and every duplicate
     * is logged so the pollution surfaces instead of passing silently.
     */
    public Map<Integer, Long> findRowIdsByRuntimeId() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, kind, device_id, service_id, probe_id, control_id FROM t_asset "
                        + "ORDER BY id DESC");
        Map<Integer, Long> rowIdsByRuntimeId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            AssetKind kind = AssetKind.fromCode(((Number) row.get("kind")).intValue());
            int runtimeId  = firstRuntimeIdOfRow(row);
            if (kind == null || runtimeId <= 0) {
                continue;
            }
            Long previous = rowIdsByRuntimeId.putIfAbsent(runtimeId, ((Number) row.get("id")).longValue());
            if (previous != null) {
                log.warn("t_asset carries duplicate view rows for runtime id {}; "
                                + "keeping the newest row id {} and ignoring the older {}.",
                        runtimeId, previous, ((Number) row.get("id")).longValue());
            }
        }
        return rowIdsByRuntimeId;
    }

    /** First non-null per-kind id column of a query-for-list row, or 0 when none is set. */
    private static int firstRuntimeIdOfRow(Map<String, Object> row) {
        for (String column : RUNTIME_ID_COLUMNS) {
            Object id = row.get(column);
            if (id != null) {
                return ((Number) id).intValue();
            }
        }
        return 0;
    }
}
