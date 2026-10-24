package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ActiveServiceTest {

    private AtomicInteger connectionCount;
    private ActiveService service;

    /** Concrete test subclass. */
    class TestActiveService extends ActiveService {
        @Override
        public MonitorConnection createConnection() {
            connectionCount.incrementAndGet();
            return new TestConnection();
        }

        @Override
        public void start() { /* no-op */ }

        @Override
        public void stop() { /* no-op */ }
    }

    static class TestConnection implements MonitorConnection {
        private boolean open = false;

        @Override
        public void open() { open = true; }

        @Override
        public boolean isConnected() { return open; }

        @Override
        public void close() { open = false; }
    }

    @BeforeEach
    void setUp() {
        connectionCount = new AtomicInteger(0);
        service = new TestActiveService();
        service.init(new ServiceType("svcType"), 1, "activeSvc");
    }

    @Test
    @DisplayName("ActiveService mode is ACTIVE")
    void modeIsActive() {
        assertThat(service.getMode()).isEqualTo(MonitorMode.ACTIVE);
    }

    @Test
    @DisplayName("Default maxConnections is 10")
    void defaultMaxConnections() {
        assertThat(service.getMaxConnections()).isEqualTo(10);
    }

    @Test
    @DisplayName("setMaxConnections accepts positive value")
    void setMaxConnectionsPositive() {
        service.setMaxConnections(5);
        assertThat(service.getMaxConnections()).isEqualTo(5);
    }

    @Test
    @DisplayName("setMaxConnections clamps to 1 for non-positive")
    void setMaxConnectionsNonPositive() {
        service.setMaxConnections(0);
        assertThat(service.getMaxConnections()).isEqualTo(1);
        service.setMaxConnections(-5);
        assertThat(service.getMaxConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("getConnection creates and returns a connection")
    void getConnectionCreates() throws Exception {
        MonitorConnection conn = service.getConnection();
        assertThat(conn).isNotNull();
        assertThat(connectionCount.get()).isEqualTo(1);
        assertThat(conn.isConnected()).isTrue();
    }

    @Test
    @DisplayName("releaseConnection returns connection to pool")
    void releaseConnection() throws Exception {
        MonitorConnection conn = service.getConnection();
        service.releaseConnection(conn);
        // Getting again - the released connection is reused from free pool
        MonitorConnection conn2 = service.getConnection();
        assertThat(conn2).isNotNull();
        assertThat(conn2.isConnected()).isTrue();
    }

    @Test
    @DisplayName("releaseConnection ignores null")
    void releaseConnectionNull() {
        assertThatNoException().isThrownBy(() -> service.releaseConnection(null));
    }

    @Test
    @DisplayName("setConnectionLock rejects null")
    void setConnectionLockNull() {
        assertThatThrownBy(() -> service.setConnectionLock(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("setConnectionLock sets custom lock")
    void setConnectionLock() {
        Object lock = new Object();
        service.setConnectionLock(lock);
        assertThat(service.getConnectionLock()).isSameAs(lock);
    }

    @Test
    @DisplayName("kind is SERVICE")
    void kindIsService() {
        assertThat(service.getKind()).isEqualTo(AssetKind.SERVICE);
    }
}
