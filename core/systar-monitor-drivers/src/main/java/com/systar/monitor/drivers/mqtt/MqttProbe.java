package com.systar.monitor.drivers.mqtt;

import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT probe that receives data from MQTT topics.
 * <p>
 * Source format: {@code topic} or {@code topic:$.jsonPath}.
 * <ul>
 *   <li>{@code sensor/temp} — raw payload value</li>
 *   <li>{@code sensor/data:$.temperature} — extract {@code "temperature"} from JSON payload</li>
 * </ul>
 * The probe caches the last received value and returns it on {@link #detect(IMonitorResult)}.
 */
public class MqttProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(MqttProbe.class);

    private String topic;
    private String jsonPath;
    private volatile Object cachedValue;

    public MqttProbe() {
    }

    @Override
    public void init(ProbeType type, int id, String name) {
        super.init(type, id, name);
        if (type != null && type.getSource() != null) {
            parseSource(type.getSource());
        }
    }

    // ======================== source parsing ========================

    private void parseSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("MQTT source must not be empty");
        }
        String trimmed = source.trim();
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx >= 0) {
            this.topic = trimmed.substring(0, colonIdx).trim();
            this.jsonPath = trimmed.substring(colonIdx + 1).trim();
            if (this.jsonPath.isEmpty()) {
                throw new IllegalArgumentException(
                        "MQTT source jsonPath must not be empty, got: " + source);
            }
        } else {
            this.topic = trimmed;
            this.jsonPath = null;
        }
        if (this.topic.isEmpty()) {
            throw new IllegalArgumentException("MQTT source topic must not be empty");
        }
    }

    // ======================== detection (passive) ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        result.setValue(cachedValue);
        result.setSampleTime(System.currentTimeMillis());
    }

    // ======================== message handling ========================

    /**
     * Called by {@link MqttService} when a message arrives on this probe's topic.
     * Extracts the value according to the configured {@code jsonPath} and updates
     * the cached value.
     *
     * @param payload the raw MQTT message payload (UTF-8 string)
     */
    void onMessage(String payload) {
        if (payload == null) {
            return;
        }
        try {
            Object value;
            if (jsonPath != null) {
                value = extractJsonPath(payload.trim());
            } else {
                value = parseRaw(payload.trim());
            }
            cachedValue = value;
        } catch (Exception e) {
            LOG.warn("Failed to parse MQTT message for probe {} on topic {}: {}",
                    getId(), topic, e.getMessage());
        }
    }

    private Object extractJsonPath(String payload) {
        JSONObject json = new JSONObject(payload);
        // Support $.key1.key2 syntax
        String path = jsonPath;
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if (path.startsWith("$")) {
            path = path.substring(1);
        }
        String[] keys = path.split("\\.");
        Object current = json;
        for (int i = 0; i < keys.length && current instanceof JSONObject; i++) {
            String key = keys[i].trim();
            if (key.isEmpty()) continue;
            current = ((JSONObject) current).opt(key);
        }
        if (current instanceof Number num) {
            return num.floatValue();
        }
        return current;
    }

    private Object parseRaw(String payload) {
        // Try as number first
        try {
            if (payload.contains(".")) {
                return Float.parseFloat(payload);
            }
            return Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            // Treat as string
        }
        // Treat "true"/"false" as boolean
        if ("true".equalsIgnoreCase(payload)) return true;
        if ("false".equalsIgnoreCase(payload)) return false;
        return payload;
    }

    // ======================== IPassiveMonitor ========================

    @Override
    public String makeRegisterKey() {
        return topic;
    }

    // ======================== getters / setters ========================

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }

    public Object getCachedValue() { return cachedValue; }
}
