package com.systar.monitor.drivers.websocket;

import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class WsProbeTest {

    private WsProbe probe;

    @BeforeEach
    void setUp() {
        probe = new WsProbe();
    }

    private void initProbe(String source) {
        ProbeType type = new ProbeType("ws-probe");
        type.setSource(source);
        probe.init(type, 1, "ws-probe");
    }

    @Nested
    @DisplayName("source parsing")
    class ParseSource {

        @Test
        @DisplayName("stores trimmed message key from source")
        void storesMessageKey() {
            initProbe("sensor.temperature");
            assertThat(probe.getMessageKey()).isEqualTo("sensor.temperature");
        }

        @Test
        @DisplayName("null source accepted (passive probe)")
        void nullSource() {
            ProbeType type = new ProbeType("ws-probe");
            type.setSource(null);
            probe.init(type, 1, "ws-probe");
            assertThat(probe.getMessageKey()).isNull();
        }
    }

    @Nested
    @DisplayName("makeRegisterKey")
    class RegisterKey {

        @Test
        @DisplayName("returns message key as register key")
        void returnsMessageKey() {
            initProbe("sensor.temperature");
            assertThat(probe.makeRegisterKey()).isEqualTo("sensor.temperature");
        }
    }
}
