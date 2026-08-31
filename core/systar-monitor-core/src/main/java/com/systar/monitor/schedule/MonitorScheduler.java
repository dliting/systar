package com.systar.monitor.schedule;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.Monitor;
import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.result.ResultDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Main scheduling entry-point for the monitoring pipeline.
 * <p>
 * Manages the lifecycle of all detection tasks:
 * <ul>
 *   <li>{@link #start()} -- iterates all monitors in the {@link AssetStore}
 *       and schedules an initial {@link DetectTask} for each one, with a
 *       randomly staggered initial delay to avoid a thundering-herd start.</li>
 *   <li>{@link #stop()} -- cancels all scheduled tasks and shuts down the
 *       thread pools.</li>
 *   <li>{@link #scheduleMonitor(Monitor)} -- schedules a single monitor.</li>
 *   <li>{@link #unscheduleMonitor(int)} -- cancels a scheduled monitor by id.</li>
 *   <li>{@link #reschedule(DetectTask, long)} -- re-schedules a completed task
 *       (called by {@link CompletionHandler}).</li>
 * </ul>
 */
@Component
public class MonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

    /** Maximum random initial-delay jitter (ms) to spread start-up load. */
    private static final long MAX_INITIAL_JITTER_MS = 5_000L;

    /** Timeout in seconds to wait for scheduler termination on shutdown. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    // ---- collaborators (injected) ----
    private final AssetStore assetStore;
    private final ResultDispatcher resultDispatcher;

    // ---- internal components ----
    private final TaskDispatcher taskDispatcher;
    private final CompletionHandler completionHandler;

    // ---- scheduling infrastructure ----
    private final ScheduledExecutorService scheduler;
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    // ======================== constructor ========================

    public MonitorScheduler(AssetStore assetStore, ResultDispatcher resultDispatcher) {
        this.assetStore = assetStore;
        this.resultDispatcher = resultDispatcher;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.completionHandler = new CompletionHandler(this);
        this.taskDispatcher = new TaskDispatcher(completionHandler);
    }

    // ======================== lifecycle ========================

    /**
     * Starts the scheduler: enumerates all monitors in the asset store and
     * creates an initial detection task for each one.
     * <p>
     * Each task is given a small random delay so that monitors of the same
     * type do not all fire simultaneously.
     */
    @PostConstruct
    public void start() {
        log.info("MonitorScheduler starting...");
        Collection<MonitorService> services = assetStore.getAssetsByKind(AssetKind.SERVICE)
                .stream()
                .filter(a -> a instanceof MonitorService)
                .map(a -> (MonitorService) a)
                .toList();

        for (MonitorService service : services) {
            for (Monitor<?> monitor : service.getMonitors()) {
                scheduleMonitor(monitor);
            }
        }
        log.info("MonitorScheduler started with {} scheduled monitors.", scheduledTasks.size());
    }

    /**
     * Stops the scheduler: cancels all pending tasks and shuts down thread pools.
     */
    @PreDestroy
    public void stop() {
        log.info("MonitorScheduler stopping ({} pending tasks)...", scheduledTasks.size());
        scheduledTasks.values().forEach(f -> f.cancel(false));
        scheduledTasks.clear();
        taskDispatcher.shutdown();
        DetectTask.shutdownExecutor();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("MonitorScheduler stopped.");
    }

    // ======================== scheduling ========================

    /**
     * Creates a {@link DetectTask} for the given monitor and schedules it
     * with a random initial jitter to spread the start-up load.
     *
     * @param monitor the monitor to schedule
     */
    public void scheduleMonitor(Monitor<?> monitor) {
        if (monitor == null || scheduledTasks.containsKey(monitor.getId())) {
            return;
        }

        DetectTask task = new DetectTask(monitor, resultDispatcher);
        long jitter = ThreadLocalRandom.current().nextLong(0, MAX_INITIAL_JITTER_MS);

        ScheduledFuture<?> future = scheduler.schedule(
                () -> taskDispatcher.submit(task),
                jitter, TimeUnit.MILLISECONDS);

        scheduledTasks.put(monitor.getId(), future);
        log.debug("Scheduled monitor {} (initial delay {} ms).", monitor, jitter);
    }

    /**
     * Cancels the scheduled detection task for the monitor with the given id.
     *
     * @param monitorId the monitor id to unschedule
     */
    public void unscheduleMonitor(int monitorId) {
        ScheduledFuture<?> future = scheduledTasks.remove(monitorId);
        if (future != null) {
            future.cancel(false);
            log.debug("Unscheduled monitor id={}.", monitorId);
        }
    }

    /**
     * Immediately runs a manual detection for the given monitor, bypassing the normal
     * schedule. The monitor's existing scheduled task (if any) is cancelled, and a
     * new {@link DetectTask} is submitted with zero delay.
     * <p>
     * After completion the normal {@link CompletionHandler} re-schedules the next
     * periodic detection, effectively postponing the next auto-detect from now.
     * <p>
     * Must not be called when the monitor is already {@link Monitor#isDetecting()}.
     *
     * @param monitor the monitor to detect immediately
     * @throws IllegalStateException if the monitor is already detecting
     */
    public void detectImmediately(Monitor<?> monitor) {
        if (!monitor.trySetDetecting()) {
            throw new IllegalStateException(
                    "Monitor " + monitor.getId() + " is already detecting.");
        }

        // Cancel the existing scheduled task so its next-fire time is discarded.
        ScheduledFuture<?> old = scheduledTasks.remove(monitor.getId());
        if (old != null) {
            old.cancel(false);
        }

        // Submit a manual task with zero delay so the worker picks it up instantly.
        DetectTask task = new DetectTask(monitor, resultDispatcher, true /*manual*/);
        reschedule(task, 0);
    }

    /**
     * Immediately executes a control command and re-detects the state, bypassing the
     * normal schedule. The control's existing scheduled task (if any) is cancelled, and
     * a new {@link DetectTask} (with command) is submitted with zero delay.
     * <p>
     * After completion the normal {@link CompletionHandler} re-schedules the next
     * periodic detection, effectively postponing the next auto-detect from now.
     *
     * @param control the control to execute
     * @param command the command to send before re-detecting
     * @throws IllegalStateException if the control is already detecting
     */
    public void controlImmediately(Control control, String command) {
        if (!control.trySetDetecting()) {
            throw new IllegalStateException(
                    "Control " + control.getId() + " is already detecting.");
        }

        ScheduledFuture<?> old = scheduledTasks.remove(control.getId());
        if (old != null) {
            old.cancel(false);
        }

        DetectTask task = new DetectTask(control, resultDispatcher, true /*manual*/, command);
        reschedule(task, 0);
    }

    /**
     * Re-schedules a completed detection task for its next cycle.
     * <p>
     * Called by {@link CompletionHandler} after a task finishes.
     *
     * @param task    the completed task to re-schedule
     * @param delayMs the delay before the next execution
     */
    public void reschedule(DetectTask task, long delayMs) {
        Monitor<?> monitor = task.getMonitor();
        int monitorId = monitor.getId();

        ScheduledFuture<?> future = scheduler.schedule(
                () -> taskDispatcher.submit(task),
                delayMs, TimeUnit.MILLISECONDS);

        scheduledTasks.put(monitorId, future);
    }
}
