package com.systar.monitor.drivers.bacnet;

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
class BacnetProbeTest {

    private BacnetProbe probe;

    @BeforeEach
    void setUp() {
        probe = new BacnetProbe();
    }

    private void initProbe(String source) {
        ProbeType type = new ProbeType("bacnet-probe");
        type.setSource(source);
        probe.init(type, 1, "bacnet-probe");
    }

    @Nested
    @DisplayName("parseSource - valid formats")
    class ParseSourceValid {

        @Test
        @DisplayName("analogInput:0:presentValue → objectType=0, property=85")
        void analogInputPresentValue() {
            initProbe("analogInput:0:presentValue");
            assertThat(probe.getObjectTypeName()).isEqualTo("analogInput");
            assertThat(probe.getObjectType()).isEqualTo(0);
            assertThat(probe.getInstanceNumber()).isEqualTo(0);
            assertThat(probe.getPropertyIdentifier()).isEqualTo(85);
        }

        @Test
        @DisplayName("binaryOutput:3:statusFlags → objectType=4, property=111")
        void binaryOutputStatusFlags() {
            initProbe("binaryOutput:3:statusFlags");
            assertThat(probe.getObjectType()).isEqualTo(4);
            assertThat(probe.getInstanceNumber()).isEqualTo(3);
            assertThat(probe.getPropertyIdentifier()).isEqualTo(111);
        }

        @Test
        @DisplayName("analogValue:1:units → objectType=2, property=117")
        void analogValueUnits() {
            initProbe("analogValue:1:units");
            assertThat(probe.getObjectType()).isEqualTo(2);
            assertThat(probe.getPropertyIdentifier()).isEqualTo(117);
        }

        @Test
        @DisplayName("multiStateInput:10:description → objectType=13")
        void multiStateInputDescription() {
            initProbe("multiStateInput:10:description");
            assertThat(probe.getObjectType()).isEqualTo(13);
            assertThat(probe.getPropertyIdentifier()).isEqualTo(28);
        }

        @Test
        @DisplayName("binaryInput:5:objectName → objectType=3, property=77")
        void binaryInputObjectName() {
            initProbe("binaryInput:5:objectName");
            assertThat(probe.getObjectType()).isEqualTo(3);
            assertThat(probe.getPropertyIdentifier()).isEqualTo(77);
        }
    }

    @Nested
    @DisplayName("parseSource - invalid formats")
    class ParseSourceInvalid {

        @Test
        @DisplayName("null source throws during detect")
        void nullSource() {
            ProbeType type = new ProbeType("bacnet-probe");
            type.setSource(null);
            probe.init(type, 1, "bacnet-probe");
            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank source throws")
        void blankSource() {
            assertThatThrownBy(() -> initProbe("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("missing property part throws")
        void missingProperty() {
            assertThatThrownBy(() -> initProbe("analogInput:0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BACnet source format");
        }

        @Test
        @DisplayName("unknown object type throws")
        void unknownObjectType() {
            assertThatThrownBy(() -> initProbe("bogusType:1:presentValue"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown BACnet object type");
        }

        @Test
        @DisplayName("unknown property throws")
        void unknownProperty() {
            assertThatThrownBy(() -> initProbe("analogInput:1:xyzProp"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown BACnet property");
        }
    }

    @Nested
    @DisplayName("detect - service validation")
    class DetectServiceValidation {

        @Test
        @DisplayName("throws when not attached to BacnetService")
        void wrongServiceType() {
            initProbe("analogInput:0:presentValue");
            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BacnetService");
        }
    }
}
