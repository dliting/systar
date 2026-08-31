package com.systar.monitor.alarm;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Monitor;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Alarm engine.
 * <p>
 * Listens for {@link MonitorResultEvent}s, evaluates alarm rules, and produces
 * {@link ErrorMessageLog} instances according to the configured
 * {@link AlarmStrategy}.
 *
 * <h3>Strategy semantics</h3>
 * <ul>
 *   <li><b>ONLY_ONCE</b> -- The first alarm fires normally. Subsequent alarms
 *       for the same monitor are suppressed until the monitor recovers to
 *       NORMAL, at which point the tracker is cleared.</li>
 *   <li><b>CONTINUOUS</b> -- Every matching detection cycle produces an
 *       alarm.</li>
 *   <li><b>SELECTIVE</b> -- Fires once per alarm cycle. A new cycle may only
 *       begin after the monitor has returned to NORMAL. This is tracked via
 *       the {@code alarmedSet}: an entry is added on the first alarm and
 *       removed when the monitor recovers.</li>
 * </ul>
 */
@Component
public class AlarmHandler {

    private static final Logger log = LoggerFactory.getLogger(AlarmHandler.class);

    private final AlarmRepository alarmRepository;
    private final AlarmCorrelator correlator;
    private final AlarmSuppressionChecker suppressionChecker;
    private Thread consumerThread;
    private volatile boolean running;

    public AlarmHandler(AlarmRepository alarmRepository,
                        AlarmCorrelator correlator,
                        AlarmSuppressionChecker suppressionChecker) {
        this.alarmRepository      = alarmRepository;
        this.correlator           = correlator;
        this.suppressionChecker   = suppressionChecker;
    }

    // ======================== lifecycle ========================

