package com.systar.monitor.drivers.weather;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.weather.WeatherService.WeatherConnection;
import com.systar.monitor.result.IMonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Weather station probe that reads meteorological data from an HTTP API.
 * <p>
 * Source format: attribute name, e.g. {@code temperature}, {@code humidity},
 * {@code wind_speed}, {@code pressure}, {@code rainfall}.
 */
public class WeatherProbe extends Probe {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherProbe.class);

    private String attribute;

    public WeatherProbe() {
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
            throw new IllegalArgumentException("Weather source (attribute name) must not be empty");
        }
        this.attribute = source.trim().toLowerCase();
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        ProbeType type = getType();
        String source = type != null ? type.getSource() : null;
        if (attribute == null) {
            parseSource(source);
        }

        MonitorService svc = getSource();
        if (!(svc instanceof WeatherService weatherService)) {
            throw new IllegalStateException("WeatherProbe must belong to a WeatherService");
        }

        synchronized (weatherService) {
            if (weatherService.isCacheExpired()) {
                WeatherConnection conn = null;
                try {
                    conn = (WeatherConnection) weatherService.getConnection();
                    conn.fetchData();
                } catch (Exception e) {
                    LOG.warn("Weather data fetch failed for station '{}'", weatherService.getStationCode(), e);
                    result.setError("Weather fetch failed: " + e.getMessage());
                    if (conn != null) { conn.close(); }
                    return;
                } finally {
                    weatherService.releaseConnection(conn);
                }
            }
        }

        String json = weatherService.getCachedJsonData();
        float value = WeatherJsonParser.parseAttribute(json, attribute);
        result.setValue(value);
        result.setSampleTime(System.currentTimeMillis());
    }

    // ======================== getters / setters ========================

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }
}
