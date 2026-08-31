package com.systar.monitor.drivers.bacnet;

import com.systar.monitor.drivers.bacnet.BacnetService.BacnetConnection;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class BacnetServiceTest {

    private BacnetService service;

    @BeforeEach
    void setUp() {
        service = new BacnetService();
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("default deviceId is 100")
        void defaultDeviceId() {
            assertThat(new BacnetService().getDeviceId()).isEqualTo(100);
        }

        @Test
        @DisplayName("default port is 47808")
        void defaultPort() {
            assertThat(new BacnetService().getRemotePort()).isEqualTo(47808);
        }

        @Test
        @DisplayName("default timeout is 5000ms")
        void defaultTimeout() {
            assertThat(new BacnetService().getTimeout()).isEqualTo(5000);
        }

        @Test
        @DisplayName("getters and setters")
        void gettersAndSetters() {
            service.setRemoteHost("192.168.1.100");
            service.setRemotePort(47809);
            service.setDeviceId(200);
            service.setTimeout(10000);

            assertThat(service.getRemoteHost()).isEqualTo("192.168.1.100");
            assertThat(service.getRemotePort()).isEqualTo(47809);
            assertThat(service.getDeviceId()).isEqualTo(200);
            assertThat(service.getTimeout()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("object type mapping")
    class ObjectTypeMapping {

        @Test
        @DisplayName("0 → analogInput")
        void analogInput() {
            assertThat(BacnetConnection.mapObjectType(0)).isEqualTo(ObjectType.analogInput);
        }

        @Test
        @DisplayName("3 → binaryInput")
        void binaryInput() {
            assertThat(BacnetConnection.mapObjectType(3)).isEqualTo(ObjectType.binaryInput);
        }

        @Test
        @DisplayName("5 → binaryValue")
        void binaryValue() {
            assertThat(BacnetConnection.mapObjectType(5)).isEqualTo(ObjectType.binaryValue);
        }

        @Test
        @DisplayName("13 → multiStateInput")
        void multiStateInput() {
            assertThat(BacnetConnection.mapObjectType(13)).isEqualTo(ObjectType.multiStateInput);
        }

        @Test
        @DisplayName("19 → multiStateValue")
        void multiStateValue() {
            assertThat(BacnetConnection.mapObjectType(19)).isEqualTo(ObjectType.multiStateValue);
        }

        @Test
        @DisplayName("unknown → null")
        void unknownObjectType() {
            assertThat(BacnetConnection.mapObjectType(999)).isNull();
        }
    }

    @Nested
    @DisplayName("property identifier mapping")
    class PropertyIdentifierMapping {

        @Test
        @DisplayName("85 → presentValue")
        void presentValue() {
            assertThat(BacnetConnection.mapPropertyIdentifier(85))
                    .isEqualTo(PropertyIdentifier.presentValue);
        }

        @Test
        @DisplayName("28 → description")
        void description() {
            assertThat(BacnetConnection.mapPropertyIdentifier(28))
                    .isEqualTo(PropertyIdentifier.description);
        }

        @Test
        @DisplayName("117 → units")
        void units() {
            assertThat(BacnetConnection.mapPropertyIdentifier(117))
                    .isEqualTo(PropertyIdentifier.units);
        }

        @Test
        @DisplayName("77 → objectName")
        void objectName() {
            assertThat(BacnetConnection.mapPropertyIdentifier(77))
                    .isEqualTo(PropertyIdentifier.objectName);
        }

        @Test
        @DisplayName("111 → statusFlags")
        void statusFlags() {
            assertThat(BacnetConnection.mapPropertyIdentifier(111))
                    .isEqualTo(PropertyIdentifier.statusFlags);
        }

        @Test
        @DisplayName("unknown → null")
        void unknownProperty() {
            assertThat(BacnetConnection.mapPropertyIdentifier(999)).isNull();
        }
    }

    @Nested
    @DisplayName("encodable conversion")
    class EncodableConversion {

        @Test
        @DisplayName("null → null")
        void nullToNull() {
            assertThat(BacnetConnection.encodableToJava(null)).isNull();
        }
    }

    @Nested
    @DisplayName("connection lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("not connected before open")
        void notConnectedBeforeOpen() {
            BacnetConnection conn = new BacnetConnection(service);
            assertThat(conn.isConnected()).isFalse();
        }

        @Test
        @DisplayName("close is safe when not open")
        void closeSafeWhenNotOpen() {
            BacnetConnection conn = new BacnetConnection(service);
            assertThatCode(() -> conn.close()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIdempotent() {
            BacnetConnection conn = new BacnetConnection(service);
            conn.close();
            assertThatCode(() -> conn.close()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("read throws when not open")
        void readThrowsWhenNotOpen() {
            BacnetConnection conn = new BacnetConnection(service);
            assertThatThrownBy(() -> conn.read(0, 0, 85))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not open");
        }

        @Test
        @DisplayName("start and stop service does not throw")
        void startStopService() throws Exception {
            service.setRemoteHost("192.168.1.1");
            service.start();
            service.stop();
        }
    }
}
