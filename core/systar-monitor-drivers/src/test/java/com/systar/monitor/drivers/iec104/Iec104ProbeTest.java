package com.systar.monitor.drivers.iec104;

import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class Iec104ProbeTest {

    private Iec104Probe probe;

    @BeforeEach
    void setUp() {
        probe = new Iec104Probe();
    }

    private void initProbe(String source) {
        ProbeType type = new ProbeType("iec104-probe");
        type.setSource(source);
        probe.init(type, 1, "iec104-probe");
    }

    @Nested
    @DisplayName("source parsing")
    class ParseSource {

        @Test
        @DisplayName("parses YC:12345 format")
        void parsesYc() {
            initProbe("YC:12345");
            assertThat(probe.getDataType()).isEqualTo("YC");
            assertThat(probe.getAddress()).isEqualTo(12345);
        }

        @Test
        @DisplayName("parses YX:67 remote signal")
        void parsesYx() {
            initProbe("YX:67");
            assertThat(probe.getDataType()).isEqualTo("YX");
            assertThat(probe.getAddress()).isEqualTo(67);
        }

        @Test
        @DisplayName("normalizes Chinese 遥测 to YC")
        void normalizesChineseTelemeter() {
            initProbe("遥测:100");
            assertThat(probe.getDataType()).isEqualTo("YC");
        }

        @Test
        @DisplayName("normalizes Chinese 遥信 to YX")
        void normalizesChineseTeleindication() {
            initProbe("遥信:200");
            assertThat(probe.getDataType()).isEqualTo("YX");
        }

        @Test
        @DisplayName("rejects empty source")
        void emptySource() {
            assertThatThrownBy(() -> initProbe("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
