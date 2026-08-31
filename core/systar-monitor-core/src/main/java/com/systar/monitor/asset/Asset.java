package com.systar.monitor.asset;

import com.systar.monitor.asset.type.AssetType;
import com.systar.monitor.asset.type.AssetTypeProperty;
import com.systar.monitor.asset.type.DataType;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all assets (spaces, devices, probes, controls, services).
 * <p>
 * An asset is the fundamental unit in the monitoring model. It carries identity,
 * state, type metadata, and participates in a parent-child hierarchy. State changes
 * bubble up to parent assets so that compound assets (spaces/devices) always reflect
 * the most severe state among their children.
 *
 * @param <T> the concrete {@link AssetType} of this asset
 */
@Getter
public abstract class Asset<T extends AssetType> {

    private static final Logger BIND_LOG = LoggerFactory.getLogger(Asset.class);

    public static final int INVALID_ID = 0;

    // ---- identity fields ----
    private int id;
    private String name;
    private String caption;

    // ---- type ----
    private T type;

    // ---- state (custom setter with bubbling) ----
    private volatile AssetState state;

    // ---- hierarchy ----
    private Asset<?> parent;
    private int parentId;

    // ---- flags ----
    private boolean enabled;
    private boolean removed;

    // ---- context ----
    private AssetContext context;

    // ---- runtime metadata ----
    private double healthIndex;
    private Map<String, Object> metadata;

    protected Asset() {
        this.state = AssetState.NORMAL;
        this.parentId = INVALID_ID;
        this.enabled = true;
        this.removed = false;
        this.metadata = new ConcurrentHashMap<>();
    }

    // ======================== abstract methods ========================

    /** Returns the asset kind determined by the concrete class. */
    public abstract AssetKind getKind();

    /** Whether this asset is a compound (container) asset. */
    public abstract boolean isCompound();

    /** Whether this asset is a monitor (leaf) asset. */
    public abstract boolean isMonitor();

    /** Accepts a visitor for traversal / processing. */
    public abstract <R> R accept(AssetVisitor<R> visitor);

    // ======================== initialisation ========================

    /**
     * Initialises the asset with the required fields.
     * Must be called immediately after construction.
     *
     * @param type the asset type
     * @param id   unique identifier
     * @param name unique name
     * @throws AssetException if any argument is invalid
     */
    public void init(T type, int id, String name) {
        if (type == null) {
            throw new AssetException("Asset type must not be null.");
        }
        if (id == INVALID_ID) {
            throw new AssetException("Asset id must be a positive non-zero value.");
        }
        if (name == null || name.isBlank()) {
            throw new AssetException("Asset name must not be blank.");
        }
        this.type = type;
        this.id = id;
        this.name = name;
        if (this.caption == null) {
            this.caption = type.getCaption();
        }
    }

    // ======================== state management ========================

    /**
     * Sets the asset state and bubbles up to the parent if the new state is
     * more severe, ensuring compound assets always reflect the worst child state.
     */
    public synchronized void setState(AssetState newState) {
        if (newState == null || this.state == newState) {
            return;
        }
        AssetState oldState = this.state;
        this.state = newState;

        // Notify listeners via context
        if (context != null) {
            context.notifyStateChange(this, oldState, newState);
        }

        // Bubble up to parent
        if (parent != null) {
            if (newState.isMoreSevereThan(parent.getState())) {
                parent.setState(newState);
            } else if (parent.getState().isMoreSevereThan(newState)) {
                parent.recomputeStateFromChildren();
            }
        }
    }

    /**
     * Called on a compound asset to recalculate its state from its children.
     * Default implementation is a no-op; subclasses (CompoundAsset) will override.
     */
    protected void recomputeStateFromChildren() {
        // no-op by default; compound assets override
    }

    // ======================== hierarchy ========================

    /**
     * Sets the parent asset. Intended for framework use only.
     */
    public void setParent(Asset<?> parent) {
        this.parent = parent;
    }

    // ======================== metadata helpers ========================

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <V> V getMetadata(String key) {
        return (V) metadata.get(key);
    }

    // ======================== reflective property binding ========================

