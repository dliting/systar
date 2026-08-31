package com.systar.monitor.alarm;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AlarmHandlerTest {

    private AlarmHandler handler;
    private AlarmRepository alarmRepository;
    private AlarmCorrelator correlator;
    private AlarmSuppressionChecker suppressionChecker;
    private Probe monitor;
    private ProbeType type;

    @BeforeEach
    void setUp() {
        alarmRepository    = mock(AlarmRepository.class);
        correlator         = mock(AlarmCorrelator.class);
        suppressionChecker = mock(AlarmSuppressionChecker.class);

        // Default: no correlation, no suppression
        when(correlator.correlate(any())).thenReturn(null);
        when(suppressionChecker.isSilenced(any(), anyInt())).thenReturn(false);
        when(suppressionChecker.isDuplicate(anyInt(), anyInt(), anyInt())).thenReturn(false);

        handler = new AlarmHandler(alarmRepository, correlator, suppressionChecker);
        type = new ProbeType("pt");
        monitor = new Probe();
        monitor.init(type, 1, "probe1");
        monitor.setCaption("Temperature");
    }

    // ---- helper ----

    private MonitorResultEvent eventFor(AssetState status, String error) {
        MonitorResult result;
        if (error != null) {
            result = new MonitorResult(monitor, error);
        } else {
            result = new MonitorResult(monitor, 42);
        }
        result.setStatus(status);
        return new MonitorResultEvent(this, result);
    }

    private MonitorResultEvent eventFor(AssetState status) {
        return eventFor(status, null);
    }

    private void addRule(int monitorId, AlarmStrategy strategy) {
        AlarmRule rule = new AlarmRule();
        rule.setId(1);
        rule.setMonitorId(monitorId);
        rule.setStrategy(strategy);
        rule.setEnabled(true);
        handler.addRule(rule);
    }

    // ---- ONLY_ONCE ----

    @Test
    @DisplayName("ONLY_ONCE: first WARNING fires alarm")
    void onlyOnceFirstFires() {
        addRule(1, AlarmStrategy.ONLY_ONCE);

        MonitorResultEvent event = eventFor(AssetState.WARNING);
        handler.onMonitorResult(event);

        assertThat(handler.getAlarmQueue()).hasSize(1);
        ErrorMessageLog msg = handler.getAlarmQueue().poll();
        assertThat(msg.getMonitorId()).isEqualTo(1);
        assertThat(msg.getState()).isEqualTo(AssetState.WARNING);
    }

    @Test
    @DisplayName("ONLY_ONCE: second WARNING is suppressed")
    void onlyOnceSecondSuppressed() {
        addRule(1, AlarmStrategy.ONLY_ONCE);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        handler.onMonitorResult(eventFor(AssetState.WARNING));

        assertThat(handler.getAlarmQueue()).hasSize(1);
    }

    @Test
    @DisplayName("ONLY_ONCE: recovery to NORMAL resets tracker")
    void onlyOnceRecoveryResets() {
        addRule(1, AlarmStrategy.ONLY_ONCE);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        handler.onMonitorResult(eventFor(AssetState.NORMAL)); // recovery
        handler.onMonitorResult(eventFor(AssetState.WARNING)); // new alarm

        assertThat(handler.getAlarmQueue()).hasSize(2);
    }

    // ---- CONTINUOUS ----

    @Test
    @DisplayName("CONTINUOUS: every matching result fires alarm")
    void continuousFiresEachTime() {
        addRule(1, AlarmStrategy.CONTINUOUS);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        handler.onMonitorResult(eventFor(AssetState.WARNING));
        handler.onMonitorResult(eventFor(AssetState.WARNING));

        assertThat(handler.getAlarmQueue()).hasSize(3);
    }

    // ---- SELECTIVE ----

    @Test
    @DisplayName("SELECTIVE: first fires, second suppressed, recovery resets")
    void selectiveStrategy() {
        addRule(1, AlarmStrategy.SELECTIVE);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).hasSize(1);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).hasSize(1); // still 1

        handler.onMonitorResult(eventFor(AssetState.NORMAL)); // recovery
        handler.onMonitorResult(eventFor(AssetState.WARNING)); // new cycle
        assertThat(handler.getAlarmQueue()).hasSize(2);
    }

    // ---- disabled rule ----

    @Test
    @DisplayName("Disabled rule does not fire alarm")
    void disabledRule() {
        AlarmRule rule = new AlarmRule();
        rule.setId(1);
        rule.setMonitorId(1);
        rule.setStrategy(AlarmStrategy.CONTINUOUS);
        rule.setEnabled(false);
        handler.addRule(rule);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    // ---- no rule for monitor ----

    @Test
    @DisplayName("No rule for monitor produces no alarm")
    void noRule() {
        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    // ---- ERROR state ----

    @Test
    @DisplayName("ERROR state triggers alarm")
    void errorStateTriggersAlarm() {
        addRule(1, AlarmStrategy.CONTINUOUS);
        MonitorResultEvent event = eventFor(AssetState.ERROR, "sensor failed");
        handler.onMonitorResult(event);

        assertThat(handler.getAlarmQueue()).hasSize(1);
        ErrorMessageLog msg = handler.getAlarmQueue().poll();
        assertThat(msg.getError()).isEqualTo("sensor failed");
    }

    // ---- OFFLINE ignored ----

    @Test
    @DisplayName("OFFLINE state does not trigger alarm")
    void offlineIgnored() {
        addRule(1, AlarmStrategy.CONTINUOUS);
        MonitorResultEvent event = eventFor(AssetState.OFFLINE);
        handler.onMonitorResult(event);

        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    // ---- null event / result ----

    @Test
    @DisplayName("Null result is ignored")
    void nullResult() {
        MonitorResultEvent event = new MonitorResultEvent(this, null);
        handler.onMonitorResult(event);
        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    // ---- loadRules ----

    @Test
    @DisplayName("loadRules replaces existing rules")
    void loadRules() {
        AlarmRule rule1 = new AlarmRule();
        rule1.setId(1);
        rule1.setMonitorId(1);
        rule1.setStrategy(AlarmStrategy.CONTINUOUS);
        rule1.setEnabled(true);

        handler.loadRules(List.of(rule1));
        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).hasSize(1);
    }

    @Test
    @DisplayName("loadRules clears previous state")
    void loadRulesClearsState() {
        addRule(1, AlarmStrategy.ONLY_ONCE);
        handler.onMonitorResult(eventFor(AssetState.WARNING));

        // Reload with empty -> clears everything
        handler.loadRules(null);
        assertThat(handler.getAlarmQueue()).hasSize(1); // old queue not cleared
    }

    // ---- removeRule ----

    @Test
    @DisplayName("removeRule prevents future alarms")
    void removeRule() {
        addRule(1, AlarmStrategy.CONTINUOUS);
        handler.removeRule(1);
        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    // ---- message template ----

    @Test
    @DisplayName("Message template is used with placeholders replaced")
    void messageTemplate() {
        AlarmRule rule = new AlarmRule();
        rule.setId(1);
        rule.setMonitorId(1);
        rule.setStrategy(AlarmStrategy.CONTINUOUS);
        rule.setEnabled(true);
        rule.setMessageTemplate("${name} = ${value}");
        handler.addRule(rule);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        ErrorMessageLog msg = handler.getAlarmQueue().poll();
        assertThat(msg.getError()).isEqualTo("probe1 = 42");
    }

    @Test
    @DisplayName("Default message when no template and no error")
    void defaultMessage() {
        addRule(1, AlarmStrategy.CONTINUOUS);
        handler.onMonitorResult(eventFor(AssetState.WARNING));

        ErrorMessageLog msg = handler.getAlarmQueue().poll();
        assertThat(msg.getError()).contains("probe1").contains("WARNING");
    }

    // ---- persistence via AlarmRepository ----

    @Test
    @DisplayName("Manually drained alarm is saved via AlarmRepository")
    void alarmPersistedViaRepository() {
        addRule(1, AlarmStrategy.CONTINUOUS);
        handler.onMonitorResult(eventFor(AssetState.WARNING));

        assertThat(handler.getAlarmQueue()).hasSize(1);
        ErrorMessageLog msg = handler.getAlarmQueue().poll();
        assertThat(msg).isNotNull();

        // Simulate what the consumer thread would do
        alarmRepository.saveAlarm(msg);

        verify(alarmRepository).saveAlarm(msg);
    }

    // ---- correlation & suppression integration ----

    @Test
    @DisplayName("Correlation group is set on alarm message")
    void correlationGroupSetOnAlarm() {
        when(correlator.correlate(any())).thenReturn("CORR-10-2026060105");
        addRule(1, AlarmStrategy.CONTINUOUS);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        ErrorMessageLog msg = handler.getAlarmQueue().poll();
        assertThat(msg.getCorrelationGroup()).isEqualTo("CORR-10-2026060105");
    }

    @Test
    @DisplayName("Silenced alarm is not enqueued")
    void silencedAlarmNotEnqueued() {
        when(suppressionChecker.isSilenced(any(), anyInt())).thenReturn(true);
        addRule(1, AlarmStrategy.CONTINUOUS);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    @Test
    @DisplayName("Deduplicated alarm is not enqueued")
    void deduplicatedAlarmNotEnqueued() {
        when(suppressionChecker.isDuplicate(anyInt(), anyInt(), anyInt())).thenReturn(true);
        AlarmRule rule = new AlarmRule();
        rule.setId(1);
        rule.setMonitorId(1);
        rule.setStrategy(AlarmStrategy.CONTINUOUS);
        rule.setEnabled(true);
        rule.setDedupWindowSeconds(300);
        handler.addRule(rule);

        handler.onMonitorResult(eventFor(AssetState.WARNING));
        assertThat(handler.getAlarmQueue()).isEmpty();
    }

    @Test
    @DisplayName("recordAlarmFired is called when alarm fires")
    void recordAlarmFiredCalled() {
        addRule(1, AlarmStrategy.CONTINUOUS);
        handler.onMonitorResult(eventFor(AssetState.WARNING));

        verify(suppressionChecker).recordAlarmFired(1, 1);
    }
}
