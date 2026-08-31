package com.systar.monitor.drivers.mqtt;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MqttProbeTest {

    private MqttProbe probe;

    @BeforeEach
    void setUp() {
        probe = new MqttProbe();
    }

    private void initProbe(String source) {
        ProbeType type = new ProbeType("mqtt-probe");
        type.setSource(source);
        probe.init(type, 1, "mqtt-probe");
    }

    @Nested
    @DisplayName("source parsing")
    class ParseSource {

        @Test
        @DisplayName("topic only → topic set, jsonPath null")
        void topicOnly() {
            initProbe("sensor/temperature");
            assertThat(probe.getTopic()).isEqualTo("sensor/temperature");
            assertThat(probe.getJsonPath()).isNull();
        }

        @Test
        @DisplayName("topic with jsonPath → both parsed")
        void topicWithJsonPath() {
            initProbe("device/data:$.temperature");
            assertThat(probe.getTopic()).isEqualTo("device/data");
            assertThat(probe.getJsonPath()).isEqualTo("$.temperature");
        }

        @Test
        @DisplayName("topic with nested jsonPath")
        void topicWithNestedJsonPath() {
            initProbe("building/room:$.data.sensor.value");
            assertThat(probe.getTopic()).isEqualTo("building/room");
            assertThat(probe.getJsonPath()).isEqualTo("$.data.sensor.value");
        }

        @Test
        @DisplayName("empty source throws")
        void emptySource() {
            assertThatThrownBy(() -> initProbe("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null source — probe initializes but has null topic")
        void nullSource() {
            ProbeType type = new ProbeType("mqtt-probe");
            type.setSource(null);
            probe.init(type, 1, "p");
            assertThat(probe.getTopic()).isNull();
        }

        @Test
        @DisplayName("empty jsonPath after colon throws")
        void emptyJsonPathAfterColon() {
            assertThatThrownBy(() -> initProbe("topic:"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jsonPath");
        }

        @Test
        @DisplayName("colon only in middle → valid")
        void colonInTopic() {
            initProbe("sensor/temp:extra");
            assertThat(probe.getTopic()).isEqualTo("sensor/temp");
            assertThat(probe.getJsonPath()).isEqualTo("extra");
        }
    }

    @Nested
    @DisplayName("makeRegisterKey")
    class RegisterKey {

        @Test
        @DisplayName("returns topic")
        void returnsTopic() {
            initProbe("sensor/temp");
            assertThat(probe.makeRegisterKey()).isEqualTo("sensor/temp");
        }

        @Test
        @DisplayName("returns topic with jsonPath source")
        void returnsTopicWithJsonPath() {
            initProbe("device/data:$.value");
            assertThat(probe.makeRegisterKey()).isEqualTo("device/data");
        }
    }

    @Nested
    @DisplayName("message handling — raw payload")
    class RawPayload {

        @Test
        @DisplayName("integer string → Integer")
        void integerString() {
            initProbe("sensor/count");
            probe.onMessage("42");
            assertThat(probe.getCachedValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("float string → Float")
        void floatString() {
            initProbe("sensor/temp");
            probe.onMessage("36.5");
            assertThat(probe.getCachedValue()).isEqualTo(36.5f);
        }

        @Test
        @DisplayName("true → Boolean true")
        void trueString() {
            initProbe("device/status");
            probe.onMessage("true");
            assertThat(probe.getCachedValue()).isEqualTo(true);
        }

        @Test
        @DisplayName("false → Boolean false")
        void falseString() {
            initProbe("device/status");
            probe.onMessage("false");
            assertThat(probe.getCachedValue()).isEqualTo(false);
        }

        @Test
        @DisplayName("non-numeric → String")
        void stringValue() {
            initProbe("device/name");
            probe.onMessage("pump-01");
            assertThat(probe.getCachedValue()).isEqualTo("pump-01");
        }

        @Test
        @DisplayName("null payload no-ops")
        void nullPayload() {
            initProbe("sensor/temp");
            probe.onMessage(null);
            assertThat(probe.getCachedValue()).isNull();
        }
    }

    @Nested
    @DisplayName("message handling — JSON payload")
    class JsonPayload {

        @Test
        @DisplayName("extracts simple jsonPath")
        void simpleJsonPath() {
            initProbe("device/data:$.temperature");
            probe.onMessage("{\"temperature\": 25.5, \"humidity\": 60}");
            assertThat(probe.getCachedValue()).isEqualTo(25.5f);
        }

        @Test
        @DisplayName("extracts nested jsonPath")
        void nestedJsonPath() {
            initProbe("building/data:$.sensor.temp");
            probe.onMessage("{\"sensor\":{\"temp\": 22.3}}");
            assertThat(probe.getCachedValue()).isEqualTo(22.3f);
        }

        @Test
        @DisplayName("extracts boolean from JSON")
        void booleanFromJson() {
            initProbe("device/state:$.online");
            probe.onMessage("{\"online\": true}");
            assertThat(probe.getCachedValue()).isEqualTo(true);
        }

        @Test
        @DisplayName("extracts string from JSON")
        void stringFromJson() {
            initProbe("device/info:$.status");
            probe.onMessage("{\"status\": \"running\"}");
            assertThat(probe.getCachedValue()).isEqualTo("running");
        }

        @Test
        @DisplayName("missing jsonPath key → null")
        void missingJsonPathKey() {
            initProbe("device/data:$.missing");
            probe.onMessage("{\"temperature\": 25.5}");
            assertThat(probe.getCachedValue()).isNull();
        }

        @Test
        @DisplayName("jsonPath without $ prefix also works")
        void jsonPathWithoutDollar() {
            initProbe("device/data:temperature");
            probe.onMessage("{\"temperature\": 30.0}");
            assertThat(probe.getCachedValue()).isEqualTo(30.0f);
        }
    }

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("returns cached value and sets sample time")
        void returnsCachedValue() throws Exception {
            initProbe("sensor/temp");
            probe.onMessage("18.5");
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);
            assertThat(result.getValue()).isEqualTo(18.5f);
            assertThat(result.getSampleTime()).isGreaterThan(0);
        }

        @Test
        @DisplayName("returns null when no message received")
        void returnsNullWhenEmpty() throws Exception {
            initProbe("sensor/temp");
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);
            assertThat(result.getValue()).isNull();
        }
    }
}
