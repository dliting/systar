package com.systar.common.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory system configuration manager.
 *
 * <p>Stores key-value pairs loaded from an external source (database, file, etc.).
 * Pure Java implementation with no framework dependencies.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}.
 */
public class SystemConfigManager {

    private final ConcurrentHashMap<String, String> configMap = new ConcurrentHashMap<>();

    /**
     * Initialize (or replace) the configuration data.
     *
     * @param dataMap key-value pairs to load
     */
    public void loadConfigs(Map<String, String> dataMap) {
        configMap.clear();
        if (dataMap != null) {
            configMap.putAll(dataMap);
        }
    }

    /**
     * Get the raw string value for a key.
     *
     * @param key configuration key
     * @return value, or null if not found
     */
    public String getValue(String key) {
        return configMap.get(key);
    }

    /**
     * Get the string value for a key, with a fallback default.
     *
     * @param key          configuration key
     * @param defaultValue fallback value when key is absent
     * @return value or default
     */
    public String getValue(String key, String defaultValue) {
        return configMap.getOrDefault(key, defaultValue);
    }

    /**
     * Get an integer configuration value.
     *
     * @param key          configuration key
     * @param defaultValue fallback when key is absent or value is not a valid integer
     * @return parsed integer or default
     */
    public int getIntValue(String key, int defaultValue) {
        String value = configMap.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a double configuration value.
     *
     * @param key          configuration key
     * @param defaultValue fallback when key is absent or value is not a valid double
     * @return parsed double or default
     */
    public double getDoubleValue(String key, double defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a long configuration value.
     *
     * @param key          configuration key
     * @param defaultValue fallback when key is absent or value is not a valid long
     * @return parsed long or default
     */
    public long getLongValue(String key, long defaultValue) {
        String value = configMap.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a boolean configuration value.
     *
     * <p>Recognizes "true", "1", "yes" (case-insensitive) as {@code true};
     * everything else (including absent keys) returns the default.
     *
     * @param key          configuration key
     * @param defaultValue fallback when key is absent
     * @return parsed boolean or default
     */
    public boolean getBoolValue(String key, boolean defaultValue) {
        String value = configMap.get(key);
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim().toLowerCase();
        return "true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed);
    }

    /**
     * Check whether a configuration key exists.
     *
     * @param key configuration key
     * @return true if the key is present
     */
    public boolean containsKey(String key) {
        return configMap.containsKey(key);
    }
}
