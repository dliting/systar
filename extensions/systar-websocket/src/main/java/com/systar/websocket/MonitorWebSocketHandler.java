package com.systar.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systar.monitor.asset.AssetState;
import com.systar.monitor.result.MonitorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket handler that maintains client connections and broadcasts
 * {@link MonitorResult} data in real time.
 * <p>
 * Thread-safe: connections are stored in a {@link CopyOnWriteArrayList}
 * and per-session subscriptions in a {@link ConcurrentHashMap}.
 */
@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** All active WebSocket sessions. */
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    /** Per-session subscribed monitor ids (empty set = receive all). */
    private final ConcurrentHashMap<String, List<Integer>> subscriptions = new ConcurrentHashMap<>();

    // ======================== connection lifecycle ========================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        subscriptions.remove(session.getId());
        log.info("WebSocket disconnected: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    // ======================== client messages ========================

    /**
     * Handles incoming text messages from clients.
     * <p>
     * Expected JSON format for subscription:
     * <pre>{@code {"action":"subscribe","monitorIds":[1,2,3]}}</pre>
     * When a client sends a subscription message, only results for the
     * specified monitor ids will be pushed to that client.
     * If no subscription is active, all results are broadcast.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from {}: {}", session.getId(), payload);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String action = (String) msg.get("action");

            if ("subscribe".equals(action)) {
                @SuppressWarnings("unchecked")
                List<Integer> monitorIds = (List<Integer>) msg.get("monitorIds");
                if (monitorIds != null) {
                    subscriptions.put(session.getId(), monitorIds);
                    log.info("Session {} subscribed to monitors: {}", session.getId(), monitorIds);
                }
            } else if ("unsubscribe".equals(action)) {
                subscriptions.remove(session.getId());
                log.info("Session {} unsubscribed", session.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to parse client message from {}: {}", session.getId(), e.getMessage());
        }
    }

    // ======================== broadcast ========================

    /**
     * Broadcasts a raw text message to all connected sessions.
     */
    public void broadcast(String message) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.warn("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Serialises a {@link MonitorResult} to JSON and broadcasts it
     * to all connected (and subscribed) sessions.
     * <p>
     * JSON format:
     * <pre>{@code {
     *   "type": "monitorResult",
     *   "monitorId": 1,
     *   "value": 23.5,
     *   "status": "NORMAL",
     *   "sampleTime": 1700000000000
     * }}</pre>
     *
     * @param result the monitor result to push
     */
    public void broadcastMonitorResult(MonitorResult result) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            int      monitorId  = result.getMonitor().getId();
            Object   value      = result.getValue();
            AssetState status   = result.getStatus();
            long     sampleTime = result.getSampleTime();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type",      "monitorResult");
            payload.put("monitorId", monitorId);
            payload.put("value",      value);
            payload.put("status",     status != null ? status.name() : AssetState.NORMAL.name());
            payload.put("sampleTime", sampleTime);

            String json = objectMapper.writeValueAsString(payload);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    continue;
                }

                // Check subscription filter
                List<Integer> subscribed = subscriptions.get(session.getId());
                if (subscribed != null && !subscribed.contains(monitorId)) {
                    continue;
                }

                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("Failed to push monitor result to session {}: {}",
                            session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialise MonitorResult for broadcast: {}", e.getMessage(), e);
        }
    }
}
