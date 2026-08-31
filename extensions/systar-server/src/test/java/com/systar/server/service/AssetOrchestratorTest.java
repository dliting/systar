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
        @DisplayName("creates space and publishes CREATED event")
        void createSpace() {
            when(repo.nextId(AssetKind.SPACE)).thenReturn(10);
            Space created = new Space();
            created.init(new SpaceType("building"), 10, "b1");
            when(repo.findSpaceById(10)).thenReturn(created);

            var req = new AssetCreateRequest("SPACE", 0, "b1", "Building",
                    null, Map.of("sequence", 1, "showInClient", 1), Map.of());
            int id = orchestrator.createAsset(req);

            assertThat(id).isEqualTo(10);
            verify(repo).insertSpace(any(AssetRepository.SpaceRow.class));
            verify(repo).insertAssetView(eq("b1"), eq("Building"), eq(AssetKind.SPACE), eq(0), eq(10));

            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.CREATED);
            assertThat(event.assetId()).isEqualTo(10);
        }

        @Test
        @DisplayName("creates space without NPE when int properties are null")
        void createSpaceNullIntProps() {
            when(repo.nextId(AssetKind.SPACE)).thenReturn(11);
            Space created = new Space();
            created.init(new SpaceType("building"), 11, "b2");
            when(repo.findSpaceById(11)).thenReturn(created);

            var req = new AssetCreateRequest("SPACE", 0, "b2", "Building 2",
                    null, Map.of(), Map.of());
            assertThatCode(() -> orchestrator.createAsset(req)).doesNotThrowAnyException();
            verify(repo).insertSpace(any(AssetRepository.SpaceRow.class));
        }

        @Test
        @DisplayName("creates device and publishes CREATED event")
        void createDevice() {
            when(repo.nextId(AssetKind.DEVICE)).thenReturn(20);
            Device created = new Device();
            created.init(new DeviceType("meter"), 20, "d1");
            when(repo.findDeviceById(20)).thenReturn(created);

            var req = new AssetCreateRequest("DEVICE", 0, "d1", "Meter",
                    null, Map.of(), Map.of());
            int id = orchestrator.createAsset(req);

            assertThat(id).isEqualTo(20);
            verify(repo).insertDevice(any(AssetRepository.DeviceRow.class));
            verify(repo).insertAssetView(eq("d1"), eq("Meter"), eq(AssetKind.DEVICE), eq(0), eq(20));
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
            int id = orchestrator.createAsset(req);

            assertThat(id).isEqualTo(30);
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
            int id = orchestrator.createAsset(req);

            assertThat(id).isEqualTo(40);
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
            int id = orchestrator.createAsset(req);

            assertThat(id).isEqualTo(50);
            verify(repo).insertService(any(AssetRepository.ServiceRow.class));
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
            Space space = new Space();
            space.init(new SpaceType("room"), 50, "room1");
            store.addAsset(space);

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
        @DisplayName("updates space and publishes UPDATED event")
        void updateSpace() {
            Space space = new Space();
            space.init(new SpaceType("building"), 1, "b1");
            space.setCaption("Old Caption");
            when(repo.findSpaceById(1)).thenReturn(space);

            var req = new AssetUpdateRequest("b1-new", "New Caption", null,
                    Map.of("area", 100), null);
            orchestrator.updateAsset(1, AssetKind.SPACE, req);

            verify(repo).updateSpace(eq(1), any(AssetRepository.SpaceUpdateFields.class));
            verify(repo).updateAssetView(eq(1), eq(AssetKind.SPACE), eq("b1"), eq("Old Caption"));

            AssetChangedEvent event = captureEvent();
            assertThat(event.action()).isEqualTo(Action.UPDATED);
        }
    }

    // ======================== Delete ========================

    @Nested
    @DisplayName("Delete asset")
    class DeleteAsset {

        @Test
        @DisplayName("deletes space with no constraints, calls repo and publishes event")
        void deleteSpaceNoConstraints() {
            Space space = new Space();
            space.init(new SpaceType("building"), 1, "space1");
            store.addAsset(space);

            when(repo.countAlarmRulesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(1)).thenReturn(0L);

            orchestrator.deleteAsset(1, AssetKind.SPACE);

            verify(repo).deleteSpace(1);
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
            Space parent = new Space();
            parent.init(new SpaceType("floor"), 1, "floor1");
            Space child = new Space();
            child.init(new SpaceType("room"), 2, "room1");
            store.addAsset(parent);
            parent.addChild(child);

            when(repo.countAlarmRulesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageCausesForMonitor(1)).thenReturn(0L);
            when(repo.countLinkageEffectsForMonitor(1)).thenReturn(0L);

            assertThatThrownBy(() -> orchestrator.deleteAsset(1, AssetKind.SPACE))
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
            Space space = new Space();
            space.init(new SpaceType("room"), 1, "room1");
            store.addAsset(space);

            assertThatThrownBy(() -> orchestrator.startAsset(1, AssetKind.SPACE))
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
            Space space = new Space();
            space.init(new SpaceType("room"), 3, "room1");
            store.addAsset(space);

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
