package com.systar.monitor.asset.type;

import com.systar.common.util.TimeSpan;

/**
 * Data types supported by asset type properties.
 * <p>
 * Each constant provides type conversion methods for reflective property binding:
 * {@link #parseValue(String)} converts string representation to typed values,
 * {@link #jvmType()} returns the corresponding Java class.
 */
public enum DataType {

    INT,
    FLOAT,
    BOOLEAN,
    STRING,
    TIMESPAN;

    /**
     * Parses a string value into the JVM type corresponding to this DataType.
     *
     * @param text the string representation
     * @return the parsed value, or null if input is null/blank
     * @throws NumberFormatException for INT/FLOAT if the value is unparseable
     */
    public Object parseValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        return switch (this) {
            case INT      -> Integer.parseInt(trimmed);
            case FLOAT    -> Float.parseFloat(trimmed);
            case BOOLEAN  -> Boolean.parseBoolean(trimmed);
            case STRING   -> trimmed;
            case TIMESPAN -> TimeSpan.parse(trimmed);
        };
    }

    /**
     * Returns the JVM Class that corresponds to this DataType.
     */
    public Class<?> jvmType() {
        return switch (this) {
            case INT      -> int.class;
            case FLOAT    -> float.class;
            case BOOLEAN  -> boolean.class;
            case STRING   -> String.class;
            case TIMESPAN -> TimeSpan.class;
        };
    }

    /**
     * Converts an arbitrary value to the appropriate JVM type for this DataType.
     * Handles boxed primitives and string-based conversion.
     *
     * @param value the value to convert (may be String, Number, Boolean, etc.)
     * @return the converted value, or null if input is null
     */
    public Object toJvmValue(Object value) {
        if (value == null) {
            return null;
        }
        if (jvmType().isInstance(value)) {
            return value;
        }
        if (value instanceof Number num) {
            return switch (this) {
                case INT   -> num.intValue();
                case FLOAT -> num.floatValue();
                default    -> parseValue(value.toString());
            };
        }
        return parseValue(value.toString());
    }

    /**
     * Returns the DataType whose {@link #jvmType()} matches the given Java class
     * (treating primitives and their boxed equivalents as equal), or {@code null}
     * if no DataType maps to the given class.
     * <p>
     * Used by reflective binding to drive conversion from the setter's actual
     * parameter type when it diverges from the property-declared DataType.
     */
    public static DataType forJvmType(Class<?> javaType) {
        if (javaType == null) return null;
        Class<?> boxed = boxed(javaType);
        if (boxed == Integer.class) return INT;
        if (boxed == Float.class)   return FLOAT;
        if (boxed == Boolean.class) return BOOLEAN;
        if (boxed == String.class)  return STRING;
        if (TimeSpan.class.isAssignableFrom(boxed)) return TIMESPAN;
        return null;
    }

    private static Class<?> boxed(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class)     return Integer.class;
        if (c == float.class)   return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class)  return Double.class;
        if (c == long.class)    return Long.class;
        return c;
    }
}
