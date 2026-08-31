package com.systar.monitor.drivers.environmental;

import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class EnvProbeTest {

    private EnvProbe probe;

    @BeforeEach
    void setUp() {
        probe = new EnvProbe();
    }

    private void initProbeWithSource(String source) {
        ProbeType type = new ProbeType("env-probe");
        type.setSource(source);
        probe.init(type, 1, "env-probe");
    }

    @Nested
    @DisplayName("parseSource - attribute parsing")
    class ParseSource {

        @Test
        @DisplayName("parses temperature attribute")
        void parsesTemperature() {
            initProbeWithSource("temperature");
            assertThat(probe.getAttribute()).isEqualTo("temperature");
        }

        @Test
        @DisplayName("lowercases and trims attribute")
        void lowercasesAndTrims() {
            initProbeWithSource("  PM25  ");
            assertThat(probe.getAttribute()).isEqualTo("pm25");
        }

        @Test
        @DisplayName("blank source throws during init")
        void blankSource() {
            assertThatThrownBy(() -> initProbeWithSource("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