    /**
     * Reflectively binds metadata values to typed setter methods on the concrete
     * subclass, based on the type's PropertyList definitions.
     * <p>
     * This bridges the gap between generic KV metadata and typed driver fields
     * (e.g., BacnetControl.objectType, OpcUaControl.nodeIdStr). Only iterates
     * properties declared in {@code type.getProperties()} — type-level fields like
     * {@code dataType} on {@link com.systar.monitor.asset.type.MonitorType} are not
     * affected and remain driven by their XML element.
     * <p>
     * Setter matching is case-insensitive on the property name; conversion uses
     * {@link com.systar.monitor.asset.type.DataType#toJvmValue(Object)}.
     * Failures are logged as warnings and do not prevent the asset from loading.
     */
    public void bindProperties() {
        AssetType type = getType();
        if (type == null || type.getProperties() == null) return;

        for (AssetTypeProperty prop : type.getProperties()) {
            Object rawValue = getMetadata(prop.getName());
            if (rawValue == null) continue;

            try {
                Method setter = findSetter(getClass(), prop.getName(), prop.getDataType());
                if (setter == null) {
                    BIND_LOG.debug("No setter for property '{}' on {} — skipping.",
                            prop.getName(), getClass().getSimpleName());
                    continue;
                }
                Class<?> paramType  = setter.getParameterTypes()[0];
                Object    converted = convertValue(rawValue, paramType, prop.getDataType());
                if (converted != null) {
                    setter.invoke(this, converted);
                }
            } catch (Exception e) {
                BIND_LOG.warn("Failed to bind '{}' on {}: {}",
                        prop.getName(), getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Locates a single-arg public setter for the given property name (case-insensitive).
     * <p>
     * Walks the class hierarchy from concrete subclass up to {@link Asset}. When multiple
     * overloads match the name (e.g. {@code setAddress(int)} vs {@code setAddress(String)}),
     * the one whose parameter type matches {@code preferredDataType.jvmType()} wins;
     * otherwise the first match found is returned.
     * <p>
     * Properties with the same name as an inherited {@link Asset} setter (e.g. {@code name},
     * {@code caption}) will resolve to that inherited setter — usually the desired behavior.
     */
    private static Method findSetter(Class<?> clazz, String propName, DataType preferredDataType) {
        String expectedSetter = "set" + propName;
        Class<?> preferredJvm = preferredDataType != null
                ? toBoxed(preferredDataType.jvmType()) : null;
        Method firstMatch = null;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (!m.getName().equalsIgnoreCase(expectedSetter)
                        || m.getParameterCount() != 1
                        || !Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                if (firstMatch == null) firstMatch = m;
                if (preferredJvm != null
                        && toBoxed(m.getParameterTypes()[0]).equals(preferredJvm)) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return firstMatch;
    }

    /** Returns the boxed equivalent of a primitive class, or the class itself if non-primitive. */
    private static Class<?> toBoxed(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class)     return Integer.class;
        if (c == long.class)    return Long.class;
        if (c == float.class)   return Float.class;
        if (c == double.class)  return Double.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class)    return Byte.class;
        if (c == short.class)   return Short.class;
        if (c == char.class)    return Character.class;
        return c;
    }

    private static Object convertValue(Object rawValue, Class<?> targetParamType,
                                        DataType propDataType) {
        if (rawValue == null) return null;
        if (targetParamType.isAssignableFrom(rawValue.getClass())) return rawValue;
        // Prefer conversion driven by the setter's actual parameter type — guards
        // against XML/setter type mismatches (e.g. property declared STRING but
        // setter takes int). Fall back to property DataType if no match.
        DataType byParam = DataType.forJvmType(targetParamType);
        if (byParam != null) return byParam.toJvmValue(rawValue);
        if (propDataType != null) return propDataType.toJvmValue(rawValue);
        return rawValue.toString();
    }

    // ======================== setters for basic fields ========================

    public void setName(String name) {
        this.name = name;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setType(T type) {
        this.type = type;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public void setContext(AssetContext context) {
        this.context = context;
    }

    public void setHealthIndex(double healthIndex) {
        this.healthIndex = healthIndex;
    }

    // ======================== Object ========================

    @Override
    public String toString() {
        return getKind() + "(" + id + ") " + name;
    }
}
