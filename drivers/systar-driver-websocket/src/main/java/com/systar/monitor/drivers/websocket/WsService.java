package com.systar.monitor.drivers.websocket;

import com.systar.monitor.asset.PassiveService;
import com.systar.monitor.result.MonitorResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Passive service for WebSocket-based data ingestion.
 * Connects to an external WebSocket server, parses JSON messages,
 * and routes data to registered WsProbe instances.
 */
public class WsService extends PassiveService {

    private static final Logger LOG = LoggerFactory.getLogger(WsService.class);

    private static final int DEFAULT_RECONNECT_INTERVAL_SECS = 5;
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 100;
    private static final int MAX_LOGGED_PAYLOAD_CHARS = 200;

    /** Shared, thread-safe JSON parser for incoming messages. */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private String url;
    private int reconnectInterval = DEFAULT_RECONNECT_INTERVAL_SECS;
    private String messageFormat = "json";
    private int maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS;

    private WebSocketClient client;
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private ScheduledExecutorService reconnectExecutor;

    public WsService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        if (url == null || url.isBlank()) {
            LOG.warn("WsService url not configured — service will not start");
            return;
        }
        reconnectCount.set(0);
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-reconnect");
            t.setDaemon(true);
            return t;
        });
        connect();
        LOG.info("WsService started, connecting to {}", url);
    }

    @Override
    public void stop() {
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor = null;
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.warn("Error closing WebSocket client", e);
            }
            client = null;
        }
        LOG.info("WsService stopped");
    }

    private void connect() throws Exception {
        client = new WebSocketClient(URI.create(url)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                reconnectCount.set(0);
                LOG.info("WebSocket connected to {}", url);
            }

            @Override
            public void onMessage(String message) {
                parseAndRoute(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                LOG.info("WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
                scheduleReconnect();
            }

            @Override
            public void onError(Exception ex) {
                LOG.warn("WebSocket error: {}", ex.getMessage());
            }
        };
        client.connect();
    }

    private void scheduleReconnect() {
        int attempts = reconnectCount.incrementAndGet();
        if (attempts > maxReconnectAttempts) {
            LOG.error("Max reconnect attempts ({}) reached, giving up", maxReconnectAttempts);
            return;
        }
        LOG.info("Scheduling reconnect attempt {} of {} in {}s",
                attempts, maxReconnectAttempts, reconnectInterval);

        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) return;

        reconnectExecutor.schedule(() -> {
            try {
                connect();
            } catch (Exception e) {
                LOG.warn("Reconnect failed", e);
            }
        }, reconnectInterval, TimeUnit.SECONDS);
    }

    // ======================== message routing ========================

    void parseAndRoute(String message) {
        var dispatcher = getResultDispatcher();
        if (dispatcher == null) return;

        try {
            JsonNode json = JSON_MAPPER.readTree(message);
            if (!json.isObject()) {
                LOG.warn("Ignoring non-object WebSocket payload: {}", abbreviate(message));
                return;
            }
            json.fields().forEachRemaining(entry -> {
                var monitor = getMonitor(entry.getKey());
                if (monitor == null) return;
                dispatcher.dispatch(new MonitorResult(monitor, toValue(entry.getValue())));
            });
        } catch (Exception e) {
            LOG.warn("Failed to parse WebSocket message", e);
        }
    }

    /** Caps a payload snippet for logging so oversized frames cannot flood the log. */
    private static String abbreviate(String payload) {
        return payload.length() <= MAX_LOGGED_PAYLOAD_CHARS
                ? payload
                : payload.substring(0, MAX_LOGGED_PAYLOAD_CHARS) + "...";
    }

    /**
     * Converts a JSON node to the value type dispatched to monitors:
     * numbers as float, booleans and strings as-is, null/missing as null,
     * anything else (objects, arrays) as the raw node.
     */
    private static Object toValue(JsonNode node) {
        if (node.isNumber()) {
            return (float) node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node;
    }

    // ======================== getters / setters ========================

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getReconnectInterval() { return reconnectInterval; }
    public void setReconnectInterval(int reconnectInterval) { this.reconnectInterval = reconnectInterval; }

    public String getMessageFormat() { return messageFormat; }
    public void setMessageFormat(String messageFormat) { this.messageFormat = messageFormat; }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }
}
