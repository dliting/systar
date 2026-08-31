package com.systar.monitor.drivers.siemens;

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
class SiemensProbeTest {

    private SiemensProbe probe;

    @BeforeEach
    void setUp() {
        probe = new SiemensProbe();
    }

    private void initProbe(String source) {
        ProbeType type = new ProbeType("siemens-probe");
        type.setSource(source);
        probe.init(type, 1, "siemens-probe");
    }

    @Nested
    @DisplayName("parseSource - DB area formats")
    class ParseSourceDbArea {

        @Test
        @DisplayName("DB1.DBD4:real → area=DB, dbNumber=1, byteOffset=4, dataType=real")
        void dbFloat() {
            initProbe("DB1.DBD4:real");
            assertThat(probe.getArea()).isEqualTo("DB");
            assertThat(probe.getDbNumber()).isEqualTo(1);
            assertThat(probe.getByteOffset()).isEqualTo(4);
            assertThat(probe.getBitOffset()).isEqualTo(0);
            assertThat(probe.getDataType()).isEqualTo("real");
        }

        @Test
        @DisplayName("DB1.DBX0.0:bool → byteOffset=0, bitOffset=0")
        void dbBool() {
            initProbe("DB1.DBX0.0:bool");
            assertThat(probe.getArea()).isEqualTo("DB");
            assertThat(probe.getByteOffset()).isEqualTo(0);
            assertThat(probe.getBitOffset()).isEqualTo(0);
            assertThat(probe.getDataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("DB2.DBW10:int → byteOffset=10, dataType=int")
        void dbWord() {
            initProbe("DB2.DBW10:int");
            assertThat(probe.getDbNumber()).isEqualTo(2);
            assertThat(probe.getByteOffset()).isEqualTo(10);
            assertThat(probe.getDataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("DB5.DBB8:byte → byteOffset=8")
        void dbByte() {
            initProbe("DB5.DBB8:byte");
            assertThat(probe.getByteOffset()).isEqualTo(8);
            assertThat(probe.getDataType()).isEqualTo("byte");
        }
    }

    @Nested
    @DisplayName("parseSource - Marker area formats")
    class ParseSourceMarkerArea {

        @Test
        @DisplayName("M10.2:bool → area=M, byteOffset=10, bitOffset=2")
        void markerBool() {
            initProbe("M10.2:bool");
            assertThat(probe.getArea()).isEqualTo("M");
            assertThat(probe.getDbNumber()).isEqualTo(0);
            assertThat(probe.getByteOffset()).isEqualTo(10);
            assertThat(probe.getBitOffset()).isEqualTo(2);
            assertThat(probe.getDataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("M20:byte → bitOffset=0 (no dot in address)")
        void markerByte() {
            initProbe("M20:byte");
            assertThat(probe.getArea()).isEqualTo("M");
            assertThat(probe.getByteOffset()).isEqualTo(20);
            assertThat(probe.getBitOffset()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("parseSource - invalid formats")
    class ParseSourceInvalid {

        @Test
        @DisplayName("null source throws during detect")
        void nullSource() {
            ProbeType type = new ProbeType("siemens-probe");
            type.setSource(null);
            probe.init(type, 1, "siemens-probe");
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
        @DisplayName("no colon throws")
        void noColon() {
            assertThatThrownBy(() -> initProbe("DB1.DBD4"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Siemens source format");
        }

        @Test
        @DisplayName("unknown area prefix throws")
        void unknownArea() {
            assertThatThrownBy(() -> initProbe("E0.0:bool"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown Siemens address area");
        }

        @Test
        @DisplayName("invalid DB address sub-type throws")
        void invalidDbSubType() {
            assertThatThrownBy(() -> initProbe("DB1.DBV2:real"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot parse DB address");
        }
    }

    @Nested
    @DisplayName("detect - service validation")
    class DetectServiceValidation {

        @Test
        @DisplayName("throws when not attached to SiemensService")
        void wrongServiceType() {
            initProbe("DB1.DBD4:real");
            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SiemensService");
        }
    }
}
