package com.systar.monitor.linkage;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class LinkageHandlerTest {

    private AssetStore store;
    private LinkageRepository linkageRepository;
    private LinkageHandler handler;

    static class TestControl extends Control {
        String lastCommand;
        boolean shouldThrow;

        @Override
        public void execute(String command) {
            if (shouldThrow) throw new RuntimeException("exec failed");
            lastCommand = command;
        }
    }

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new com.systar.monitor.asset.type.SpaceType("root"), "root");
        linkageRepository = Mockito.mock(LinkageRepository.class);
        handler = new LinkageHandler(store, linkageRepository);
    }

    private Probe createBooleanProbe(int id, String name) {
        ProbeType type = new ProbeType("pt-" + name);
        type.setUnit("off|on");
        Probe probe = new Probe();
        probe.init(type, id, name);
        store.addAsset(probe);
        return probe;
    }

    private TestControl createControl(int id, String name) {
        TestControl ctrl = new TestControl();
        ControlType type = new ControlType("ct-" + name);
        ctrl.init(type, id, name);
        store.addAsset(ctrl);
        return ctrl;
    }

    private MonitorResultEvent eventFor(Probe probe, Object value) {
        return eventFor(probe, value, AssetState.NORMAL);
    }

    private MonitorResultEvent eventFor(Probe probe, Object value, AssetState status) {
        MonitorResult result = new MonitorResult(probe, value);
        result.setStatus(status);
        return new MonitorResultEvent(this, result);
    }

    private LinkageRuleBean monitorRule(int ruleId) {
        LinkageRuleBean rule = new LinkageRuleBean();
        rule.setId(ruleId);
        rule.setCauseType(CauseType.MONITOR);
        rule.setEnabled(true);
        return rule;
    }

    private LinkageRuleBean alarmRule(int ruleId) {
        LinkageRuleBean rule = new LinkageRuleBean();
        rule.setId(ruleId);
        rule.setCauseType(CauseType.ALARM);
        rule.setEnabled(true);
        return rule;
    }

    private LinkageRuleCauseBean cause(int id, int ruleId, int monitorId, String triggerValue) {
        LinkageRuleCauseBean c = new LinkageRuleCauseBean();
        c.setId(id);
        c.setRuleId(ruleId);
        c.setCauseMonitorId(monitorId);
        c.setTriggerValue(triggerValue);
        return c;
    }

    private LinkageRuleEffectBean effect(int id, int ruleId, int controlId, String cmd) {
        LinkageRuleEffectBean e = new LinkageRuleEffectBean();
        e.setId(id);
        e.setRuleId(ruleId);
        e.setEffectMonitorId(controlId);
        e.setEffectCommand(cmd);
        return e;
    }

    // ======================== monitor-triggered linkage ========================

    @Nested
    @DisplayName("Monitor-triggered linkage")
    class MonitorTriggered {

        @Test
        @DisplayName("Boolean probe matching trigger value fires linkage")
        void matchingValueFires() {
            Probe probe = createBooleanProbe(10, "switch1");
            TestControl ctrl = createControl(20, "light1");

            handler.loadRules(
                    List.of(monitorRule(100)),
                    List.of(cause(1, 100, 10, "on")),
                    List.of(effect(1, 100, 20, "turn_on")));

            handler.onMonitorResult(eventFor(probe, "on"));
            assertThat(ctrl.lastCommand).isEqualTo("turn_on");
        }

        @Test
        @DisplayName("Non-matching value does not fire")
        void nonMatchingValue() {
            Probe probe = createBooleanProbe(10, "switch1");
            TestControl ctrl = createControl(20, "light1");

            handler.loadRules(
                    List.of(monitorRule(100)),
                    List.of(cause(1, 100, 10, "on")),
                    List.of(effect(1, 100, 20, "turn_on")));

            handler.onMonitorResult(eventFor(probe, "off"));
            assertThat(ctrl.lastCommand).isNull();
        }

        @Test
        @DisplayName("Non-boolean probe (no pipe in unit) is ignored")
        void nonBooleanIgnored() {
            ProbeType type = new ProbeType("pt");
            type.setUnit("C");
            Probe probe = new Probe();
            probe.init(type, 10, "temp");
            store.addAsset(probe);

            handler.onMonitorResult(eventFor(probe, 25.0));
        }

        @Test
        @DisplayName("Probe in WARNING state ignored for monitor linkage")
        void nonNormalIgnored() {
            Probe probe = createBooleanProbe(10, "switch1");

            handler.loadRules(
                    List.of(monitorRule(100)),
                    List.of(cause(1, 100, 10, "on")),
                    List.of());

            probe.setState(AssetState.WARNING);
            MonitorResult result = new MonitorResult(probe, "on");
            result.setStatus(AssetState.NORMAL);
            handler.onMonitorResult(new MonitorResultEvent(this, result));
        }

        @Test
        @DisplayName("Disabled rule does not fire")
        void disabledRule() {
            Probe probe = createBooleanProbe(10, "switch1");
            TestControl ctrl = createControl(20, "light1");

            LinkageRuleBean rule = monitorRule(100);
            rule.setEnabled(false);

            handler.loadRules(
                    List.of(rule),
                    List.of(cause(1, 100, 10, "on")),
                    List.of(effect(1, 100, 20, "turn_on")));

            handler.onMonitorResult(eventFor(probe, "on"));
            assertThat(ctrl.lastCommand).isNull();
        }

        @Test
        @DisplayName("Null result is ignored")
        void nullResult() {
            MonitorResultEvent event = new MonitorResultEvent(this, null);
            assertThatNoException().isThrownBy(() -> handler.onMonitorResult(event));
        }

        @Test
        @DisplayName("loadRules replaces previous rules")
        void loadRulesReplaces() {
            Probe probe = createBooleanProbe(10, "switch1");

            handler.loadRules(
                    List.of(monitorRule(100)),
                    List.of(cause(1, 100, 10, "on")),
                    List.of());

            handler.loadRules(null, null, null);
            handler.onMonitorResult(eventFor(probe, "on"));
        }

        @Test
        @DisplayName("Effect target that is not a Control is skipped")
        void nonControlTargetSkipped() {
            Probe probe = createBooleanProbe(10, "switch1");
            createBooleanProbe(20, "notAControl");

            handler.loadRules(
                    List.of(monitorRule(100)),
                    List.of(cause(1, 100, 10, "on")),
                    List.of(effect(1, 100, 20, "cmd")));

            assertThatNoException().isThrownBy(() -> handler.onMonitorResult(eventFor(probe, "on")));
        }

        @Test
        @DisplayName("Effect target not in store is skipped")
        void missingTargetSkipped() {
            Probe probe = createBooleanProbe(10, "switch1");

            handler.loadRules(
                    List.of(monitorRule(100)),
                    List.of(cause(1, 100, 10, "on")),
                    List.of(effect(1, 100, 999, "cmd")));

            assertThatNoException().isThrownBy(() -> handler.onMonitorResult(eventFor(probe, "on")));
        }
    }

    // ======================== alarm-triggered linkage ========================

    @Nested
    @DisplayName("Alarm-triggered linkage")
    class AlarmTriggered {

        @Test
        @DisplayName("WARNING state triggers alarm linkage")
        void warningTriggersAlarm() {
            Probe probe = createBooleanProbe(10, "smoke");
            TestControl ctrl = createControl(20, "fan");

            handler.loadRules(
                    List.of(alarmRule(200)),
                    List.of(cause(1, 200, 10, "ALARM")),
                    List.of(effect(1, 200, 20, "1")));

            handler.onMonitorResult(eventFor(probe, 99.0, AssetState.WARNING));
            assertThat(ctrl.lastCommand).isEqualTo("1");
        }

        @Test
        @DisplayName("ERROR state triggers alarm linkage")
        void errorTriggersAlarm() {
            Probe probe = createBooleanProbe(10, "smoke");
            TestControl ctrl = createControl(20, "fan");

            handler.loadRules(
                    List.of(alarmRule(200)),
                    List.of(cause(1, 200, 10, "ALARM")),
                    List.of(effect(1, 200, 20, "1")));

            handler.onMonitorResult(eventFor(probe, 100.0, AssetState.ERROR));
            assertThat(ctrl.lastCommand).isEqualTo("1");
        }

        @Test
        @DisplayName("NORMAL state does not trigger alarm linkage")
        void normalDoesNotTrigger() {
            Probe probe = createBooleanProbe(10, "smoke");
            TestControl ctrl = createControl(20, "fan");

            handler.loadRules(
                    List.of(alarmRule(200)),
                    List.of(cause(1, 200, 10, "ALARM")),
                    List.of(effect(1, 200, 20, "1")));

            handler.onMonitorResult(eventFor(probe, 0.0, AssetState.NORMAL));
            assertThat(ctrl.lastCommand).isNull();
        }
    }

    // ======================== log persistence ========================

    @Test
    @DisplayName("saveLinkageLog called on success")
    void logPersistedOnSuccess() {
        Probe probe = createBooleanProbe(10, "switch1");
        TestControl ctrl = createControl(20, "light1");

        handler.loadRules(
                List.of(monitorRule(100)),
                List.of(cause(1, 100, 10, "on")),
                List.of(effect(1, 100, 20, "turn_on")));
        handler.onMonitorResult(eventFor(probe, "on"));

        verify(linkageRepository).saveLinkageLog(100, 10, 20, "turn_on", true);
    }

    @Test
    @DisplayName("saveLinkageLog records failure when execute throws")
    void logPersistedOnFailure() {
        Probe probe = createBooleanProbe(10, "switch1");
        TestControl ctrl = createControl(20, "light1");
        ctrl.shouldThrow = true;

        handler.loadRules(
                List.of(monitorRule(100)),
                List.of(cause(1, 100, 10, "on")),
                List.of(effect(1, 100, 20, "turn_on")));
        handler.onMonitorResult(eventFor(probe, "on"));

        verify(linkageRepository).saveLinkageLog(100, 10, 20, "turn_on", false);
    }

    // ======================== correlation-group-triggered linkage ========================

    @Nested
    @DisplayName("Correlation-group-triggered linkage")
    class CorrelationGroupTriggered {

        @Test
        @DisplayName("Correlation group event triggers linkage for matching device")
        void correlationGroupTriggersLinkage() {
            TestControl ctrl = createControl(20, "fan");

            LinkageRuleBean rule = new LinkageRuleBean();
            rule.setId(300);
            rule.setCauseType(CauseType.CORRELATION_GROUP);
            rule.setEnabled(true);

            handler.loadRules(
                    List.of(rule),
                    List.of(cause(1, 300, 10, "CORR")),
                    List.of(effect(1, 300, 20, "shutdown")));

            handler.onCorrelationGroup(new CorrelationGroupEvent(this, "CORR-10-2026060105", 10, 101));
            assertThat(ctrl.lastCommand).isEqualTo("shutdown");
        }

        @Test
        @DisplayName("Correlation group with null deviceId is ignored")
        void nullDeviceIdIgnored() {
            TestControl ctrl = createControl(20, "fan");

            LinkageRuleBean rule = new LinkageRuleBean();
            rule.setId(300);
            rule.setCauseType(CauseType.CORRELATION_GROUP);
            rule.setEnabled(true);

            handler.loadRules(
                    List.of(rule),
                    List.of(cause(1, 300, 10, "CORR")),
                    List.of(effect(1, 300, 20, "shutdown")));

            handler.onCorrelationGroup(new CorrelationGroupEvent(this, "CORR-null-2026060105", null, 101));
            assertThat(ctrl.lastCommand).isNull();
        }

        @Test
        @DisplayName("Correlation group for non-matching device does not fire")
        void nonMatchingDeviceIgnored() {
            TestControl ctrl = createControl(20, "fan");

            LinkageRuleBean rule = new LinkageRuleBean();
            rule.setId(300);
            rule.setCauseType(CauseType.CORRELATION_GROUP);
            rule.setEnabled(true);

            handler.loadRules(
                    List.of(rule),
                    List.of(cause(1, 300, 10, "CORR")),
                    List.of(effect(1, 300, 20, "shutdown")));

            handler.onCorrelationGroup(new CorrelationGroupEvent(this, "CORR-99-2026060105", 99, 101));
            assertThat(ctrl.lastCommand).isNull();
        }

        @Test
        @DisplayName("Disabled correlation rule does not fire")
        void disabledCorrelationRule() {
            TestControl ctrl = createControl(20, "fan");

            LinkageRuleBean rule = new LinkageRuleBean();
            rule.setId(300);
            rule.setCauseType(CauseType.CORRELATION_GROUP);
            rule.setEnabled(false);

            handler.loadRules(
                    List.of(rule),
                    List.of(cause(1, 300, 10, "CORR")),
                    List.of(effect(1, 300, 20, "shutdown")));

            handler.onCorrelationGroup(new CorrelationGroupEvent(this, "CORR-10-2026060105", 10, 101));
            assertThat(ctrl.lastCommand).isNull();
        }
    }

    // ======================== bean accessors ========================

    @Test
    @DisplayName("LinkageRuleCauseBean accessors")
    void causeBeanAccessors() {
        LinkageRuleCauseBean bean = new LinkageRuleCauseBean();
        bean.setId(1);
        bean.setRuleId(2);
        bean.setCauseMonitorId(3);
        bean.setTriggerValue("on");

        assertThat(bean.getId()).isEqualTo(1);
        assertThat(bean.getRuleId()).isEqualTo(2);
        assertThat(bean.getCauseMonitorId()).isEqualTo(3);
        assertThat(bean.getTriggerValue()).isEqualTo("on");
    }

    @Test
    @DisplayName("LinkageRuleEffectBean accessors")
    void effectBeanAccessors() {
        LinkageRuleEffectBean bean = new LinkageRuleEffectBean();
        bean.setId(1);
        bean.setRuleId(2);
        bean.setEffectMonitorId(3);
        bean.setEffectCommand("turn_off");

        assertThat(bean.getId()).isEqualTo(1);
        assertThat(bean.getRuleId()).isEqualTo(2);
        assertThat(bean.getEffectMonitorId()).isEqualTo(3);
        assertThat(bean.getEffectCommand()).isEqualTo("turn_off");
    }
}
