package com.systar.monitor.drivers.opcua;

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
class OpcUaProbeTest {

    private OpcUaProbe probe;

    @BeforeEach
    void setUp() {
        probe = new OpcUaProbe();
    }

    private void initProbe(String source) {
        ProbeType type = new ProbeType("opcua-probe");
        type.setSource(source);
        probe.init(type, 1, "opcua-probe");
        // init() no longer parses type source; simulate metadata-based resolution
        probe.setMetadata("NodeId", source);
        probe.parseMetadataNodeId();
    }

    @Nested
    @DisplayName("parseSource - valid formats")
    class ParseSourceValid {

        @Test
        @DisplayName("ns=2;s=Temperature → namespace=2, stringId=Temperature")
        void stringNodeId() {
            initProbe("ns=2;s=Temperature");
            assertThat(probe.getNamespaceIndex()).isEqualTo(2);
            assertThat(probe.getIdentifier()).isEqualTo("Temperature");
            assertThat(probe.isIntegerId()).isFalse();
        }

        @Test
        @DisplayName("ns=0;i=2258 → namespace=0, intId=2258")
        void integerNodeId() {
            initProbe("ns=0;i=2258");
            assertThat(probe.getNamespaceIndex()).isEqualTo(0);
            assertThat(probe.getIdentifier()).isEqualTo("2258");
            assertThat(probe.isIntegerId()).isTrue();
        }

        @Test
        @DisplayName("ns=4;s=Pressure → namespace=4")
        void highNamespace() {
            initProbe("ns=4;s=Pressure");
            assertThat(probe.getNamespaceIndex()).isEqualTo(4);
        }

        @Test
        @DisplayName("metadata NodeId takes priority over type source")
        void metadataOverridesTypeSource() {
            ProbeType type = new ProbeType("opcua-probe");
            type.setSource("ns=2;s=TypeLevelNode");
            probe.init(type, 1, "opcua-probe");
            probe.setMetadata("NodeId", "ns=3;s=MetadataNode");
            probe.parseMetadataNodeId();
            assertThat(probe.getNamespaceIndex()).isEqualTo(3);
            assertThat(probe.getIdentifier()).isEqualTo("MetadataNode");
        }

        @Test
        @DisplayName("fallback to type source when no metadata NodeId")
        void fallbackToTypeSource() {
            ProbeType type = new ProbeType("opcua-probe");
            type.setSource("ns=2;s=TypeLevelNode");
            probe.init(type, 1, "opcua-probe");
            probe.parseMetadataNodeId();
            assertThat(probe.getNamespaceIndex()).isEqualTo(2);
            assertThat(probe.getIdentifier()).isEqualTo("TypeLevelNode");
        }
    }

    @Nested
    @DisplayName("parseSource - invalid formats")
    class ParseSourceInvalid {

        @Test
        @DisplayName("null source sets error during detect")
        void nullSource() {
            ProbeType type = new ProbeType("opcua-probe");
            type.setSource(null);
            probe.init(type, 1, "opcua-probe");
            MonitorResult result = new MonitorResult(probe);
            assertThatCode(() -> probe.detect(result)).doesNotThrowAnyException();
            assertThat(result.getError()).isNotNull();
        }

        @Test
        @DisplayName("blank source is silently skipped")
        void blankSource() {
            initProbe("   ");
            assertThat(probe.getIdentifier()).isNull();
        }

        @Test
        @DisplayName("missing identifier throws")
        void missingIdentifier() {
            assertThatThrownBy(() -> initProbe("ns=2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must contain s=<stringId> or i=<intId>");
        }

        @Test
        @DisplayName("both s= and i= present → last one wins (integerId=true)")
        void bothSAndI() {
            initProbe("ns=2;s=Temp;i=42");
            assertThat(probe.getIdentifier()).isEqualTo("42");
            assertThat(probe.isIntegerId()).isTrue();
        }

        @Test
        @DisplayName("empty s= identifier throws")
        void emptyStringId() {
            assertThatThrownBy(() -> initProbe("ns=0;s="))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must contain s=<stringId> or i=<intId>");
        }

        @Test
        @DisplayName("unknown keys ignored, falls through to missing identifier")
        void unknownKeysIgnored() {
            assertThatThrownBy(() -> initProbe("foo=bar;x=y"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must contain s=<stringId> or i=<intId>");
        }
    }

    @Nested
    @DisplayName("detect - service validation")
    class DetectServiceValidation {

        @Test
        @DisplayName("throws when not attached to OpcUaService")
        void wrongServiceType() {
            initProbe("ns=2;s=Temperature");
            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("OpcUaService");
        }
    }
}
