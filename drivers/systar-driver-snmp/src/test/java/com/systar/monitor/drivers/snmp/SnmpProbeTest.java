package com.systar.monitor.drivers.snmp;

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
class SnmpProbeTest {

    private SnmpProbe probe;

    @BeforeEach
    void setUp() {
        probe = new SnmpProbe();
    }

    private void initProbeWithSource(String source) {
        ProbeType type = new ProbeType("snmp-probe");
        type.setSource(source);
        probe.init(type, 1, "snmp-probe");
    }

    // ======================== source parsing ========================

    @Nested
    @DisplayName("parseSource - OID validation")
    class ParseSource {

        @Test
        @DisplayName("accepts standard system OID")
        void standardOid() {
            initProbeWithSource("1.3.6.1.2.1.1.3.0");
            assertThat(probe.getOid()).isEqualTo("1.3.6.1.2.1.1.3.0");
        }

        @Test
        @DisplayName("accepts interface OID with instance index")
        void interfaceOid() {
            initProbeWithSource("1.3.6.1.2.1.2.2.1.10.1");
            assertThat(probe.getOid()).isEqualTo("1.3.6.1.2.1.2.2.1.10.1");
        }

        @Test
        @DisplayName("rejects OID starting with dot")
        void startsWithDot() {
            assertThatThrownBy(() -> initProbeWithSource(".1.3.6.1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SNMP OID");
        }

        @Test
        @DisplayName("rejects OID with non-numeric characters")
        void nonNumeric() {
            assertThatThrownBy(() -> initProbeWithSource("1.3.6.1.abc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SNMP OID");
        }

        @Test
        @DisplayName("rejects null source")
        void nullSource() {
            ProbeType type = new ProbeType("snmp-probe");
            type.setSource(null);
            probe.init(type, 1, "snmp-probe");

            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }
    }

    // ======================== detect ========================

    @Nested
    @DisplayName("detect - with SNMP connection")
    class Detect {

        @Test
        @DisplayName("detect sets error when not attached to SnmpService")
        void wrongService() {
            initProbeWithSource("1.3.6.1.2.1.1.3.0");
            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SnmpService");
        }
    }

    // ======================== OID to int[] conversion ========================

    @Nested
    @DisplayName("OID string to int array conversion")
    class OidConversion {

        @Test
        @DisplayName("converts OID string to int array")
        void convertsOid() {
            int[] result = SnmpProbe.oidToIntArray("1.3.6.1.2.1.1.3.0");
            assertThat(result).containsExactly(1, 3, 6, 1, 2, 1, 1, 3, 0);
        }

        @Test
        @DisplayName("single element OID")
        void singleElement() {
            int[] result = SnmpProbe.oidToIntArray("1");
            assertThat(result).containsExactly(1);
        }
    }
}
