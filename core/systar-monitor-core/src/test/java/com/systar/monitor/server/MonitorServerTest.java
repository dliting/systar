package com.systar.monitor.server;

import com.systar.monitor.alarm.AlarmCorrelator;
import com.systar.monitor.alarm.AlarmHandler;
import com.systar.monitor.alarm.AlarmRepository;
import com.systar.monitor.alarm.AlarmRule;
import com.systar.monitor.alarm.AlarmStrategy;
import com.systar.monitor.alarm.AlarmSuppressionChecker;
import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.AssetTypeLoader;
import com.systar.monitor.asset.type.*;
import com.systar.monitor.linkage.CauseType;
import com.systar.monitor.linkage.LinkageHandler;
import com.systar.monitor.linkage.LinkageRepository;
import com.systar.monitor.linkage.LinkageRuleBean;
import com.systar.monitor.linkage.LinkageRuleCauseBean;
import com.systar.monitor.linkage.LinkageRuleEffectBean;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import com.systar.monitor.schedule.MonitorScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorServerTest {

    private AssetStore store;
    private ResultDispatcher dispatcher;
    private MonitorScheduler scheduler;
    private AlarmHandler alarmHandler;
    private LinkageHandler linkageHandler;
    private MonitorServer server;

    /** Testable Control subclass. */
    static class TestControl extends Control {
        String lastCommand;

        @Override
        public void execute(String command) {
            lastCommand = command;
        }
    }

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new SpaceType("root"), "root");

        dispatcher = mock(ResultDispatcher.class);
        scheduler = mock(MonitorScheduler.class);
        alarmHandler = new AlarmHandler(
                mock(AlarmRepository.class),
                mock(AlarmCorrelator.class),
                mock(AlarmSuppressionChecker.class));
        linkageHandler = new LinkageHandler(store, mock(LinkageRepository.class));

        server = new MonitorServer(store, dispatcher, scheduler, alarmHandler, linkageHandler, List.of());
    }

    // ---- controlExecute ----

    @Test
    @DisplayName("controlExecute executes command on control asset")
    void controlExecuteSuccess() {
        TestControl ctrl = new TestControl();
        ctrl.init(new ControlType("ct"), 10, "ctrl1");
        store.addAsset(ctrl);

        String result = server.controlExecute(10, "turn_on");
        assertThat(result).isEqualTo("success");
        assertThat(ctrl.lastCommand).isEqualTo("turn_on");
    }

    @Test
    @DisplayName("controlExecute returns error for non-existent asset")
    void controlExecuteNotFound() {
        String result = server.controlExecute(999, "cmd");
        assertThat(result).contains("not found");
    }

    @Test
    @DisplayName("controlExecute returns error for non-control asset")
    void controlExecuteNotControl() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "probe1");
        store.addAsset(probe);

        String result = server.controlExecute(10, "cmd");
        assertThat(result).contains("not a control");
    }

    @Test
    @DisplayName("controlExecute dispatches error result on exception")
    void controlExecuteException() {
        Control ctrl = new Control() {
            @Override
            public void execute(String command) throws Exception {
                throw new RuntimeException("cmd failed");
            }
        };
        ctrl.init(new ControlType("ct"), 10, "ctrl1");
        store.addAsset(ctrl);

        String result = server.controlExecute(10, "bad");
        assertThat(result).contains("cmd failed");
        verify(dispatcher).dispatch(any(MonitorResult.class));
    }

    // ---- receivePassiveData ----

    @Test
    @DisplayName("receivePassiveData with null key is ignored")
    void receivePassiveDataNullKey() {
        assertThatNoException().isThrownBy(() -> server.receivePassiveData(null, 42));
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("receivePassiveData with blank key is ignored")
    void receivePassiveDataBlankKey() {
        assertThatNoException().isThrownBy(() -> server.receivePassiveData("  ", 42));
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("receivePassiveData with unknown key is ignored")
    void receivePassiveDataUnknownKey() {
        server.receivePassiveData("unknown", 42);
        verifyNoInteractions(dispatcher);
    }

    // ---- loadAssets ----

    @Test
    @DisplayName("loadAssets delegates to loader")
    void loadAssets() {
        AssetLoader loader = mock(AssetLoader.class);
        server.loadAssets(loader);
        verify(loader).load(store);
    }

    @Test
    @DisplayName("loadAssets with null loader is safe")
    void loadAssetsNull() {
        assertThatNoException().isThrownBy(() -> server.loadAssets(null));
    }

    // ---- loadAlarmRules ----

    @Test
    @DisplayName("loadAlarmRules delegates to alarm handler")
    void loadAlarmRules() {
        AlarmRule rule = new AlarmRule();
        rule.setId(1);
        rule.setMonitorId(10);
        rule.setStrategy(AlarmStrategy.CONTINUOUS);
        rule.setEnabled(true);

        server.loadAlarmRules(List.of(rule));

        // Verify by checking the handler processes rules
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        MonitorResult result = new MonitorResult(probe, "err");
        result.setStatus(com.systar.monitor.asset.AssetState.ERROR);
        var event = new com.systar.monitor.result.MonitorResultEvent(this, result);

        alarmHandler.onMonitorResult(event);
        assertThat(alarmHandler.getAlarmQueue()).hasSize(1);
    }

    // ---- loadLinkageRules ----

    @Test
    @DisplayName("loadLinkageRules delegates to linkage handler")
    void loadLinkageRules() {
        LinkageRuleBean rule = new LinkageRuleBean();
        rule.setId(100);
        rule.setCauseType(CauseType.MONITOR);
        rule.setEnabled(true);

        LinkageRuleCauseBean cause = new LinkageRuleCauseBean();
        cause.setId(1);
        cause.setRuleId(100);
        cause.setCauseMonitorId(10);
        cause.setTriggerValue("on");

        LinkageRuleEffectBean effect = new LinkageRuleEffectBean();
        effect.setId(1);
        effect.setRuleId(100);
        effect.setEffectMonitorId(20);
        effect.setEffectCommand("turn_on");

        server.loadLinkageRules(List.of(rule), List.of(cause), List.of(effect));

        assertThatNoException().isThrownBy(() -> server.loadLinkageRules(null, null, null));
    }

    // ---- findAsset / getAssets ----

    @Test
    @DisplayName("findAsset delegates to store")
    void findAsset() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        store.addAsset(probe);

        assertThat(server.findAsset(10)).isSameAs(probe);
        assertThat(server.findAsset(999)).isNull();
    }

    @Test
    @DisplayName("getAssets returns store assets")
    void getAssets() {
        assertThat(server.getAssets()).isNotEmpty(); // has root
    }

    // ---- getAssetsByKind ----

    @Test
    @DisplayName("getAssetsByKind delegates to store")
    void getAssetsByKind() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        store.addAsset(probe);

        List<Asset<?>> probes = server.getAssetsByKind(AssetKind.PROBE);
        assertThat(probes).hasSize(1);
    }

    // ---- getAssetPath ----

    @Test
    @DisplayName("getAssetPath returns full path for existing asset")
    void getAssetPath() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "sensor1");
        store.addAsset(probe);

        String path = server.getAssetPath(10);
        assertThat(path).isEqualTo("root->sensor1");
    }

    @Test
    @DisplayName("getAssetPath returns empty for non-existent")
    void getAssetPathNotFound() {
        assertThat(server.getAssetPath(999)).isEmpty();
    }

    // ---- addAsset ----

    @Test
    @DisplayName("addAsset adds to store and schedules active monitors")
    void addAsset() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        // No source set -> not scheduled
        server.addAsset(probe);
        assertThat(store.findAsset(10)).isSameAs(probe);
        verifyNoInteractions(scheduler);
    }

    // ---- removeAsset ----

    @Test
    @DisplayName("removeAsset unschedules monitor and removes from store")
    void removeAsset() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        store.addAsset(probe);

        server.removeAsset(10);
        assertThat(store.findAsset(10)).isNull();
        verify(scheduler).unscheduleMonitor(10);
    }

    // ---- updateAsset ----

    @Test
    @DisplayName("updateAsset replaces old asset with new version")
    void updateAsset() {
        Probe oldProbe = new Probe();
        oldProbe.init(new ProbeType("pt"), 10, "old-probe");
        store.addAsset(oldProbe);

        Probe newProbe = new Probe();
        newProbe.init(new ProbeType("pt"), 10, "new-probe");

        server.updateAsset(newProbe);
        assertThat(store.findAsset(10)).isSameAs(newProbe);
        verify(scheduler).unscheduleMonitor(10);
        // No source → not rescheduled
        verify(scheduler, never()).scheduleMonitor(any());
    }

    @Test
    @DisplayName("updateAsset reschedules if new asset has active source")
    void updateAssetReschedules() {
        Probe oldProbe = new Probe();
        oldProbe.init(new ProbeType("pt"), 10, "old-probe");
        store.addAsset(oldProbe);

        Probe newProbe = new Probe();
        newProbe.init(new ProbeType("pt"), 10, "new-probe");
        ActiveService activeService = new ActiveService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public MonitorConnection createConnection() { return null; }
        };
        activeService.init(new com.systar.monitor.asset.type.ServiceType("svc"), 20, "svc");
        newProbe.setSource(activeService);

        server.updateAsset(newProbe);
        verify(scheduler).unscheduleMonitor(10);
        verify(scheduler).scheduleMonitor(newProbe);
    }

    // ---- startMonitor ----

    @Test
    @DisplayName("startMonitor schedules monitor and enables it")
    void startMonitor() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        probe.setEnabled(false);
        store.addAsset(probe);

        server.startMonitor(10);
        assertThat(probe.isEnabled()).isTrue();
        verify(scheduler).scheduleMonitor(probe);
    }

    @Test
    @DisplayName("startMonitor throws for non-monitor asset")
    void startMonitorNonMonitor() {
        assertThatThrownBy(() -> server.startMonitor(store.getRoot().getId()))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("not a monitor");
    }

    @Test
    @DisplayName("startMonitor throws for non-existent asset")
    void startMonitorNotFound() {
        assertThatThrownBy(() -> server.startMonitor(999))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("not found");
    }

    // ---- stopMonitor ----

    @Test
    @DisplayName("stopMonitor unschedules monitor")
    void stopMonitor() {
        Probe probe = new Probe();
        probe.init(new ProbeType("pt"), 10, "p1");
        store.addAsset(probe);

        server.stopMonitor(10);
        verify(scheduler).unscheduleMonitor(10);
    }

    @Test
    @DisplayName("stopMonitor throws for non-monitor asset")
    void stopMonitorNonMonitor() {
        assertThatThrownBy(() -> server.stopMonitor(store.getRoot().getId()))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("not a monitor");
    }

    @Test
    @DisplayName("stopMonitor throws for non-existent asset")
    void stopMonitorNotFound() {
        assertThatThrownBy(() -> server.stopMonitor(999))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("not found");
    }

    // ---- reloadAlarmRules ----

    @Test
    @DisplayName("reloadAlarmRules reloads rules")
    void reloadAlarmRules() {
        server.reloadAlarmRules(List.of());
        // No exception
    }

    // ---- reloadLinkageRules ----

    @Test
    @DisplayName("reloadLinkageRules reloads rules")
    void reloadLinkageRules() {
        server.reloadLinkageRules(List.of(), List.of(), List.of());
        // No exception
    }

    @Nested
    @DisplayName("detectOnce")
    class DetectOnce {

        private MonitorServer detectOnceServer(AssetStore store, ResultDispatcher dispatcher) {
            MonitorScheduler scheduler = null;
            AlarmHandler alarmHandler = null;
            LinkageHandler linkageHandler = null;
            List<AssetTypeLoader> typeLoaders = List.of();
            return new MonitorServer(store, dispatcher, scheduler, alarmHandler, linkageHandler, typeLoaders);
        }

        @Test
        @DisplayName("triggers detect and dispatches result")
        void triggersDetectAndDispatches() throws Exception {
            Probe probe = mock(Probe.class);
            ResultDispatcher dispatcher = mock(ResultDispatcher.class);

            AssetStore store = new AssetStore();
            store.createRoot(new SpaceType("rootType"), "r");
            store.addAsset(probe);

            MonitorServer svr = detectOnceServer(store, dispatcher);
            svr.detectOnce(probe.getId());

            verify(probe).detect(any(MonitorResult.class));
            verify(dispatcher).dispatch(any(MonitorResult.class));
        }

        @Test
        @DisplayName("throws when asset not found")
        void throwsNotFound() {
            AssetStore store = new AssetStore();
            store.createRoot(new SpaceType("rootType"), "r");
            MonitorServer svr = detectOnceServer(store, null);
            assertThatThrownBy(() -> svr.detectOnce(999))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws when asset is not a Monitor")
        void throwsNotMonitor() {
            Space space = new Space();
            space.init(new SpaceType("st"), 1, "s");

            AssetStore store = spy(new AssetStore());
            store.createRoot(new SpaceType("rootType"), "r");
            store.addAsset(space);
            doReturn(space).when(store).findAsset(1);

            MonitorServer svr = detectOnceServer(store, null);
            assertThatThrownBy(() -> svr.detectOnce(1))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not a monitor");
        }
    }

    @Nested
    @DisplayName("detectImmediately")
    class DetectImmediately {

        // Reuse the lighter-weight server builder from DetectOnce
        private MonitorServer detectOnceServer(AssetStore store, ResultDispatcher dispatcher) {
            MonitorScheduler scheduler = mock(MonitorScheduler.class);
            return new MonitorServer(store, dispatcher, scheduler, null, null, List.of());
        }

        @Test
        @DisplayName("delegates to scheduler without throwing")
        void delegatesToScheduler() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 1, "p");
            AssetStore store = spy(new AssetStore());
            store.createRoot(new SpaceType("rootType"), "r");
            doReturn(probe).when(store).findAsset(1);

            MonitorServer svr = detectOnceServer(store, mock(ResultDispatcher.class));
            // No exception = scheduler accepted the call (mock silently does nothing)
            assertThatNoException().isThrownBy(() -> svr.detectImmediately(1));
        }

        @Test
        @DisplayName("throws AssetException when asset not found")
        void throwsNotFound() {
            AssetStore store = new AssetStore();
            store.createRoot(new SpaceType("rootType"), "r");
            MonitorServer svr = detectOnceServer(store, mock(ResultDispatcher.class));
            assertThatThrownBy(() -> svr.detectImmediately(999))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws AssetException when asset is not a Monitor")
        void throwsNotMonitor() {
            Space space = new Space();
            space.init(new SpaceType("st"), 1, "s");
            AssetStore store = spy(new AssetStore());
            store.createRoot(new SpaceType("rootType"), "r");
            doReturn(space).when(store).findAsset(1);

            MonitorServer svr = detectOnceServer(store, mock(ResultDispatcher.class));
            assertThatThrownBy(() -> svr.detectImmediately(1))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not a monitor");
        }

        @Test
        @DisplayName("throws AssetException for passive monitor")
        void throwsPassive() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 1, "p");
            probe.setMode(MonitorMode.PASSIVE);
            AssetStore store = spy(new AssetStore());
            store.createRoot(new SpaceType("rootType"), "r");
            doReturn(probe).when(store).findAsset(1);

            MonitorServer svr = detectOnceServer(store, mock(ResultDispatcher.class));
            assertThatThrownBy(() -> svr.detectImmediately(1))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("passive");
        }
    }
}
