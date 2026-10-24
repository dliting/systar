package com.systar.monitor.drivers.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses weather API JSON responses to extract individual attribute values.
 * Expected format: {@code {"Code":200,"Content":{"attribute":value}}}
 */
public final class WeatherJsonParser {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherJsonParser.class);
    private static final float PLACEHOLDER_VALUE = -999f;
    private static final int SUCCESS_CODE = 200;

    /** Shared, thread-safe JSON parser. */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private WeatherJsonParser() {
    }

    /**
     * Extracts a named attribute from the weather API JSON response.
     *
     * @param jsonData  raw JSON string from the weather API
     * @param attribute the attribute name to extract (e.g. "temperature")
     * @return the attribute value as a float
     * @throws Exception if the response is invalid, the API returned an error,
     *                   or the attribute value is a placeholder
     */
    public static float parseAttribute(String jsonData, String attribute) throws Exception {
        if (jsonData == null) {
            throw new Exception("Weather data fetch failed: response is null");
        }
        final JsonNode root;
        try {
            root = JSON_MAPPER.readTree(jsonData);
        } catch (Exception e) {
            throw new Exception(parseFailedMessage(attribute), e);
        }
        JsonNode codeNode = root.path("Code");
        if (!root.isObject() || !codeNode.isNumber()) {
            throw new Exception(parseFailedMessage(attribute));
        }
        int code = codeNode.asInt();
        if (code != SUCCESS_CODE) {
            throw new Exception("Weather API error, status code: " + code);
        }
        JsonNode value = root.path("Content").path(attribute);
        if (!value.isNumber()) {
            throw new Exception(parseFailedMessage(attribute));
        }
        float result = (float) value.asDouble();
        if (result == PLACEHOLDER_VALUE) {
            throw new Exception("Weather API returned placeholder value: " + PLACEHOLDER_VALUE);
        }
        return result;
    }

    private static String parseFailedMessage(String attribute) {
        return "Weather JSON parse failed for attribute '" + attribute + "'";
    }
}
