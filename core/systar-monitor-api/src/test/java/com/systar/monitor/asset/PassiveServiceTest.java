package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.ServiceType;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class PassiveServiceTest {

    private PassiveService service;
    private ProbeType probeType;

    /** Concrete subclass for testing. */
    static class TestPassiveService extends PassiveService {
        @Override
        public void start() { /* no-op */ }

        @Override
        public void stop() { /* no-op */ }
    }

    @BeforeEach
    void setUp() {
        service = new TestPassiveService();
        service.init(new ServiceType("svcType"), 1, "passiveSvc");
        probeType = new ProbeType("pt");
    }

    @Test
    @DisplayName("PassiveService mode is PASSIVE")
    void modeIsPassive() {
        assertThat(service.getMode()).isEqualTo(MonitorMode.PASSIVE);
    }

    @Test
    @DisplayName("registerMonitor and getMonitor work")
    void registerAndGet() {
        Probe probe = new Probe();
        probe.init(probeType, 10, "probe1");

        service.registerMonitor("key1", probe);
        assertThat(service.getMonitor("key1")).isSameAs(probe);
    }

    @Test
    @DisplayName("registerMonitor ignores null key")
    void registerNullKey() {
        Probe probe = new Probe();
        probe.init(probeType, 10, "probe1");
        service.registerMonitor(null, probe);
        // Monitor should not be registered with any key
        assertThat(service.getKey(10)).isNull();
    }

    @Test
    @DisplayName("registerMonitor ignores null monitor")
    void registerNullMonitor() {
        service.registerMonitor("key1", null);
        assertThat(service.getMonitor("key1")).isNull();
    }

    @Test
    @DisplayName("registerMonitor rejects different monitor for same key")
    void registerDuplicateKey() {
        Probe probe1 = new Probe();
        probe1.init(probeType, 10, "probe1");
        Probe probe2 = new Probe();
        probe2.init(probeType, 11, "probe2");

        service.registerMonitor("key1", probe1);
        assertThatThrownBy(() -> service.registerMonitor("key1", probe2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("registerMonitor allows same monitor re-registration")
    void registerSameMonitor() {
        Probe probe = new Probe();
        probe.init(probeType, 10, "probe1");
        service.registerMonitor("key1", probe);
        // Re-register same monitor - no exception
        assertThatNoException().isThrownBy(() -> service.registerMonitor("key1", probe));
    }

    @Test
    @DisplayName("unregisterMonitor removes entry")
    void unregister() {
        Probe probe = new Probe();
        probe.init(probeType, 10, "probe1");
        service.registerMonitor("key1", probe);

        Monitor<?> removed = service.unregisterMonitor("key1");
        assertThat(removed).isSameAs(probe);
        assertThat(service.getMonitor("key1")).isNull();
        assertThat(service.getKey(10)).isNull();
    }

    @Test
    @DisplayName("unregisterMonitor with null key returns null")
    void unregisterNullKey() {
        assertThat(service.unregisterMonitor(null)).isNull();
    }

    @Test
    @DisplayName("unregisterMonitor with unknown key returns null")
    void unregisterUnknownKey() {
        assertThat(service.unregisterMonitor("unknown")).isNull();
    }

    @Test
    @DisplayName("getKey returns register key for monitor id")
    void getKey() {
        Probe probe = new Probe();
        probe.init(probeType, 10, "probe1");
        service.registerMonitor("key1", probe);

        assertThat(service.getKey(10)).isEqualTo("key1");
    }

    @Test
    @DisplayName("getKey returns null for unknown monitor id")
    void getKeyUnknown() {
        assertThat(service.getKey(999)).isNull();
    }

    @Test
    @DisplayName("resultDispatcher getter/setter")
    void resultDispatcher() {
        ResultDispatcher dispatcher = mock(ResultDispatcher.class);
        service.setResultDispatcher(dispatcher);
        assertThat(service.getResultDispatcher()).isSameAs(dispatcher);
    }

    @Test
    @DisplayName("kind is SERVICE")
    void kindIsService() {
        assertThat(service.getKind()).isEqualTo(AssetKind.SERVICE);
    }
}
