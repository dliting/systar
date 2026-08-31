package com.systar.monitor.drivers.ups;

import com.systar.monitor.drivers.ups.UpsService.UpsConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class UpsServiceTest {

    private UpsService service;

    @BeforeEach
    void setUp() {
        service = new UpsService();
        service.setHost("192.168.1.100");
    }

    // ======================== configuration defaults ========================

    @Nested
    @DisplayName("Default configuration")
    class Defaults {

        @Test
        @DisplayName("default port is 161")
        void defaultPort() {
            assertThat(service.getPort()).isEqualTo(161);
        }

        @Test
        @DisplayName("default protocol is snmp")
        void defaultProtocol() {
            assertThat(service.getProtocol()).isEqualTo("snmp");
        }

        @Test
        @DisplayName("default community is public")
        void defaultCommunity() {
            assertThat(service.getCommunity()).isEqualTo("public");
        }
    }

    // ======================== OID mapping ========================

    @Nested
    @DisplayName("UPS-MIB OID mapping (RFC 1628)")
    class OidMapping {

        @Test
        @DisplayName("maps input_voltage to correct OID")
        void inputVoltage() {
            assertThat(UpsService.resolveOid("input_voltage"))
                    .isEqualTo("1.3.6.1.2.1.33.1.3.3.1.3.1");
        }

        @Test
        @DisplayName("maps output_voltage to correct OID")
        void outputVoltage() {
            assertThat(UpsService.resolveOid("output_voltage"))
                    .isEqualTo("1.3.6.1.2.1.33.1.4.4.1.3.1");
        }

        @Test
        @DisplayName("maps battery_level to correct OID")
        void batteryLevel() {
            assertThat(UpsService.resolveOid("battery_level"))
                    .isEqualTo("1.3.6.1.2.1.33.1.2.4.0");
        }

        @Test
        @DisplayName("maps battery_status to correct OID")
        void batteryStatus() {
            assertThat(UpsService.resolveOid("battery_status"))
                    .isEqualTo("1.3.6.1.2.1.33.1.2.1.0");
        }

        @Test
        @DisplayName("maps ups_status to correct OID")
        void upsStatus() {
            assertThat(UpsService.resolveOid("ups_status"))
                    .isEqualTo("1.3.6.1.2.1.33.1.4.1.0");
        }

        @Test
        @DisplayName("maps output_frequency to correct OID")
        void outputFrequency() {
            assertThat(UpsService.resolveOid("output_frequency"))
                    .isEqualTo("1.3.6.1.2.1.33.1.4.4.1.2.1");
        }

        @Test
        @DisplayName("maps input_frequency to correct OID")
        void inputFrequency() {
            assertThat(UpsService.resolveOid("input_frequency"))
                    .isEqualTo("1.3.6.1.2.1.33.1.3.3.1.2.1");
        }

        @Test
        @DisplayName("maps load_level to correct OID")
        void loadLevel() {
            assertThat(UpsService.resolveOid("load_level"))
                    .isEqualTo("1.3.6.1.2.1.33.1.4.4.1.5.1");
        }

        @Test
        @DisplayName("throws for unknown attribute")
        void unknownAttribute() {
            assertThatThrownBy(() -> UpsService.resolveOid("unknown_attr"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown UPS attribute");
        }

        @Test
        @DisplayName("all 8 standard UPS attributes are mapped")
        void allAttributesMapped() {
            assertThat(UpsService.getSupportedAttributes())
                    .containsExactlyInAnyOrder(
                            "input_voltage", "output_voltage", "battery_level",
                            "battery_status", "ups_status", "output_frequency",
                            "input_frequency", "load_level"
                    );
        }
    }

    // ======================== connection factory ========================

    @Nested
    @DisplayName("Connection creation")
    class ConnectionCreation {

        @Test
        @DisplayName("createConnection returns UpsConnection")
        void createsConnection() throws Exception {
            UpsConnection conn = (UpsConnection) service.createConnection();
            assertThat(conn).isNotNull();
            assertThat(conn.isConnected()).isFalse();
        }
    }

    // ======================== lifecycle ========================

    @Nested
    @DisplayName("Service lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("start completes without error")
        void startService() throws Exception {
            assertThatCode(() -> service.start()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("stop completes without error")
        void stopService() {
            assertThatCode(() -> service.stop()).doesNotThrowAnyException();
        }
    }
}
