package com.systar.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systar.data.event.AlarmPersistedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges {@link AlarmPersistedEvent} from the data layer to
 * the WebSocket layer.
 * <p>
 * Listens for {@link AlarmPersistedEvent} published after alarm
 * persistence and forwards the alarm identifiers to the
 * {@link MonitorWebSocketHandler} for real-time broadcast to
 * connected WebSocket clients.
 */
@Component
public class AlarmPusher {

    private static final Logger log = LoggerFactory.getLogger(AlarmPusher.class);

    private static final String TYPE_ALARM = "alarm";

    private final MonitorWebSocketHandler handler;
    private final ObjectMapper            objectMapper = new ObjectMapper();

    public AlarmPusher(MonitorWebSocketHandler handler) {
        this.handler = handler;
    }

    /**
     * Receives an alarm persisted event and pushes the alarm data
     * to all connected WebSocket clients.
     *
     * @param event the Spring application event carrying alarm identifiers
     */
    @EventListener
    public void onAlarmPersisted(AlarmPersistedEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type",           TYPE_ALARM);
            payload.put("alarmMessageId", event.getAlarmMessageId());
            payload.put("eventRankId",    event.getEventRankId());
            payload.put("assetId",        event.getAssetId());

            // Alarms are broadcast to all sessions — no subscription filtering
            // (unlike monitor results which support per-session monitor ID filters)
            String json = objectMapper.writeValueAsString(payload);
            handler.broadcast(json);
        } catch (Exception e) {
            log.error("Failed to push alarm via WebSocket: {}", e.getMessage(), e);
        }
    }
}
