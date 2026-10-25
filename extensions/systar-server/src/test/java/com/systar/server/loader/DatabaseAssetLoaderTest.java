package com.systar.server.loader;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.server.config.DatabaseInitializer;
import com.systar.server.plugin.PluginSeedExecutor;
import com.systar.server.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DatabaseAssetLoaderTest {

    private AssetRepository repo;
    private DatabaseAssetLoader loader;
    private AssetStore store;

    @BeforeEach
    void setUp() {
        repo = mock(AssetRepository.class);
        store = new AssetStore();
        VirtualProbeEngine engine = mock(VirtualProbeEngine.class);
        PluginSeedExecutor seedExecutor = mock(PluginSeedExecutor.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatabaseInitializer> dbInit = mock(ObjectProvider.class);
        loader = new DatabaseAssetLoader(repo, engine, seedExecutor, dbInit);

        when(repo.findAllDeviceRows()).thenReturn(List.of());
        when(repo.findAllServiceRows()).thenReturn(List.of());
        when(repo.findAllProbeRows()).thenReturn(List.of());
        when(repo.findAllControlRows()).thenReturn(List.of());
    }

    @Test
    @DisplayName("Loads devices into asset store")
    void loadsDevices() {
        Device device = createTestDevice(1, "floor1", "First Floor");
        var row = deviceRow(1, "floor1", "First Floor");
        when(repo.findAllDeviceRows()).thenReturn(List.of(row));
        when(repo.assembleDevice(eq(row))).thenReturn(device);

        loader.load(store);

        Asset<?> asset = store.findAsset(1);
        assertThat(asset).isNotNull();
        assertThat(asset.getName()).isEqualTo("floor1");
        assertThat(asset.getCaption()).isEqualTo("First Floor");
        assertThat(asset.getKind()).isEqualTo(AssetKind.DEVICE);
    }

    @Test
    @DisplayName("Loads probes with source service binding")
    void loadsProbesWithSource() {
        var svc = service(100, "modbus-svc");
        var svcRow = new AssetRepository.ServiceRow(100, "modbus-svc", null, 0, null, 5, "test-svc");

        Probe probe = new Probe();
        probe.init(new ProbeType("test-probe"), 200, "temp1");
        probe.setCaption("Temperature");
        probe.setSource(svc);
        var probeRow = new AssetRepository.ProbeRow(200, "temp1", "Temperature", 0, 100, null,
                "1s", "1m", null, null, (short) 0, "FLOAT", null, null, "test-probe",
                0, null, null);

        when(repo.findAllServiceRows()).thenReturn(List.of(svcRow));
        when(repo.assembleService(eq(svcRow), any())).thenReturn(svc);
        when(repo.findAllProbeRows()).thenReturn(List.of(probeRow));
        when(repo.assembleProbe(eq(probeRow), any())).thenReturn(probe);

        loader.load(store);

        Asset<?> asset = store.findAsset(200);
        assertThat(asset).isNotNull();
        assertThat(asset.getKind()).isEqualTo(AssetKind.PROBE);
        assertThat(asset.getName()).isEqualTo("temp1");
        assertThat(asset).isInstanceOf(Probe.class);
    }

    @Test
    @DisplayName("Empty database produces an empty store (anchor is not an asset)")
    void emptyDb() {
        loader.load(store);
        assertThat(store.getAssets()).isEmpty();
        assertThat(store.getRoot()).isNotNull();
    }

    @Test
    @DisplayName("Tree hierarchy: Device top-level, probe attached to device")
    void buildsTreeHierarchy() {
        Device device = createTestDevice(10, "deviceA", null);
        var deviceRow = deviceRow(10, "deviceA", null);

        var svc = service(100, "svc");
        var svcRow = new AssetRepository.ServiceRow(100, "svc", null, 0, null, 5, "test-svc");

        Probe probe = new Probe();
        probe.init(new ProbeType("test-probe"), 200, "temp1");
        probe.setParentId(10);
        probe.setSource(svc);
        var probeRow = new AssetRepository.ProbeRow(200, "temp1", null, 10, 100, null,
                "1s", "1m", null, null, (short) 0, "FLOAT", null, null, "test-probe",
                0, null, null);

        when(repo.findAllDeviceRows()).thenReturn(List.of(deviceRow));
        when(repo.assembleDevice(eq(deviceRow))).thenReturn(device);
        when(repo.findAllServiceRows()).thenReturn(List.of(svcRow));
        when(repo.assembleService(eq(svcRow), any())).thenReturn(svc);
        when(repo.findAllProbeRows()).thenReturn(List.of(probeRow));
        when(repo.assembleProbe(eq(probeRow), any())).thenReturn(probe);

        loader.load(store);

        Asset<?> found = store.findAsset(200);
        assertThat(found).isNotNull();
        assertThat(found.getParent()).isNotNull();
        assertThat(found.getParent().getId()).isEqualTo(10);
    }

    @Test
    @DisplayName("Loads attributes after asset loading")
    void loadsAttributes() {
        var row = deviceRow(1, "floor1", null);
        when(repo.findAllDeviceRows()).thenReturn(List.of(row));
        when(repo.assembleDevice(eq(row))).thenReturn(createTestDevice(1, "floor1", null));
        loader.load(store);
        verify(repo).loadAllAttributes(any());
    }

    @Test
    @DisplayName("End-to-end: metadata loaded by repo flows into driver setter via bindProperties")
    void bindsDriverFieldsAfterAttributeLoad() {
        // Build a Probe whose ProbeType declares an Address property (INT).
        ProbeType probeType = new ProbeType("temp-probe");
        probeType.addProperty(new AssetTypeProperty("Address", DataType.INT, "0", "addr"));

        BindingProbe probe = new BindingProbe();
        probe.init(probeType, 300, "p1");

        var probeRow = new AssetRepository.ProbeRow(300, "p1", null, 0, null, null,
                "1s", "1m", null, null, (short) 0, "FLOAT", null, null, "temp-probe",
                0, null, null);
        when(repo.findAllProbeRows()).thenReturn(List.of(probeRow));
        when(repo.assembleProbe(eq(probeRow), any())).thenReturn(probe);
        // Simulate loadAllAttributes populating the metadata map (as it does in production
        // from t_asset_attribute rows).
        doAnswer(inv -> {
            probe.setMetadata("Address", "1234");
            return null;
        }).when(repo).loadAllAttributes(any());

        loader.load(store);

        // If DatabaseAssetLoader skipped bindProperties(), address would stay at 0.
        // This assertion guards the loader→bindProperties→setter chain.
        // (XML parsing and DB attribute loading are covered by their own tests.)
        assertThat(probe.getAddress()).isEqualTo(1234);
    }

    @Test
    @DisplayName("Service row failure WARN-skips the row; probes referencing it cascade-skip")
    void serviceRowSkippedAndProbesCascade() {
        var rowA = new AssetRepository.ServiceRow(100, "good", "Good", 0, null, 5, "SvcType");
        var rowB = new AssetRepository.ServiceRow(101, "broken", "Broken", 0, "com.example.Missing", 5, null);
        when(repo.findAllServiceRows()).thenReturn(List.of(rowA, rowB));
        var probeRow = new AssetRepository.ProbeRow(200, "p1", "P1", 0, 101, null,
                "1s", "1m", null, null, (short) 0, "FLOAT", null, null, "ProbeType",
                0, null, null);
        when(repo.findAllProbeRows()).thenReturn(List.of(probeRow));

        when(repo.assembleService(eq(rowA), any())).thenReturn(service(100, "good"));
        when(repo.assembleService(eq(rowB), any()))
                .thenThrow(new AssetException("Service 'broken' (id=101) references class 'com.example.Missing' which cannot be loaded."));
        when(repo.assembleProbe(eq(probeRow), any()))
                .thenThrow(new AssetException("Probe 'p1' (id=200) references service id 101, but no service with that id was loaded."));

        loader.load(store);

        assertThat(store.findAsset(100)).isNotNull();
        assertThat(store.findAsset(101)).isNull();
        assertThat(store.findAsset(200)).isNull();
    }

    @Test
    @DisplayName("Skipped-row WARN carries the wrapped root cause, not just the wrapper message")
    void skipWarnIncludesRootCause() {
        // Reported 2026-10-25: broken driver jars made every Modbus/Mqtt service fail
        // with "Cannot instantiate driver class" while the real reason (an ECJ
        // "Unresolved compilation problems" Error inside the driver class) stayed
        // invisible because the WARN logged only e.getMessage() without the cause.
        var row = new AssetRepository.ServiceRow(101, "broken", "Broken", 0, "com.example.Missing", 5, null);
        when(repo.findAllServiceRows()).thenReturn(List.of(row));
        when(repo.assembleService(eq(row), any())).thenThrow(new AssetException(
                new IllegalStateException("Unresolved compilation problems: The import org.slf4j.Logger cannot be resolved"),
                "Cannot instantiate driver class '%s' for service '%s'.", "com.example.Missing", "broken"));

        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DatabaseAssetLoader.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            loader.load(store);
        } finally {
            logbackLogger.detachAppender(appender);
        }

        StringBuilder logged = new StringBuilder();
        for (var event : appender.list) {
            logged.append(event.getFormattedMessage()).append('\n');
            for (var tp = event.getThrowableProxy(); tp != null; tp = tp.getCause()) {
                logged.append(tp.getClassName()).append(": ").append(tp.getMessage()).append('\n');
            }
        }
        assertThat(logged)
                .contains("Cannot instantiate driver class")
                .contains("Unresolved compilation problems");
    }

    @Test
    @DisplayName("Device row failure WARN-skips only that row")
    void deviceRowSkipped() {
        var ok = new AssetRepository.DeviceRow(1, "d1", "D1", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, "DevType");
        var bad = new AssetRepository.DeviceRow(2, "d2", "D2", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, "MissingType");
        when(repo.findAllDeviceRows()).thenReturn(List.of(ok, bad));
        when(repo.assembleDevice(eq(ok))).thenReturn(device(1, "d1"));
        when(repo.assembleDevice(eq(bad)))
                .thenThrow(new AssetException("Device 'd2' (id=2) references type 'MissingType' which is not registered."));

        loader.load(store);

        assertThat(store.findAsset(1)).isNotNull();
        assertThat(store.findAsset(2)).isNull();
    }

    /** Probe subclass with a typed setter, used to verify bindProperties wiring.
     *  Must be {@code public} so reflective {@code setter.invoke} can access it,
     *  matching the visibility of real driver subclasses (BacnetControl, etc.). */
    public static class BindingProbe extends Probe {
        private int address;
        public int  getAddress()              { return address; }
        public void setAddress(int address)   { this.address = address; }
    }

    private Device createTestDevice(int id, String name, String caption) {
        Device device = new Device();
        device.init(new DeviceType("test-device"), id, name);
        device.setParentId(Asset.INVALID_ID);
        if (caption != null) device.setCaption(caption);
        return device;
    }

    private Device device(int id, String name) {
        return createTestDevice(id, name, null);
    }

    private MonitorService service(int id, String name) {
        var svc = new com.systar.monitor.asset.ActiveService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public MonitorConnection createConnection() { return null; }
        };
        svc.init(new ServiceType("test-svc"), id, name);
        return svc;
    }

    private AssetRepository.DeviceRow deviceRow(int id, String name, String caption) {
        return new AssetRepository.DeviceRow(id, name, caption, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, "test-device");
    }
}
