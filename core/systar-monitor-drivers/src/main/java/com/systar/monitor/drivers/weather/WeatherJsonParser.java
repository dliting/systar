package com.systar.monitor.drivers.weather;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses weather API JSON responses to extract individual attribute values.
 * Expected format: {@code {"Code":200,"Content":{"attribute":value}}}
 */
public final class WeatherJsonParser {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherJsonParser.class);
    private static final float PLACEHOLDER_VALUE = -999f;

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
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            int code = jsonObject.getInt("Code");
            if (code != 200) {
                throw new Exception("Weather API error, status code: " + code);
            }
            JSONObject content = jsonObject.getJSONObject("Content");
            float result = (float) content.getDouble(attribute);
            if (result == PLACEHOLDER_VALUE) {
                throw new Exception("Weather API returned placeholder value: " + PLACEHOLDER_VALUE);
            }
            return result;
        } catch (Exception e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("status code") || e.getMessage().contains("placeholder") || e.getMessage().contains("response is null"))) {
                throw e;
            }
            throw new Exception("Weather JSON parse failed for attribute '" + attribute + "'", e);
        }
    }
}
