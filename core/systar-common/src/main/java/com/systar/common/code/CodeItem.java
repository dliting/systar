package com.systar.common.code;

import lombok.Data;

/**
 * A single code item within a code catalog.
 */
@Data
public class CodeItem {

    /** Item identifier, unique within a catalog. */
    private int id;

    /** Short programmatic name. */
    private String name;

    /** Human-readable caption / label. */
    private String caption;

    /** Parent item ID for hierarchical items, null for top-level. */
    private Integer parentId;
}
