package com.systar.monitor.integration;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.alarm.AlarmCorrelator;
import com.systar.monitor.alarm.AlarmHandler;
import com.systar.monitor.alarm.AlarmRepository;
import com.systar.monitor.alarm.AlarmRule;
import com.systar.monitor.alarm.AlarmStrategy;
import com.systar.monitor.alarm.AlarmSuppressionChecker;
import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.ServiceType;
import com.systar.monitor.asset.type.SpaceType;
import com.systar.monitor.linkage.CauseType;
import com.systar.monitor.linkage.LinkageHandler;
import com.systar.monitor.linkage.LinkageRepository;
import com.systar.monitor.linkage.LinkageRuleBean;
import com.systar.monitor.linkage.LinkageRuleCauseBean;
import com.systar.monitor.linkage.LinkageRuleEffectBean;
import com.systar.monitor.result.IMonitorResult;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import com.systar.monitor.result.ResultDispatcher;
import com.systar.monitor.schedule.DetectTask;
import com.systar.monitor.schedule.MonitorScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying concurrent multi-probe detection through the
 * full pipeline: Scheduler → TaskDispatcher → DetectTask → ResultDispatcher.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ConcurrentDetectionIT {

    private AssetStore store;
    private ResultDispatcher dispatcher;
    private MonitorScheduler scheduler;
    private List<MonitorResult> collectedResults;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new SpaceType("root"), "root");
        collectedResults = new CopyOnWriteArrayList<>();
        dispatcher = new ResultDispatcher();
        // Collect every dispatched result
        dispatcher.setApplicationEventPublisher(event -> {
            if (event instanceof MonitorResultEvent mre && mre.getResult() != null) {
                collectedResults.add(mre.getResult());
            }
        });
        scheduler = new MonitorScheduler(store, dispatcher);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    // ======================== test infrastructure ========================

    /**
     * Simple probe that generates an incrementing counter value.
     * Does not require network or external resources.
     */
    static class CounterProbe extends Probe {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public void detect(IMonitorResult result) throws Exception {
            result.setValue(counter.incrementAndGet());
            result.setSampleTime(System.currentTimeMillis());
        }
    }

    /**
     * Minimal ActiveService that doesn't need real connections.
     */
    static class TestService extends ActiveService {
        @Override
        public void start() throws Exception {}

        @Override
        public void stop() {}

        @Override
        public MonitorConnection createConnection() throws Exception {
            return new MonitorConnection() {
                private volatile boolean open = true;
                @Override public void open() throws Exception { open = true; }
                @Override public boolean isConnected() { return open; }
                @Override public void close() { open = false; }
            };
        }
    }

    private TestService createServiceWithProbes(int probeCount, TimeSpan interval) {
        TestService service = new TestService();
        service.init(new ServiceType("test-svc"), 100, "test-service");
        store.addAsset(service);

        for (int i = 1; i <= probeCount; i++) {
            CounterProbe probe = new CounterProbe();
            ProbeType type = new ProbeType("counter");
            type.setDetectInterval(interval);
            probe.init(type, 100 + i, "probe-" + i);
            probe.setSource(service);
            store.addAsset(probe);
        }
        return service;
    }

    // ======================== test cases ========================

    @Nested
    @DisplayName("Concurrent multi-probe detection")
    class ConcurrentDetection {

        @Test
        @DisplayName("10 probes produce results concurrently")
        void multipleProbesProduceResults() {
            createServiceWithProbes(10, TimeSpan.ofSeconds(1));
            scheduler.start();

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            assertThat(collectedResults).hasSizeGreaterThanOrEqualTo(10));

            // Every probe should have a non-null value
            assertThat(collectedResults)
                    .allSatisfy(r -> assertThat(r.getValue()).isNotNull());
        }

        @Test
        @DisplayName("probes produce multiple rounds of results")
        void multipleRounds() {
            createServiceWithProbes(5, TimeSpan.ofSeconds(1));
            scheduler.start();

            // With up to 5s jitter + 1s interval, allow generous timeout
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            assertThat(collectedResults).hasSizeGreaterThanOrEqualTo(10));

            // At least 3 of 5 probes should have detected multiple times
            int multiDetectProbes = 0;
            for (int i = 1; i <= 5; i++) {
                final int probeId = 100 + i;
                long count = collectedResults.stream()
                        .filter(r -> r.getMonitor() != null && r.getMonitor().getId() == probeId)
                        .count();
                if (count >= 2) multiDetectProbes++;
            }
            assertThat(multiDetectProbes).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Alarm integration under concurrency")
    class AlarmConcurrency {

        @Test
        @DisplayName("concurrent detection triggers alarms correctly")
        void alarmsGeneratedConcurrently() {
            AlarmRepository alarmRepo = mock(AlarmRepository.class);
            AlarmHandler alarmHandler = new AlarmHandler(
                    alarmRepo,
                    mock(AlarmCorrelator.class),
                    mock(AlarmSuppressionChecker.class));

            // Create a probe with a warn condition that always triggers
            TestService service = new TestService();
            service.init(new ServiceType("alarm-svc"), 200, "alarm-service");
            store.addAsset(service);

            ProbeType type = new ProbeType("alarm-probe");
            type.setDetectInterval(TimeSpan.ofSeconds(1));
            type.setWarnCondition("#value > 0");

            CounterProbe probe = new CounterProbe();
            probe.init(type, 201, "alarm-probe");
            probe.setSource(service);
            store.addAsset(probe);

            // Set up alarm rule
            AlarmRule rule = new AlarmRule();
            rule.setId(1);
            rule.setMonitorId(201);
            rule.setStrategy(AlarmStrategy.CONTINUOUS);
            rule.setEnabled(true);
            alarmHandler.loadRules(List.of(rule));

            // Wire alarm handler to result dispatcher
            dispatcher.setApplicationEventPublisher(event -> {
                if (event instanceof MonitorResultEvent mre) {
                    alarmHandler.onMonitorResult(mre);
                }
            });

            scheduler.start();

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            assertThat(alarmHandler.getAlarmQueue().size()).isGreaterThanOrEqualTo(3));

            // All alarms should reference the correct monitor
            assertThat(alarmHandler.getAlarmQueue())
                    .allSatisfy(msg -> assertThat(msg.getMonitorId()).isEqualTo(201));
        }
    }

    @Nested
    @DisplayName("Linkage integration under concurrency")
    class LinkageConcurrency {

        @Test
        @DisplayName("concurrent detection triggers linkage execution")
        void linkageTriggeredConcurrently() {
            LinkageRepository linkageRepo = mock(LinkageRepository.class);
            LinkageHandler linkageHandler = new LinkageHandler(store, linkageRepo);

            TestService service = new TestService();
            service.init(new ServiceType("link-svc"), 300, "link-service");
            store.addAsset(service);

            // Boolean probe with "|" unit
            ProbeType boolType = new ProbeType("switch");
            boolType.setUnit("off|on");
            boolType.setDetectInterval(TimeSpan.ofSeconds(1));

            Probe triggerProbe = new Probe() {
                @Override
                public void detect(IMonitorResult result) throws Exception {
                    result.setValue("1");
                    result.setSampleTime(System.currentTimeMillis());
                }
            };
            triggerProbe.init(boolType, 301, "trigger-switch");
            triggerProbe.setSource(service);
            store.addAsset(triggerProbe);

            // Target control
            AtomicInteger executionCount = new AtomicInteger(0);
            Control targetControl = new Control() {
                @Override
                public void execute(String command) throws Exception {
                    executionCount.incrementAndGet();
                }
            };
            targetControl.init(new ControlType("actuator"), 302, "actuator");
            store.addAsset(targetControl);

            // Linkage rule
            LinkageRuleBean rule = new LinkageRuleBean();
            rule.setId(10);
            rule.setCauseType(CauseType.MONITOR);
            rule.setEnabled(true);

            LinkageRuleCauseBean cause = new LinkageRuleCauseBean();
            cause.setId(1);
            cause.setRuleId(10);
            cause.setCauseMonitorId(301);
            cause.setTriggerValue("1");

            LinkageRuleEffectBean effect = new LinkageRuleEffectBean();
            effect.setId(1);
            effect.setRuleId(10);
            effect.setEffectMonitorId(302);
            effect.setEffectCommand("on");

            linkageHandler.loadRules(List.of(rule), List.of(cause), List.of(effect));

            // Wire linkage to results
            dispatcher.setApplicationEventPublisher(event -> {
                if (event instanceof MonitorResultEvent mre) {
                    linkageHandler.onMonitorResult(mre);
                }
            });

            scheduler.start();

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            assertThat(executionCount.get()).isGreaterThanOrEqualTo(2));

            // Verify linkage logs persisted
            verify(linkageRepo, atLeast(2)).saveLinkageLog(eq(10), eq(301), eq(302), eq("on"), eq(true));
        }
    }

    @Nested
    @DisplayName("Graceful shutdown")
    class GracefulShutdown {

        @Test
        @DisplayName("scheduler stops cleanly during active detection")
        void cleanShutdownDuringDetection() {
            createServiceWithProbes(20, TimeSpan.ofSeconds(1));
            scheduler.start();

            // Let some results come in
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !collectedResults.isEmpty());

            // Stop should complete without deadlock or exception
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException();

            assertThat(collectedResults).isNotEmpty();
        }

        @Test
        @DisplayName("no new results after shutdown")
        void noResultsAfterShutdown() {
            createServiceWithProbes(5, TimeSpan.ofSeconds(1));
            scheduler.start();

            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> collectedResults.size() >= 5);

            scheduler.stop();
            int sizeAtStop = collectedResults.size();

            // Wait a bit and verify no new results
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            assertThat(collectedResults).hasSize(sizeAtStop);
        }
    }

    @Nested
    @DisplayName("Concurrent detectImmediately")
    class ConcurrentManualDetect {

        @Test
        @DisplayName("only one concurrent detectImmediately succeeds for same monitor")
        void onlyOneSucceeds() throws Exception {
            // Create a probe that takes a while to detect
            Probe slowProbe = new Probe() {
                @Override
                public void detect(IMonitorResult result) throws Exception {
                    Thread.sleep(2000); // 2s delay
                    result.setValue(42);
                    result.setSampleTime(System.currentTimeMillis());
                }
            };
            ProbeType type = new ProbeType("slow");
            type.setDetectInterval(TimeSpan.ofSeconds(10));
            slowProbe.init(type, 500, "slow-probe");
            store.addAsset(slowProbe);

            scheduler.start();

            // First call should succeed
            assertThatCode(() -> scheduler.detectImmediately(slowProbe))
                    .doesNotThrowAnyException();
            assertThat(slowProbe.isDetecting()).isTrue();

            // Second concurrent call should fail (409 conflict)
            assertThatThrownBy(() -> scheduler.detectImmediately(slowProbe))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already detecting");

            // Wait for detection to complete
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !slowProbe.isDetecting());

            // After completion, another call should succeed
            assertThatCode(() -> scheduler.detectImmediately(slowProbe))
                    .doesNotThrowAnyException();

            scheduler.stop();
        }
    }
}
