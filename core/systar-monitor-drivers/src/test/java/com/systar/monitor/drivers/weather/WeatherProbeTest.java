package com.systar.monitor.drivers.weather;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class WeatherProbeTest {

    private WeatherProbe probe;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        probe = new WeatherProbe();
        service = new WeatherService();
        service.setUrl("http://localhost:0/fake");
        service.setStationCode("ST001");
    }

    private void initProbeWithSource(String source) {
        ProbeType type = new ProbeType("weather-probe");
        type.setSource(source);
        probe.init(type, 1, "weather-probe");
    }

    @Nested
    @DisplayName("parseSource - valid and invalid inputs")
    class ParseSource {

        @Test
        @DisplayName("parses temperature attribute from source")
        void parsesAttribute() {
            initProbeWithSource("temperature");
            assertThat(probe.getAttribute()).isEqualTo("temperature");
        }

        @Test
        @DisplayName("trims and lowercases attribute name")
        void trimsAndLowercases() {
            initProbeWithSource("  Temperature  ");
            assertThat(probe.getAttribute()).isEqualTo("temperature");
        }

        @Test
        @DisplayName("null source leaves attribute unset, detect throws")
        void nullSource() {
            ProbeType type = new ProbeType("weather-probe");
            type.setSource(null);
            probe.init(type, 1, "weather-probe");

            MonitorResult result = new MonitorResult(probe);
            assertThatThrownBy(() -> probe.detect(result))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank source throws IllegalArgumentException during init")
        void blankSource() {
            assertThatThrownBy(() -> initProbeWithSource("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }
    }

    @Nested
    @DisplayName("detect - with cached data")
    class DetectCached {

        @Test
        @DisplayName("reads value from cached JSON data")
        void readsFromCache() throws Exception {
            initProbeWithSource("temperature");
            String json = """
                {"Code":200,"Content":{"temperature":22.5}}
                """;
            service.setCachedJsonData(json);

            // Attach probe to service
            service.addMonitor(probe);
            probe.setSource(service);

            IMonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isEqualTo(22.5f);
            assertThat(result.getSampleTime()).isNotNull();
        }

        @Test
        @DisplayName("extracts humidity attribute from cached data")
        void readsHumidity() throws Exception {
            initProbeWithSource("humidity");
            String json = """
                {"Code":200,"Content":{"humidity":75.0,"temperature":20.0}}
                """;
            service.setCachedJsonData(json);

            service.addMonitor(probe);
            probe.setSource(service);

            IMonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isEqualTo(75.0f);
        }
    }

    @Nested
    @DisplayName("detect - error handling")
    class DetectErrors {

        @Test
        @DisplayName("wrong service type throws IllegalStateException")
        void wrongServiceType() {
            initProbeWithSource("temperature");
            // Probe is not attached to any service
            MonitorResult result = new MonitorResult(probe);
            assertThatThrownBy(() -> probe.detect(result))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WeatherService");
        }
    }
}
