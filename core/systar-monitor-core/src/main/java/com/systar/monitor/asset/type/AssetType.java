package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata definition for a category of assets.
 * <p>
 * Each asset type describes the structure and behavior of a family of assets,
 * including what kind they are (SPACE, DEVICE, PROBE, etc.) and what properties
 * they carry.
 * <p>
 * Properties are stored in a {@link LinkedHashMap} keyed by property name,
 * preserving insertion order while supporting O(1) lookup by name.
 * This is critical for type inheritance (child overriding parent property)
 * and for runtime property resolution on asset instances.
 */
@Data
public class AssetType {

    /** Unique type name. */
    private String name;

    /** Human-readable caption. */
    private String caption;

    /** The kind of assets this type produces. */
    private AssetKind kind;

    /** Fully-qualified class name of the implementation class (optional). */
    private String relatedClass;

    /** Property definitions keyed by name, in insertion order. */
    private Map<String, AssetTypeProperty> properties = new LinkedHashMap<>();

    /** Super type this type inherits from (optional, used for type inheritance). */
    private AssetType superType;

    /** Whether this type is abstract (cannot be instantiated directly). */
    private boolean abstractType;

    public boolean isAbstractType() { return abstractType; }

    public AssetType() {
    }

    public AssetType(String name, AssetKind kind) {
        this.name = name;
        this.kind = kind;
    }

    /** Whether assets of this type are compound (containers). */
    public boolean isCompound() {
        return kind != null && kind.isCompound();
    }

    /** Whether assets of this type are monitors (leaf nodes). */
    public boolean isMonitor() {
        return kind != null && kind.isMonitor();
    }

    /**
     * Infers the primary data type from the first property, or null if no properties.
     * Useful for probe/control types that have a primary measurement.
     */
    public DataType getDataType() {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        return properties.values().iterator().next().getDataType();
    }

    /** Adds a property definition, replacing any existing property with the same name. */
    public void addProperty(AssetTypeProperty prop) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.put(prop.getName(), prop);
    }

    /** Finds a property by name. */
    public AssetTypeProperty findProperty(String name) {
        if (properties == null || name == null) return null;
        return properties.get(name);
    }

    /** Returns all properties in insertion order. */
    public Collection<AssetTypeProperty> getProperties() {
        return properties != null ? properties.values() : null;
    }
}
