package com.systar.monitor.drivers.weather;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Active service for weather station data acquisition via HTTP API.
 * <p>
 * Manages a pool of {@link WeatherConnection} instances used by
 * {@link WeatherProbe} instances to fetch meteorological data.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>{@code url} -- weather API endpoint URL</li>
 *   <li>{@code stationCode} -- weather station identifier</li>
 *   <li>{@code username} -- API authentication username (optional)</li>
 *   <li>{@code password} -- API authentication password (optional)</li>
 *   <li>{@code apiKey} -- API key (alternative auth)</li>
 *   <li>{@code refreshInterval} -- data refresh interval in seconds (default: 300)</li>
 * </ul>
 */
public class WeatherService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherService.class);

    private static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 300;
    private static final int HTTP_TIMEOUT_SECONDS = 30;

    private String url;
    private String stationCode;
    private String username;
    private String password;
    private String apiKey;
    private int refreshInterval = DEFAULT_REFRESH_INTERVAL_SECONDS;

    private volatile String cachedJsonData;
    private volatile long lastFetchTime;

    private HttpClient httpClient;

    public WeatherService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        if (url == null || url.isBlank()) {
            LOG.warn("WeatherService url not configured — service will not start");
            return;
        }
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();
        LOG.info("WeatherService started for station '{}'", stationCode);
    }

    @Override
    public void stop() {
        httpClient = null;
        LOG.info("WeatherService stopped");
    }

    // ======================== connection factory ========================

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new WeatherConnection(this);
    }

    // ======================== cache management ========================

    public String getCachedJsonData() {
        return cachedJsonData;
    }

    public void setCachedJsonData(String data) {
        this.cachedJsonData = data;
        this.lastFetchTime = System.currentTimeMillis();
    }

    public long getLastFetchTime() {
        return lastFetchTime;
    }

    public boolean isCacheExpired() {
        return (System.currentTimeMillis() - lastFetchTime) > refreshInterval * 1000L;
    }

    // ======================== getters / setters ========================

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(int refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    // ======================== inner connection class ========================

    /**
     * HTTP connection for fetching weather data from a REST API.
     * Stateless (HTTP); open/close are no-ops. The real work is in fetchData().
     */
    public static class WeatherConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(WeatherConnection.class);

        private final WeatherService service;
        private volatile boolean connected;

        public WeatherConnection(WeatherService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            connected = true;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }

        /**
         * Fetches weather data from the API and updates the service cache.
         */
        public void fetchData() throws Exception {
            String queryUrl = buildQueryUrl();
            CONN_LOG.debug("Fetching weather data from: {}", queryUrl);

            HttpClient client = service.httpClient;
            if (client == null) {
                client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                        .build();
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET();

            if (service.getApiKey() != null && !service.getApiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + service.getApiKey());
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Weather API returned HTTP " + response.statusCode());
            }

            service.setCachedJsonData(response.body());
            CONN_LOG.debug("Weather data fetched successfully, {} bytes", response.body().length());
        }

        private String buildQueryUrl() {
            StringBuilder sb = new StringBuilder(service.url);
            sb.append("?StationCode=").append(encode(service.stationCode));

            if (service.username != null && !service.username.isBlank()) {
                sb.append("&UserName=").append(encode(service.username));
            }
            if (service.password != null && !service.password.isBlank()) {
                sb.append("&Password=").append(encode(service.password));
            }
            return sb.toString();
        }

        private static String encode(String value) {
            if (value == null) return "";
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
