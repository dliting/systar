package com.systar.server.controller.vo;

import java.util.List;

/**
 * Unified tree node for the forest API ({@code GET /asset-tree}).
 * <p>
 * {@code key} is the composite node key ({@code GROUP:<id>} / {@code ASSET:<id>} /
 * {@code KIND:<KIND>} / {@code UNGROUPED}): group ids and asset ids come from
 * independent sequences and must not collide in the UI tree's node-key.
 * <p>
 * ASSET nodes expose two id spaces: {@code id} is the per-kind runtime id (what
 * the /assets CRUD API addresses), {@code assetRowId} is the asset's
 * {@code t_asset} row id — the group-membership id ({@code t_asset_group_rel.asset_id})
 * that mount/unmount writes must use. Synthetic nodes (kind roots, ungrouped)
 * carry a null {@code id}; GROUP nodes carry a null {@code assetRowId}; ASSET
 * nodes whose live store entry has no view row carry a null {@code assetRowId}.
 */
public record TreeNodeVO(
        String key,
        String nodeKind,
        String assetKind,
        Long id,
        Long assetRowId,
        String name,
        String caption,
        String state,
        Boolean enabled,
        List<TreeNodeVO> children) {
}
