package com.systar.server.dto;

/**
 * Read-only view of an {@link com.systar.monitor.asset.type.AssetTypeProperty}
 * for frontend consumption.
 */
public record TypePropertyVO(
        String name,
        String description,
        String dataType,
        String viewType,
        String defaultValue,
        boolean required,
        Double min,
        Double max,
        Integer maxLength
) {}
