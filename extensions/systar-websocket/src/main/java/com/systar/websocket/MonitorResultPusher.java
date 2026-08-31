package com.systar.websocket;

import com.systar.monitor.result.MonitorResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bridges {@link MonitorResultEvent} from the monitoring core to
 * the WebSocket layer.
 * <p>
 * Listens for {@link MonitorResultEvent} published by the result
 * dispatcher and forwards each result to the {@link MonitorWebSocketHandler}
 * for real-time broadcast to connected WebSocket clients.
 */
@Component
public class MonitorResultPusher {

    private static final Logger log = LoggerFactory.getLogger(MonitorResultPusher.class);

    private final MonitorWebSocketHandler handler;

    public MonitorResultPusher(MonitorWebSocketHandler handler) {
        this.handler = handler;
    }

    /**
     * Receives a monitor result event and pushes it to all
     * connected WebSocket clients.
     *
     * @param event the Spring application event carrying the result
     */
    @EventListener
    public void onMonitorResult(MonitorResultEvent event) {
        try {
            handler.broadcastMonitorResult(event.getResult());
        } catch (Exception e) {
            log.error("Failed to push monitor result via WebSocket: {}", e.getMessage(), e);
        }
    }
}
