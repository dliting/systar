package com.systar.server.dto;

import java.util.List;

/**
 * Body of {@code PUT /group-trees/{treeId}/groups/order}: the parent whose
 * children are reordered ({@code null} = top level) and the COMPLETE ordered
 * child id list — the atomic replacement of that parent's sibling order.
 */
public record GroupReorderRequest(Long parent, List<Long> orderedGroupIds) {
}
