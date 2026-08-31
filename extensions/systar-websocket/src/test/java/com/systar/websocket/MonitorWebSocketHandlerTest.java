package com.systar.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorWebSocketHandlerTest {

    private MonitorWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MonitorWebSocketHandler();
    }

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Probe createMonitor(int id, Object value) {
        Probe probe = new Probe();
        ProbeType type = new ProbeType("test");
        probe.init(type, id, "monitor-" + id);
        probe.setValue(value);
        return probe;
    }

    // ======================== connection lifecycle ========================

    @Nested
    @DisplayName("Connection lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("afterConnectionEstablished adds session")
        void connectionEstablished() throws Exception {
            WebSocketSession session = mockSession("sess-1");

            handler.afterConnectionEstablished(session);

            // Verify broadcast reaches the session
            handler.broadcast("hello");
            verify(session).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("afterConnectionClosed removes session and subscription")
        void connectionClosed() throws Exception {
            WebSocketSession session = mockSession("sess-2");
            handler.afterConnectionEstablished(session);

            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            // Session should no longer receive broadcasts
            reset(session);
            handler.broadcast("after-close");
            verify(session, never()).sendMessage(any());
        }

        @Test
        @DisplayName("multiple sessions are tracked independently")
        void multipleSessions() throws Exception {
            WebSocketSession s1 = mockSession("s1");
            WebSocketSession s2 = mockSession("s2");

            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);

            handler.broadcast("multi");
            verify(s1).sendMessage(any(TextMessage.class));
            verify(s2).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("closing one session does not affect others")
        void closeOneSessionKeepsOthers() throws Exception {
            WebSocketSession s1 = mockSession("s1");
            WebSocketSession s2 = mockSession("s2");

            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);
            handler.afterConnectionClosed(s1, CloseStatus.NORMAL);

            reset(s2);
            when(s2.isOpen()).thenReturn(true);
            handler.broadcast("still-here");
            verify(s2).sendMessage(any(TextMessage.class));
        }
    }

    // ======================== handleTransportError ========================

    @Nested
    @DisplayName("Transport error handling")
    class TransportError {

        @Test
        @DisplayName("handleTransportError does not throw")
        void transportErrorDoesNotThrow() throws Exception {
            WebSocketSession session = mockSession("err-1");

            assertThatCode(() ->
                    handler.handleTransportError(session, new RuntimeException("test error"))
            ).doesNotThrowAnyException();
        }
    }

    // ======================== broadcast ========================

    @Nested
    @DisplayName("broadcast - plain text")
    class BroadcastPlainText {

        @Test
        @DisplayName("broadcast sends to all connected open sessions")
        void broadcastToAll() throws Exception {
            WebSocketSession s1 = mockSession("b1");
            WebSocketSession s2 = mockSession("b2");

            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);

            handler.broadcast("test-message");

            verify(s1).sendMessage(argThat(msg ->
                    ((TextMessage) msg).getPayload().equals("test-message")));
            verify(s2).sendMessage(argThat(msg ->
                    ((TextMessage) msg).getPayload().equals("test-message")));
        }

        @Test
        @DisplayName("broadcast skips closed sessions")
        void broadcastSkipsClosed() throws Exception {
            WebSocketSession closed = mockSession("closed");
            when(closed.isOpen()).thenReturn(false);
            WebSocketSession open = mockSession("open");

            handler.afterConnectionEstablished(closed);
            handler.afterConnectionEstablished(open);

            handler.broadcast("msg");

            verify(closed, never()).sendMessage(any());
            verify(open).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("broadcast with no sessions is a no-op")
        void broadcastNoSessions() {
            assertThatCode(() -> handler.broadcast("nobody-here"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("broadcast handles IOException on a session gracefully")
        void broadcastHandlesIOException() throws Exception {
            WebSocketSession s1 = mockSession("ioe");
            doThrow(new IOException("write failed")).when(s1).sendMessage(any());
            WebSocketSession s2 = mockSession("ok");

            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);

            assertThatCode(() -> handler.broadcast("msg"))
                    .doesNotThrowAnyException();

            // s2 should still receive the message
            verify(s2).sendMessage(any(TextMessage.class));
        }
    }

    // ======================== broadcastMonitorResult ========================

    @Nested
    @DisplayName("broadcastMonitorResult")
    class BroadcastMonitorResult {

        @Test
        @DisplayName("broadcasts JSON payload with correct fields")
        void broadcastsCorrectJson() throws Exception {
            WebSocketSession session = mockSession("json-1");
            handler.afterConnectionEstablished(session);

            Probe monitor = createMonitor(42, 23.5);
            MonitorResult result = new MonitorResult(monitor, 23.5);

            handler.broadcastMonitorResult(result);

            verify(session).sendMessage(argThat(msg -> {
                String payload = ((TextMessage) msg).getPayload();
                return payload.contains("\"type\":\"monitorResult\"")
                        && payload.contains("\"monitorId\":42")
                        && payload.contains("\"value\":23.5")
                        && payload.contains("\"status\":\"NORMAL\"");
            }));
        }

        @Test
        @DisplayName("broadcasts null value as JSON null")
        void broadcastsNullValue() throws Exception {
            WebSocketSession session = mockSession("json-3");
            handler.afterConnectionEstablished(session);

            Probe monitor = createMonitor(1, null);
            monitor.setValue(null);
            MonitorResult result = new MonitorResult(monitor, null);

            handler.broadcastMonitorResult(result);

            verify(session).sendMessage(argThat(msg -> {
                String payload = ((TextMessage) msg).getPayload();
                return payload.contains("\"value\":null");
            }));
        }

        @Test
        @DisplayName("broadcastMonitorResult with no sessions is a no-op")
        void broadcastMonitorResultNoSessions() {
            Probe monitor = createMonitor(1, 10.0);
            MonitorResult result = new MonitorResult(monitor, 10.0);

            assertThatCode(() -> handler.broadcastMonitorResult(result))
                    .doesNotThrowAnyException();
        }
    }

    // ======================== text message handling ========================

    @Nested
    @DisplayName("handleTextMessage - subscription")
    class SubscriptionHandling {

        @Test
        @DisplayName("subscribe action registers monitorIds for session")
        void subscribeRegistersMonitors() throws Exception {
            WebSocketSession session = mockSession("sub-1");
            handler.afterConnectionEstablished(session);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(Map.of(
                    "action", "subscribe",
                    "monitorIds", java.util.List.of(1, 2, 3)
            ));
            TextMessage msg = new TextMessage(json);

            handler.handleTextMessage(session, msg);

            // Now only results for monitors 1,2,3 should be delivered
            Probe monitor = createMonitor(1, 10.0);
            MonitorResult result = new MonitorResult(monitor, 10.0);
            handler.broadcastMonitorResult(result);
            verify(session).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("subscribe filters out non-subscribed monitors")
        void subscribeFiltersNonSubscribed() throws Exception {
            WebSocketSession session = mockSession("sub-2");
            handler.afterConnectionEstablished(session);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(Map.of(
                    "action", "subscribe",
                    "monitorIds", java.util.List.of(1, 2)
            ));
            handler.handleTextMessage(session, new TextMessage(json));

            // Monitor 99 is not subscribed
            Probe monitor = createMonitor(99, 10.0);
            MonitorResult result = new MonitorResult(monitor, 10.0);
            handler.broadcastMonitorResult(result);
            // No message sent because monitor 99 is not in subscription
            verify(session, never()).sendMessage(any());
        }

        @Test
        @DisplayName("unsubscribe removes subscription filter")
        void unsubscribeRemovesFilter() throws Exception {
            WebSocketSession session = mockSession("sub-3");
            handler.afterConnectionEstablished(session);

            ObjectMapper mapper = new ObjectMapper();
            // Subscribe first
            String subJson = mapper.writeValueAsString(Map.of(
                    "action", "subscribe",
                    "monitorIds", java.util.List.of(1)
            ));
            handler.handleTextMessage(session, new TextMessage(subJson));

            // Unsubscribe
            String unsubJson = mapper.writeValueAsString(Map.of("action", "unsubscribe"));
            handler.handleTextMessage(session, new TextMessage(unsubJson));

            // Now monitor 99 should be delivered (no filter)
            reset(session);
            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("sub-3");
            Probe monitor = createMonitor(99, 10.0);
            MonitorResult result = new MonitorResult(monitor, 10.0);
            handler.broadcastMonitorResult(result);
            verify(session).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("invalid JSON is handled gracefully")
        void invalidJsonHandled() throws Exception {
            WebSocketSession session = mockSession("sub-4");
            handler.afterConnectionEstablished(session);

            assertThatCode(() ->
                    handler.handleTextMessage(session, new TextMessage("not-json"))
            ).doesNotThrowAnyException();
        }
    }
}
