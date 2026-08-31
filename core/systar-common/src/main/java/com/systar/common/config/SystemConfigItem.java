package com.systar.common.config;

import lombok.Data;

/**
 * A single system configuration entry.
 */
@Data
public class SystemConfigItem {

    /** Unique identifier. */
    private int id;

    /** Configuration key. */
    private String key;

    /** Configuration value stored as string. */
    private String value;

    /** Optional description / memo. */
    private String description;
}
