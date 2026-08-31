package com.systar.common.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * A code catalog groups related {@link CodeItem} instances under a shared catalog ID.
 */
@Data
public class CodeCatalog {

    /** Catalog identifier. */
    private int id;

    /** Catalog name. */
    private String name;

    /** Items in this catalog. */
    private List<CodeItem> items = new ArrayList<>();

    public CodeCatalog() {
    }

    public CodeCatalog(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addItem(CodeItem item) {
        items.add(item);
    }

    /**
     * Look up an item by its ID.
     *
     * @param itemId item ID
     * @return the matching item, or null if not found
     */
    public CodeItem getItem(int itemId) {
        return items.stream()
                .filter(item -> item.getId() == itemId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Return all items whose parentId equals the given value.
     *
     * @param parentId parent item ID (use null for root-level items)
     * @return list of matching items, never null
     */
    public List<CodeItem> getItemsByParentId(Integer parentId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getParentId(), parentId))
                .collect(Collectors.toList());
    }
}
