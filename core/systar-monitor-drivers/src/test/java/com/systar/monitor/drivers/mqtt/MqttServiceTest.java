package com.systar.monitor.drivers.mqtt;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MqttServiceTest {

    private MqttService service;
    private MqttProbe probe1;
    private MqttProbe probe2;

    @BeforeEach
    void setUp() {
        service = new MqttService();

        probe1 = new MqttProbe();
        ProbeType type1 = new ProbeType("mqtt-probe-1");
        type1.setSource("sensor/temperature");
        probe1.init(type1, 101, "temp-probe");

        probe2 = new MqttProbe();
        ProbeType type2 = new ProbeType("mqtt-probe-2");
        type2.setSource("sensor/humidity");
        probe2.init(type2, 102, "humi-probe");
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("default qos is 1")
        void defaultQos() {
            assertThat(new MqttService().getQos()).isEqualTo(1);
        }

        @Test
        @DisplayName("default keepAlive is 60s")
        void defaultKeepAlive() {
            assertThat(new MqttService().getKeepAliveIntervalSecs()).isEqualTo(60);
        }

        @Test
        @DisplayName("getters and setters")
        void gettersAndSetters() {
            service.setBrokerUrl("tcp://localhost:1883");
            service.setClientId("test-client");
            service.setUsername("user");
            service.setPassword("pass");
            service.setQos(2);
            service.setKeepAliveIntervalSecs(120);

            assertThat(service.getBrokerUrl()).isEqualTo("tcp://localhost:1883");
            assertThat(service.getClientId()).isEqualTo("test-client");
            assertThat(service.getUsername()).isEqualTo("user");
            assertThat(service.getPassword()).isEqualTo("pass");
            assertThat(service.getQos()).isEqualTo(2);
            assertThat(service.getKeepAliveIntervalSecs()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("probe registration")
    class ProbeRegistration {

        @Test
        @DisplayName("registerProbe does not throw for valid probe")
        void registersValidProbe() {
            assertThatCode(() -> service.registerProbe("sensor/temperature", probe1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("multiple probes on same topic does not throw")
        void multipleProbesSameTopic() {
            MqttProbe probe3 = new MqttProbe();
            ProbeType type3 = new ProbeType("mqtt-3");
            type3.setSource("sensor/both:$.value");
            probe3.init(type3, 103, "third");

            service.registerProbe("sensor/both", probe1);
            assertThatCode(() -> service.registerProbe("sensor/both", probe3))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("start without brokerUrl does not throw, just skips")
        void startWithoutBrokerUrl() {
            assertThatCode(() -> service.start()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("stop is safe when not started")
        void stopSafeWhenNotStarted() {
            assertThatCode(() -> service.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("stop is idempotent")
        void stopIdempotent() {
            service.setBrokerUrl("tcp://localhost:1883");
            service.stop();
            assertThatCode(() -> service.stop()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("message routing")
    class MessageRouting {

        @Test
        @DisplayName("probe receives value via onMessage")
        void probeReceivesValue() {
            service.registerProbe("sensor/temperature", probe1);

            // Simulate what MqttCallback would do
            probe1.onMessage("36.5");

            assertThat(probe1.getCachedValue()).isEqualTo(36.5f);
        }

        @Test
        @DisplayName("probe with jsonPath extracts value")
        void probeWithJsonPath() {
            MqttProbe jsonProbe = new MqttProbe();
            ProbeType type = new ProbeType("json");
            type.setSource("device/data:$.temp");
            jsonProbe.init(type, 201, "json-probe");

            service.registerProbe("device/data", jsonProbe);
            jsonProbe.onMessage("{\"temp\": 22.5, \"status\": \"ok\"}");

            assertThat(jsonProbe.getCachedValue()).isEqualTo(22.5f);
        }

        @Test
        @DisplayName("different topics isolated")
        void differentTopicsIsolated() {
            service.registerProbe("sensor/temperature", probe1);
            service.registerProbe("sensor/humidity", probe2);

            probe1.onMessage("25.0");
            probe2.onMessage("60.0");

            assertThat(probe1.getCachedValue()).isEqualTo(25.0f);
            assertThat(probe2.getCachedValue()).isEqualTo(60.0f);
        }

        @Test
        @DisplayName("ResultDispatcher can be set and retrieved")
        void dispatcherCanBeSet() {
            ResultDispatcher dispatcher = mock(ResultDispatcher.class);
            service.setResultDispatcher(dispatcher);
            assertThat(service.getResultDispatcher()).isSameAs(dispatcher);
        }
    }
}
