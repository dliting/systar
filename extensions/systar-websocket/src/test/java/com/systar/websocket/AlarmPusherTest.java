package com.systar.websocket;

import com.systar.data.event.AlarmPersistedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AlarmPusherTest {

    private MonitorWebSocketHandler handler;
    private AlarmPusher            pusher;

    @BeforeEach
    void setUp() {
        handler = mock(MonitorWebSocketHandler.class);
        pusher  = new AlarmPusher(handler);
    }

    @Test
    @DisplayName("onAlarmPersisted broadcasts JSON with alarm fields")
    void onAlarmPersistedBroadcastsCorrectJson() {
        AlarmPersistedEvent event = new AlarmPersistedEvent(this, 100, 200, 42);

        pusher.onAlarmPersisted(event);

        verify(handler).broadcast(argThat(json -> {
            return json.contains("\"type\":\"alarm\"")
                    && json.contains("\"alarmMessageId\":100")
                    && json.contains("\"eventRankId\":200")
                    && json.contains("\"assetId\":42");
        }));
    }

    @Test
    @DisplayName("onAlarmPersisted handles broadcast exception gracefully")
    void onAlarmPersistedHandlesException() {
        AlarmPersistedEvent event = new AlarmPersistedEvent(this, 1, 2, 3);

        doThrow(new RuntimeException("broadcast failed")).when(handler).broadcast(anyString());

        // Should not throw
        pusher.onAlarmPersisted(event);

        verify(handler).broadcast(anyString());
    }
}
