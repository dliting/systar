package com.systar.monitor.asset.type;

import lombok.Data;

/**
 * Describes a property definition within an {@link AssetType}.
 */
@Data
public class AssetTypeProperty {

    /** Property name (unique within an asset type). */
    private String name;

    /** Data type of this property. */
    private DataType dataType;

    /** Default value as a string representation. */
    private String defaultValue;

    /** Human-readable description. */
    private String description;

    /** Whether this property is required. */
    private boolean required;

    /** Minimum value for numeric types. */
    private Double min;

    /** Maximum value for numeric types. */
    private Double max;

    /** Maximum string length for STRING type. */
    private Integer maxLength;

    /** UI presentation type for this property. */
    private ViewType viewType;

    public AssetTypeProperty() {
    }

    public AssetTypeProperty(String name, DataType dataType, String defaultValue, String description) {
        this.name        = name;
        this.dataType    = dataType;
        this.defaultValue = defaultValue;
        this.description = description;
        this.viewType    = ViewType.infer(dataType);
    }

    public AssetTypeProperty(String name, DataType dataType, String defaultValue,
                             String description, Double min, Double max, Integer maxLength) {
        this.name        = name;
        this.dataType    = dataType;
        this.defaultValue = defaultValue;
        this.description = description;
        this.required    = false;
        this.min         = min;
        this.max         = max;
        this.maxLength   = maxLength;
        this.viewType    = ViewType.infer(dataType);
    }

    /** Copy constructor preserving all fields including required and constraints. */
    public AssetTypeProperty(AssetTypeProperty other) {
        this.name        = other.name;
        this.dataType    = other.dataType;
        this.defaultValue = other.defaultValue;
        this.description = other.description;
        this.required    = other.required;
        this.min         = other.min;
        this.max         = other.max;
        this.maxLength   = other.maxLength;
        this.viewType    = other.viewType;
    }
}
