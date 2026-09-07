package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.type.AssetTypeProperty;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.asset.type.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class ModbusServiceTest {

    private ModbusService service;
    private ServiceType    type;

    /**
     * Mirrors ModbusTcpMaster as expanded by the XML loader: parent
     * ModbusMaster properties (UnitId, MaxConnections) plus the child's
     * (Host, Port) on one type.
     */
    @BeforeEach
    void setUp() {
        service = new ModbusService();
        type    = new ServiceType("ModbusTcpMaster");
        type.addProperty(new AssetTypeProperty("UnitId", DataType.INT, "1", "unit"));
        type.addProperty(new AssetTypeProperty("MaxConnections", DataType.INT, "1", "pool"));
        type.addProperty(new AssetTypeProperty("Host", DataType.STRING, "127.0.0.1", "host"));
        type.addProperty(new AssetTypeProperty("Port", DataType.INT, "502", "port"));
        service.init(type, 1, "modbus-tcp");
    }

    // ======================== bindProperties priority ========================

    @Test
    @DisplayName("Instance metadata overrides type default and Java initial value")
    void metadataWins() {
        service.setMetadata("Host", "10.20.30.40");
        service.setMetadata("Port", "1502");
        service.setMetadata("UnitId", "7");
        service.setMetadata("MaxConnections", "5");

        service.bindProperties();

        assertThat(service.getHost()).isEqualTo("10.20.30.40");
        assertThat(service.getPort()).isEqualTo(1502);
        assertThat(service.getUnitId()).isEqualTo(7);
        assertThat(service.getMaxConnections()).isEqualTo(5);
    }

    @Test
    @DisplayName("Missing metadata falls back to type property defaults")
    void typeDefaultsApply() {
        service.bindProperties();

        assertThat(service.getHost()).isEqualTo("127.0.0.1");
        assertThat(service.getPort()).isEqualTo(502);
        assertThat(service.getUnitId()).isEqualTo(1);
        assertThat(service.getMaxConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("Properties without metadata or default leave Java initial values (timeout)")
    void javaInitialValueUntouched() {
        service.bindProperties();

        // Timeout has no XML property — the field's initial value is the fallback.
        assertThat(service.getTimeout()).isEqualTo(5000);
    }

    @Test
    @DisplayName("MaxConnections binds via the inherited ActiveService setter")
    void maxConnectionsUsesInheritedSetter() {
        service.setMetadata("MaxConnections", "8");

        service.bindProperties();

        assertThat(service.getMaxConnections()).isEqualTo(8);
    }

    // ======================== start() uses bound fields ========================

    @Test
    @DisplayName("start() connects with the bound port — no config re-resolution")
    void startUsesBoundFields() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            service.setMetadata("Host", "127.0.0.1");
            service.setMetadata("Port", String.valueOf(server.getLocalPort()));
            service.bindProperties();
            assertThat(service.getPort()).isEqualTo(server.getLocalPort());

            // The old resolveConfig() re-resolved with lowercase keys that
            // never matched ("host"/"port"), resetting the port to 502 and
            // failing with connection refused. start() must consume the
            // fields bound at load time.
            assertThatCode(service::start).doesNotThrowAnyException();
            assertThat(service.getPort()).isEqualTo(server.getLocalPort());
        } finally {
            service.stop();
        }
    }
}