    @PostConstruct
    public void start() {
        running = true;
        consumerThread = new Thread(this::consumeLoop, "alarm-persist");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    // ======================== background consumer ========================

    private void consumeLoop() {
        while (running || !alarmQueue.isEmpty()) {
            try {
                ErrorMessageLog msg = alarmQueue.poll(1, TimeUnit.SECONDS);
                if (msg != null) {
                    alarmRepository.saveAlarm(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    drainQueueOnShutdown();
                }
                break;
            } catch (Exception e) {
                log.error("Error persisting alarm", e);
            }
        }
    }

    /**
     * Drains any remaining items from the alarm queue during graceful shutdown.
     */
    private void drainQueueOnShutdown() {
        List<ErrorMessageLog> remaining = new ArrayList<>();
        alarmQueue.drainTo(remaining);
        for (ErrorMessageLog msg : remaining) {
            try {
                alarmRepository.saveAlarm(msg);
            } catch (Exception e) {
                log.error("Error persisting alarm during shutdown drain", e);
            }
        }
    }

    // ======================== rule index ========================

    /** Alarm rules indexed by monitorId for O(1) lookup. */
    private final Map<Integer, AlarmRule> ruleMap = new ConcurrentHashMap<>();

    // ======================== strategy state ========================

    /**
     * Tracks monitors that have already fired an alarm under ONLY_ONCE or
     * SELECTIVE strategy. Presence in this set means "already alarmed in the
     * current cycle; suppress further alarms until recovery".
     */
    private final Set<Integer> alarmedSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Last alarm timestamp per monitor (epoch millis).
     * Reserved for future rate-limiting / cooldown extensions.
     */
    private final Map<Integer, Long> lastAlarmTimeMap = new ConcurrentHashMap<>();

    // ======================== alarm output queue ========================

    /**
     * Alarm messages awaiting persistence.
     * Consumers (data module, phase 2) will drain this queue.
     */
    private final LinkedBlockingQueue<ErrorMessageLog> alarmQueue = new LinkedBlockingQueue<>();

    // ======================== event listener ========================

    /**
     * Processes every {@link MonitorResultEvent} published by the result
     * dispatcher.
     * <p>
     * Only results with status {@link AssetState#WARNING} or
     * {@link AssetState#ERROR} are candidates for alarm generation.
     * Results with status {@link AssetState#NORMAL} are used to reset the
     * alarm cycle for ONLY_ONCE / SELECTIVE strategies.
     */
    @EventListener
    public void onMonitorResult(MonitorResultEvent event) {
        MonitorResult result = event.getResult();
        if (result == null || result.getMonitor() == null) {
            return;
        }

        Monitor<?> monitor = result.getMonitor();
        int monitorId = monitor.getId();
        AssetState status = result.getStatus();

        // ---- recovery handling (resets ONLY_ONCE / SELECTIVE trackers) ----
        if (status == AssetState.NORMAL) {
            boolean wasAlarmed = alarmedSet.remove(monitorId);
            lastAlarmTimeMap.remove(monitorId);
            if (wasAlarmed) {
                log.debug("Monitor {} recovered to NORMAL; alarm cycle reset.", monitorId);
            }
            return;
        }

        // ---- filter: only WARNING and ERROR trigger alarms ----
        if (status != AssetState.WARNING && status != AssetState.ERROR) {
            return;
        }

        // ---- find matching rule ----
        AlarmRule rule = ruleMap.get(monitorId);
        if (rule == null || !rule.isEnabled()) {
            return;
        }

        // ---- evaluate strategy ----
        AlarmStrategy strategy = rule.getStrategy();
        if (strategy == null) {
            strategy = AlarmStrategy.ONLY_ONCE; // default
        }

        boolean shouldAlarm = switch (strategy) {
            case CONTINUOUS -> true;
            case ONLY_ONCE -> !alarmedSet.contains(monitorId);
            case SELECTIVE -> !alarmedSet.contains(monitorId);
        };

        if (!shouldAlarm) {
            return;
        }

        // ---- dedup check ----
        int dedupWindow = rule.getDedupWindowSeconds();
        if (dedupWindow > 0 && suppressionChecker.isDuplicate(rule.getId(), monitorId, dedupWindow)) {
            log.debug("Alarm deduped: monitor={}, rule={}, window={}s", monitorId, rule.getId(), dedupWindow);
            return;
        }

        // ---- silence check ----
        Integer deviceId = monitor.getParentId() > 0 ? monitor.getParentId() : null;
        if (suppressionChecker.isSilenced(deviceId, monitorId)) {
            log.debug("Alarm silenced: monitor={}, device={}", monitorId, deviceId);
            return;
        }

        // ---- correlation ----
        String correlationGroup = correlator.correlate(deviceId);

        // ---- build alarm message ----
        ErrorMessageLog msg = buildErrorMessageLog(monitor, result, rule);
        msg.setDeviceId(deviceId);
        msg.setCorrelationGroup(correlationGroup);
        msg.setSuppressed(false);
        msg.setSilenced(false);
        msg.setEscalationLevel(0);

        // ---- record alarm for dedup tracking ----
        suppressionChecker.recordAlarmFired(rule.getId(), monitorId);

        // ---- update strategy trackers ----
        alarmedSet.add(monitorId);
        lastAlarmTimeMap.put(monitorId, msg.getAlarmTime());

        // ---- enqueue for persistence ----
        alarmQueue.offer(msg);
        log.info("Alarm fired: monitor={}, state={}, strategy={}, error={}",
                monitorId, status, strategy, msg.getError());
    }

    // ======================== message builder ========================

    /**
     * Constructs an {@link ErrorMessageLog} from the current monitor result.
     */
    private ErrorMessageLog buildErrorMessageLog(Monitor<?> monitor,
                                                  MonitorResult result,
                                                  AlarmRule rule) {
        ErrorMessageLog msg = new ErrorMessageLog();
        msg.setMonitorId(monitor.getId());
        msg.setMonitorName(monitor.getName());
        msg.setValue(result.getValue());
        msg.setState(result.getStatus());
        msg.setEventRankId(rule.getEventRankId());
        msg.setAlarmTime(System.currentTimeMillis());
        msg.setRecovered(false);

        // Build error message: prefer rule template, then result error, then default
        String error;
        if (rule.getMessageTemplate() != null && !rule.getMessageTemplate().isBlank()) {
            error = rule.getMessageTemplate()
                    .replace("${value}", String.valueOf(result.getValue()))
                    .replace("${name}", monitor.getName())
                    .replace("${caption}", monitor.getCaption() != null ? monitor.getCaption() : "");
        } else if (result.getError() != null) {
            error = result.getError();
        } else {
            error = "Monitor " + monitor.getName() + " is " + result.getStatus();
        }
        msg.setError(error);

        return msg;
    }

    // ======================== public API ========================

    /**
     * Returns the queue of alarm messages waiting to be persisted.
     * The data module (phase 2) should drain this queue periodically.
     */
    public LinkedBlockingQueue<ErrorMessageLog> getAlarmQueue() {
        return alarmQueue;
    }

    /**
     * Bulk-loads alarm rules (typically at application startup).
     * Replaces any previously loaded rules.
     *
     * @param rules the rules to load
     */
    public void loadRules(List<AlarmRule> rules) {
        ruleMap.clear();
        alarmedSet.clear();
        lastAlarmTimeMap.clear();
        if (rules != null) {
            for (AlarmRule rule : rules) {
                if (rule != null && rule.getMonitorId() > 0) {
                    ruleMap.put(rule.getMonitorId(), rule);
                }
            }
        }
        log.info("Loaded {} alarm rules.", ruleMap.size());
    }

    /**
     * Adds or replaces a single alarm rule at runtime.
     *
     * @param rule the rule to add
     */
    public void addRule(AlarmRule rule) {
        if (rule != null && rule.getMonitorId() > 0) {
            ruleMap.put(rule.getMonitorId(), rule);
            log.info("Alarm rule added/updated: monitorId={}, strategy={}",
                    rule.getMonitorId(), rule.getStrategy());
        }
    }

    /**
     * Removes the alarm rule associated with the given monitor.
     * Also clears any strategy trackers for that monitor.
     *
     * @param monitorId the monitor whose rule should be removed
     */
    public void removeRule(int monitorId) {
        ruleMap.remove(monitorId);
        alarmedSet.remove(monitorId);
        lastAlarmTimeMap.remove(monitorId);
        log.info("Alarm rule removed for monitorId={}.", monitorId);
    }
}
