package com.systar.server.service;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.server.dto.AssetCreateRequest;
import com.systar.server.dto.AssetUpdateRequest;
import com.systar.server.dto.BatchResult;
import com.systar.server.event.AssetChangedEvent;
import com.systar.server.event.AssetChangedEvent.Action;
import com.systar.server.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetOrchestratorTest {

    private AssetRepository repo;
    private AssetStore store;
    private ApplicationEventPublisher events;
    private AssetOrchestrator orchestrator;
    private ArgumentCaptor<Object> eventCaptor;

    @BeforeEach
    void setUp() {
        repo = mock(AssetRepository.class);
        store = new AssetStore();
        events = mock(ApplicationEventPublisher.class);
        orchestrator = new AssetOrchestrator(repo, store, events);
        eventCaptor = ArgumentCaptor.forClass(Object.class);
    }

    private AssetChangedEvent captureEvent() {
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(AssetChangedEvent.class);
        return (AssetChangedEvent) eventCaptor.getValue();
    }

    // ======================== Create ========================

    @Nested
    @DisplayName("Create asset")
    class CreateAsset {

        @Test
        @DisplayName("creates device and publishes CREATED event")
        void createDevice() {
            when(repo.nextId(AssetKind.DEVICE)).thenReturn(20);
            Device created = new Device();
            created.init(new DeviceType("meter"), 20, "d1");
            when(repo.findDeviceById(20)).thenReturn(created);
            // The view row gets an AUTO_INCREMENT id unrelated to the runtime id.
            when(repo.insertAssetView(eq("d1"), eq("Meter"), eq(AssetKind.DEVICE), eq(0), eq(20)))
                    .thenReturn(122L);

            var req = new AssetCreateRequest("DEVICE", 0, "d1", "Meter",
                    null, Map.of(), Map.of());
            AssetOrchestrator.CreateResult result = orchestrator.createAsset(req);

            assertThat(result.runtimeId()).isEqualTo(20);
            assertThat(result.assetRowId()).isEqualTo(122L);
            ArgumentCaptor<AssetRepository.DeviceRow> row =
                    ArgumentCaptor.forClass(AssetRepository.DeviceRow.class);
            verify(repo).insertDevice(row.capture());
            verify(repo).insertAssetView(eq("d1"), eq("Meter"), eq(AssetKind.DEVICE), eq(0), eq(20));
        }

        @Test
        @DisplayName("rejects a device create request that carries a parent")
        void createDeviceRejectsParent() {
            var req = new AssetCreateRequest("DEVICE", 5, "d1", "Meter",
                    null, Map.of(), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("top-level")
                    .hasMessageContaining("parentId must be 0");
        }

        @Test
        @DisplayName("rejects a device create request with a negative parent id (not silently coerced)")
        void createDeviceRejectsNegativeParent() {
            var req = new AssetCreateRequest("DEVICE", -5, "d1", "Meter",
                    null, Map.of(), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("top-level");
        }

        @Test
        @DisplayName("rejects a service create request that carries a parent")
        void createServiceRejectsParent() {
            store.getServiceTypes().register(new ServiceType("ModbusTcpMaster"));

            var req = new AssetCreateRequest("SERVICE", 5, "svc1", "Modbus",
                    "ModbusTcpMaster", Map.of("mode", "ACTIVE"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("top-level")
                    .hasMessageContaining("parentId must be 0");
        }

        @Test
        @DisplayName("creates probe and publishes CREATED event")
        void createProbe() {
            when(repo.nextId(AssetKind.PROBE)).thenReturn(30);
            Probe created = new Probe();
            created.init(new ProbeType("voltage"), 30, "p1");
            when(repo.findProbeById(30)).thenReturn(created);

            var req = new AssetCreateRequest("PROBE", 0, "p1", "Voltage",
                    null, Map.of("unit", "V"), Map.of());
            AssetOrchestrator.CreateResult result = orchestrator.createAsset(req);

            assertThat(result.runtimeId()).isEqualTo(30);
            verify(repo).insertProbe(any(AssetRepository.ProbeRow.class));
        }

        @Test
        @DisplayName("creates control and publishes CREATED event")
        void createControl() {
            when(repo.nextId(AssetKind.CONTROL)).thenReturn(40);
            Control created = new Control() { @Override public void execute(String c) {} };
            created.init(new ControlType("switch"), 40, "c1");
            when(repo.findControlById(40)).thenReturn(created);

            var req = new AssetCreateRequest("CONTROL", 0, "c1", "Switch",
                    null, Map.of(), Map.of());
            AssetOrchestrator.CreateResult result = orchestrator.createAsset(req);

            assertThat(result.runtimeId()).isEqualTo(40);
            verify(repo).insertControl(any(AssetRepository.ControlRow.class));
        }

        @Test
        @DisplayName("creates service and publishes CREATED event")
        void createService() {
            store.getServiceTypes().register(new ServiceType("ModbusTcpMaster"));

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(50);
            MonitorService created = new ActiveService() {
                @Override public void start() {}
                @Override public void stop() {}
                @Override public MonitorConnection createConnection() { return null; }
            };
            created.init(new ServiceType("ModbusTcpMaster"), 50, "svc1");
            when(repo.findServiceById(50)).thenReturn(created);

            var req = new AssetCreateRequest("SERVICE", 0, "svc1", "Modbus",
                    "ModbusTcpMaster", Map.of("mode", "ACTIVE"), Map.of());
            AssetOrchestrator.CreateResult result = orchestrator.createAsset(req);

            assertThat(result.runtimeId()).isEqualTo(50);
            ArgumentCaptor<AssetRepository.ServiceRow> row =
                    ArgumentCaptor.forClass(AssetRepository.ServiceRow.class);
            verify(repo).insertService(row.capture());
        }

        @Test
        @DisplayName("derives service mode from the type's driver class when request omits mode")
        void createServiceDerivesModeFromDriverClass() {
            ServiceType typed = new ServiceType("MySimulate");
            typed.setRelatedClass("com.systar.monitor.drivers.simulate.SimulateService");
            store.getServiceTypes().register(typed);

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(70);
            MonitorService created = new ActiveService() {
                @Override public void start() {}
                @Override public void stop() {}
                @Override public MonitorConnection createConnection() { return null; }
            };
            created.init(typed, 70, "svc2");
            when(repo.findServiceById(70)).thenReturn(created);

            var req = new AssetCreateRequest("SERVICE", 0, "svc2", "Simulate",
                    "MySimulate", Map.of(), Map.of());
            orchestrator.createAsset(req);

            ArgumentCaptor<AssetRepository.ServiceRow> row =
                    ArgumentCaptor.forClass(AssetRepository.ServiceRow.class);
            verify(repo).insertService(row.capture());
            assertThat(row.getValue().mode())
                    .as("mode should default to the driver class's own mode (ACTIVE)")
                    .isEqualTo(MonitorMode.ACTIVE.getCode());
        }

        @Test
        @DisplayName("derives mode from request driverClass when the type has no JavaClass")
        void createServiceDerivesModeFromRequestDriverClass() {
            store.getServiceTypes().register(new ServiceType("DriverlessService"));

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(71);
            MonitorService created = new ActiveService() {
                @Override public void start() {}
                @Override public void stop() {}
                @Override public MonitorConnection createConnection() { return null; }
            };
            created.init(new ServiceType("DriverlessService"), 71, "svc3");
            when(repo.findServiceById(71)).thenReturn(created);

            var req = new AssetCreateRequest("SERVICE", 0, "svc3", "Driverless",
                    "DriverlessService",
                    Map.of("driverClass", "com.systar.monitor.drivers.simulate.SimulateService"),
                    Map.of());
            orchestrator.createAsset(req);

            ArgumentCaptor<AssetRepository.ServiceRow> row =
                    ArgumentCaptor.forClass(AssetRepository.ServiceRow.class);
            verify(repo).insertService(row.capture());
            assertThat(row.getValue().mode())
                    .as("mode should fall back to the request driverClass's mode")
                    .isEqualTo(MonitorMode.ACTIVE.getCode());
        }

        @Test
        @DisplayName("type JavaClass wins over request driverClass (repository precedence)")
        void createServiceTypeClassBeatsRequestDriverClass() {
            ServiceType typed = new ServiceType("TypedService");
            typed.setRelatedClass("com.systar.monitor.drivers.simulate.SimulateService"); // ACTIVE
            store.getServiceTypes().register(typed);

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(75);
            MonitorService created = new ActiveService() {
                @Override public void start() {}
                @Override public void stop() {}
                @Override public MonitorConnection createConnection() { return null; }
            };
            created.init(typed, 75, "svc7");
            when(repo.findServiceById(75)).thenReturn(created);

            var req = new AssetCreateRequest("SERVICE", 0, "svc7", "Typed",
                    "TypedService",
                    Map.of("driverClass", "com.systar.monitor.drivers.mqtt.MqttService"), // PASSIVE
                    Map.of());
            orchestrator.createAsset(req);

            ArgumentCaptor<AssetRepository.ServiceRow> row =
                    ArgumentCaptor.forClass(AssetRepository.ServiceRow.class);
            verify(repo).insertService(row.capture());
            assertThat(row.getValue().mode())
                    .as("type's JavaClass must take precedence over request driverClass")
                    .isEqualTo(MonitorMode.ACTIVE.getCode());
        }

        @Test
        @DisplayName("driverless type without mode or driverClass keeps null mode")
        void createServiceDriverlessWithoutModeStaysNull() {
            store.getServiceTypes().register(new ServiceType("DriverlessService"));

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(72);
            MonitorService created = new ActiveService() {
                @Override public void start() {}
                @Override public void stop() {}
                @Override public MonitorConnection createConnection() { return null; }
            };
            created.init(new ServiceType("DriverlessService"), 72, "svc4");
            when(repo.findServiceById(72)).thenReturn(created);

            var req = new AssetCreateRequest("SERVICE", 0, "svc4", "Driverless",
                    "DriverlessService", Map.of(), Map.of());
            orchestrator.createAsset(req);

            ArgumentCaptor<AssetRepository.ServiceRow> row =
                    ArgumentCaptor.forClass(AssetRepository.ServiceRow.class);
            verify(repo).insertService(row.capture());
            assertThat(row.getValue().mode())
                    .as("driverless service keeps null mode (explicit mode stays mandatory)")
                    .isNull();
        }

        @Test
        @DisplayName("misconfigured JavaClass (not a MonitorService) yields AssetException, not raw CCE")
        void createServiceBadDriverClassWrapsException() {
            ServiceType bad = new ServiceType("BadService");
            bad.setRelatedClass("com.systar.monitor.drivers.simulate.SimulateProbe");
            store.getServiceTypes().register(bad);

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(73);

            var req = new AssetCreateRequest("SERVICE", 0, "svc5", "Bad",
                    "BadService", Map.of(), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("SimulateProbe")
                    .hasMessageContaining("svc5");
        }

        @Test
        @DisplayName("invalid mode string yields AssetException naming the value")
        void createServiceInvalidModeString() {
            store.getServiceTypes().register(new ServiceType("AnyService"));

            when(repo.nextId(AssetKind.SERVICE)).thenReturn(74);

            var req = new AssetCreateRequest("SERVICE", 0, "svc6", "Any",
                    "AnyService", Map.of("mode", "SOMETIMES"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("SOMETIMES");
        }

        @Test
        @DisplayName("throws on invalid kind")
        void invalidKind() {
            var req = new AssetCreateRequest("INVALID", 0, "x", "X",
                    null, Map.of(), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("Invalid asset kind");
        }

        @Test
        @DisplayName("throws when VirtualProbe has no expression")
        void virtualProbeNoExpression() {
            when(repo.nextId(AssetKind.PROBE)).thenReturn(60);
            var req = new AssetCreateRequest("PROBE", 0, "vp1", "Virtual",
                    null, Map.of("isVirtual", 1, "dependsOn", "101"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("requires a non-blank expression");
        }

        @Test
        @DisplayName("throws when VirtualProbe has no dependsOn")
        void virtualProbeNoDependsOn() {
            when(repo.nextId(AssetKind.PROBE)).thenReturn(60);
            var req = new AssetCreateRequest("PROBE", 0, "vp1", "Virtual",
                    null, Map.of("isVirtual", 1, "expression", "#probe[101].value * 2"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("requires at least one dependency");
        }

        @Test
        @DisplayName("throws when VirtualProbe dependsOn has non-numeric IDs")
        void virtualProbeInvalidDependsOn() {
            when(repo.nextId(AssetKind.PROBE)).thenReturn(60);
            var req = new AssetCreateRequest("PROBE", 0, "vp1", "Virtual",
                    null, Map.of("isVirtual", 1, "expression", "#probe[101].value", "dependsOn", "abc"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("Invalid probe ID in dependsOn");
        }

        @Test
        @DisplayName("throws when VirtualProbe dependsOn references non-existent probe")
        void virtualProbeDependsOnNotFound() {
            when(repo.nextId(AssetKind.PROBE)).thenReturn(60);
            var req = new AssetCreateRequest("PROBE", 0, "vp1", "Virtual",
                    null, Map.of("isVirtual", 1, "expression", "#probe[999].value", "dependsOn", "999"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("Dependency probe not found");
        }

        @Test
        @DisplayName("throws when VirtualProbe dependsOn references non-probe asset")
        void virtualProbeDependsOnNonProbe() {
            Device device = new Device();
            device.init(new DeviceType("room"), 50, "dev1");
            store.addAsset(device);

            when(repo.nextId(AssetKind.PROBE)).thenReturn(60);
            var req = new AssetCreateRequest("PROBE", 0, "vp1", "Virtual",
                    null, Map.of("isVirtual", 1, "expression", "#probe[50].value", "dependsOn", "50"), Map.of());
            assertThatThrownBy(() -> orchestrator.createAsset(req))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("Dependency must be a probe");
        }
    }

    // ======================== Update ========================

    @Nested
    @DisplayName("Update asset")
    class UpdateAsset {

        @Test
        @DisplayName("updates device and publishes UPDATED event")
        void updateDevice() {
            Device device = new Device();
            device.init(new DeviceType("meter"), 1, "d1");
            device.setCaption("Old Caption");
            when(repo.findDeviceById(1)).thenReturn(device);

            var req = new AssetUpdateRequest("d1-new", "New Caption", null,
                    Map.of("vendor", "ACME"), null);
            orchestrator.updateAsset(1, AssetKind.DEVICE, req);

            verify(repo).updateDevice(eq(1), any(AssetRepository.DeviceUpdateFields.class));
            verify(repo).updateAssetView(eq(1), eq(AssetKind.DEVICE), eq("d1"), eq("Old Caption"));

            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.UPDATED);
        }
    }

    // ======================== Delete ========================

    @Nested
    @DisplayName("Delete asset")
    class DeleteAsset {

        @Test
        @DisplayName("deletes device with no constraints, calls repo and publishes event")
        void deleteDeviceNoConstraints() {
            Device device = new Device();
            device.init(new DeviceType("meter"), 1, "device1");
            store.addAsset(device);

            when(repo.countAlarmRulesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(1)).thenReturn(0L);

            orchestrator.deleteAsset(1, AssetKind.DEVICE);

            verify(repo).deleteDevice(1);
            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.DELETED);
            assertThat(event.assetId()).isEqualTo(1);
        }

        @Test
        @DisplayName("throws when alarm rules reference the asset")
        void alarmRuleConstraint() {
            Probe probe = new Probe();
            probe.init(new ProbeType("voltage"), 10, "probe1");
            store.addAsset(probe);

            when(repo.countAlarmRulesForMonitor(10)).thenReturn(3L);
            when(repo.countLinkageCausesForMonitor(10)).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(10)).thenReturn(0L);

            assertThatThrownBy(() -> orchestrator.deleteAsset(10, AssetKind.PROBE))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("alarm rule");
            verify(repo, never()).deleteProbe(anyInt());
        }

        @Test
        @DisplayName("throws when linkage cause rules reference the asset")
        void linkageCauseConstraint() {
            Probe probe = new Probe();
            probe.init(new ProbeType("voltage"), 20, "probe2");
            store.addAsset(probe);

            when(repo.countAlarmRulesForMonitor(20)).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(20)).thenReturn(1L);
            when(repo.countLinkageEffectsForMonitor(20)).thenReturn(0L);

            assertThatThrownBy(() -> orchestrator.deleteAsset(20, AssetKind.PROBE))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("linkage cause");
        }

        @Test
        @DisplayName("throws when linkage effect rules reference the asset")
        void linkageEffectConstraint() {
            Control control = new Control() {
                @Override public void execute(String command) {}
            };
            control.init(new ControlType("switch"), 30, "ctrl1");
            store.addAsset(control);

            when(repo.countAlarmRulesForMonitor(30)).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(30)).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(30)).thenReturn(2L);

            assertThatThrownBy(() -> orchestrator.deleteAsset(30, AssetKind.CONTROL))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("linkage effect");
        }

        @Test
        @DisplayName("throws when asset has children")
        void childrenConstraint() {
            Device parent = new Device();
            parent.init(new DeviceType("floor"), 1, "floor1");
            Device child = new Device();
            child.init(new DeviceType("room"), 2, "room1");
            store.addAsset(parent);
            parent.addChild(child);

            when(repo.countAlarmRulesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(1)).thenReturn(0L);

            assertThatThrownBy(() -> orchestrator.deleteAsset(1, AssetKind.DEVICE))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("children");
        }
    }

    // ======================== Enable / Disable ========================

    @Nested
    @DisplayName("Enable/disable asset")
    class EnableDisable {

        @Test
        @DisplayName("enable calls repo and publishes ENABLED event")
        void enable() {
            orchestrator.enableAsset(5, AssetKind.PROBE);

            verify(repo).setEnabled(AssetKind.PROBE, 5, true);
            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.ENABLED);
        }

        @Test
        @DisplayName("disable calls repo and publishes DISABLED event")
        void disable() {
            orchestrator.disableAsset(5, AssetKind.PROBE);

            verify(repo).setEnabled(AssetKind.PROBE, 5, false);
            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.DISABLED);
        }
    }

    // ======================== Start / Stop ========================

    @Nested
    @DisplayName("Start/stop validation")
    class StartStopValidation {

        @Test
        @DisplayName("start throws for non-monitor asset")
        void startNonMonitor() {
            Device device = new Device();
            device.init(new DeviceType("room"), 1, "dev1");
            store.addAsset(device);

            assertThatThrownBy(() -> orchestrator.startAsset(1, AssetKind.DEVICE))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not a monitor");
        }

        @Test
        @DisplayName("stop throws for non-existent asset")
        void stopNonExistent() {
            assertThatThrownBy(() -> orchestrator.stopAsset(999, AssetKind.PROBE))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("start succeeds for probe and publishes STARTED event")
        void startProbePublishesEvent() {
            Probe probe = new Probe();
            probe.init(new ProbeType("voltage"), 5, "v1");
            store.addAsset(probe);

            assertThatCode(() -> orchestrator.startAsset(5, AssetKind.PROBE))
                    .doesNotThrowAnyException();
            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.STARTED);
        }
    }

    // ======================== Batch Operations ========================

    @Nested
    @DisplayName("Batch operations")
    class BatchOperations {

        @BeforeEach
        void addAssets() {
            Probe p1 = new Probe();
            p1.init(new ProbeType("v1"), 1, "probe1");
            Probe p2 = new Probe();
            p2.init(new ProbeType("v2"), 2, "probe2");
            store.addAsset(p1);
            store.addAsset(p2);

            when(repo.countAlarmRulesForMonitor(anyInt())).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(anyInt())).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(anyInt())).thenReturn(0L);
        }

        @Test
        @DisplayName("batchStart returns success for valid probes")
        void batchStartSuccess() {
            BatchResult result = orchestrator.batchStart(List.of(1, 2));
            assertThat(result.getSuccess()).containsExactly(1, 2);
            assertThat(result.getFailed()).isEmpty();
        }

        @Test
        @DisplayName("batchStart reports failure for non-existent and non-monitor")
        void batchStartMixed() {
            Device device = new Device();
            device.init(new DeviceType("room"), 3, "dev1");
            store.addAsset(device);

            BatchResult result = orchestrator.batchStart(List.of(1, 99, 3));
            assertThat(result.getSuccess()).containsExactly(1);
            assertThat(result.getFailed()).containsKey(99);
            assertThat(result.getFailed()).containsKey(3);
        }

        @Test
        @DisplayName("batchStop returns success for valid probes")
        void batchStopSuccess() {
            BatchResult result = orchestrator.batchStop(List.of(1, 2));
            assertThat(result.getSuccess()).containsExactly(1, 2);
        }

        @Test
        @DisplayName("batchEnable calls repo for each id")
        void batchEnable() {
            BatchResult result = orchestrator.batchEnable(List.of(1, 2));
            assertThat(result.getSuccess()).containsExactly(1, 2);
            verify(repo).setEnabled(AssetKind.PROBE, 1, true);
            verify(repo).setEnabled(AssetKind.PROBE, 2, true);
        }

        @Test
        @DisplayName("batchDisable calls repo for each id")
        void batchDisable() {
            BatchResult result = orchestrator.batchDisable(List.of(1, 2));
            assertThat(result.getSuccess()).containsExactly(1, 2);
            verify(repo).setEnabled(AssetKind.PROBE, 1, false);
            verify(repo).setEnabled(AssetKind.PROBE, 2, false);
        }

        @Test
        @DisplayName("batchDelete deletes valid and reports failures")
        void batchDeleteMixed() {
            BatchResult result = orchestrator.batchDelete(List.of(1, 99));
            assertThat(result.getSuccess()).containsExactly(1);
            assertThat(result.getFailed()).containsKey(99);
            verify(repo).deleteProbe(1);
            verify(repo, never()).deleteProbe(99);
        }
    }
}
