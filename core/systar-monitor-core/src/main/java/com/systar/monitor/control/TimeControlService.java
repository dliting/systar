package com.systar.monitor.control;

import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.Control;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Spring-managed service that schedules and executes recurring control commands.
 * <p>
 * Each {@link ScheduledTask} is converted into a {@link ScheduledFuture} driven
 * by a single-thread {@link ScheduledExecutorService}. The executor calculates
 * the delay to the next cron fire time and reschedules itself after every
 * successful execution.
 * <p>
 * Execution logs are persisted to the database via
 * {@link ScheduledTaskLogRepository} and also accumulated in a bounded
 * {@link BlockingQueue} for quick in-memory access.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #loadTasks(List)} -- bulk-load and schedule tasks (typically at
 *       application start-up).</li>
 *   <li>{@link #addTask(ScheduledTask)} / {@link #removeTask(int)} --
 *       incremental management.</li>
 *   <li>{@link #enableTask(int)} / {@link #disableTask(int)} -- toggle without
 *       removing the task definition.</li>
 *   <li>{@link #shutdown()} -- cancels all futures and stops the executor
 *       (called automatically via {@code @PreDestroy}).</li>
 * </ol>
 */
@Component
public class TimeControlService {

    private static final Logger log = LoggerFactory.getLogger(TimeControlService.class);

    /** Maximum number of log entries kept in memory. */
    private static final int LOG_CAPACITY = 10_000;

    /** Timeout in seconds to wait for executor termination on shutdown. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    // ======================== collaborators ========================

    private final AssetStore assetStore;

    // ======================== internals ========================

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "time-control-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** Tracks currently-scheduled futures keyed by task id. */
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> scheduledFutures =
            new ConcurrentHashMap<>();

    /** Stores task definitions keyed by task id (for enable/disable look-ups). */
    private final ConcurrentHashMap<Integer, ScheduledTask> taskRegistry =
            new ConcurrentHashMap<>();

    /** In-memory execution log buffer (latest N retained for quick access; full history in DB). */
    private final BlockingQueue<ScheduledTaskLog> logQueue =
            new LinkedBlockingQueue<>(LOG_CAPACITY);

    /** Optional persistence sink for execution logs (null = in-memory only). */
    private final ScheduledTaskLogRepository logRepository;

    /** Optional persistence for task definition changes (null = in-memory only). */
    private final ScheduledTaskRepository taskRepository;

    // ======================== constructor ========================

    /** Test-only constructor without persistence (logs stay in memory). */
    public TimeControlService(AssetStore assetStore) {
        this(assetStore, null, null);
    }

    /** Test constructor with log repo only. */
    public TimeControlService(AssetStore assetStore,
                              ScheduledTaskLogRepository logRepository) {
        this(assetStore, logRepository, null);
    }

    @Autowired
    public TimeControlService(AssetStore assetStore,
                              @Autowired(required = false) ScheduledTaskLogRepository logRepository,
                              @Autowired(required = false) ScheduledTaskRepository taskRepository) {
        this.assetStore      = assetStore;
        this.logRepository   = logRepository;
        this.taskRepository  = taskRepository;
    }

    // ======================== bulk operations ========================

    /**
     * Loads and schedules all given tasks.
     * Any previously scheduled tasks are cancelled first.
     *
     * @param tasks the tasks to load
     */
    public void loadTasks(List<ScheduledTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        // Cancel existing schedules
        for (Map.Entry<Integer, ScheduledFuture<?>> entry : scheduledFutures.entrySet()) {
            entry.getValue().cancel(false);
        }
        scheduledFutures.clear();
        taskRegistry.clear();

        for (ScheduledTask task : tasks) {
            taskRegistry.put(task.getId(), task);
            if (task.isEnabled()) {
                scheduleTask(task);
            }
        }
        log.info("Loaded {} scheduled control task(s), {} enabled.",
                tasks.size(), scheduledFutures.size());
    }

    // ======================== single-task operations ========================

    /**
     * Adds a single task and schedules it if enabled.
     *
     * @param task the task to add
     */
    public void addTask(ScheduledTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task must not be null.");
        }
        if (taskRegistry.containsKey(task.getId())) {
            throw new IllegalArgumentException(
                    "Task already exists (id = " + task.getId() + ").");
        }
        taskRegistry.put(task.getId(), task);
        if (task.isEnabled()) {
            scheduleTask(task);
        }
        log.info("Added scheduled task [{}] id={}.", task.getName(), task.getId());
    }

    /**
     * Cancels and removes a task by id.
     *
     * @param taskId the task id to remove
     */
    public void removeTask(int taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
        ScheduledTask removed = taskRegistry.remove(taskId);
        if (removed != null) {
            log.info("Removed scheduled task [{}] id={}.", removed.getName(), taskId);
        }
    }

    /**
     * Enables a previously added task, starting its schedule.
     *
     * @param taskId the task id
     */
    public void enableTask(int taskId) {
        ScheduledTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("enableTask: task not found (id={}).", taskId);
            return;
        }
        if (task.isEnabled()) {
            return; // already enabled
        }
        task.setEnabled(true);
        scheduleTask(task);
        log.info("Enabled scheduled task [{}] id={}.", task.getName(), taskId);
        persistEnabledState(task);
    }

    /**
     * Disables a previously added task, cancelling its current schedule.
     *
     * @param taskId the task id
     */
    public void disableTask(int taskId) {
        ScheduledTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("disableTask: task not found (id={}).", taskId);
            return;
        }
        if (!task.isEnabled()) {
            return; // already disabled
        }
        task.setEnabled(false);
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
        log.info("Disabled scheduled task [{}] id={}.", task.getName(), taskId);
        persistEnabledState(task);
    }

    // ======================== persistence helper ========================

    private void persistEnabledState(ScheduledTask task) {
        if (taskRepository != null) {
            try {
                taskRepository.update(task);
            } catch (Exception e) {
                log.warn("Failed to persist enabled state for task '{}' (id={}): {}",
                        task.getName(), task.getId(), e.getMessage());
            }
        }
    }

    // ======================== lifecycle ========================

    /**
     * Stops all scheduled tasks and shuts down the executor.
     * Called automatically by Spring on container shutdown.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down TimeControlService...");
        for (Map.Entry<Integer, ScheduledFuture<?>> entry : scheduledFutures.entrySet()) {
            entry.getValue().cancel(false);
        }
        scheduledFutures.clear();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("TimeControlService executor did not terminate within {} s.", SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("TimeControlService shut down complete.");
    }

    // ======================== log access ========================

    /**
     * Returns the in-memory log queue for external consumption.
     *
     * @return unmodifiable view of the log queue
     */
    public BlockingQueue<ScheduledTaskLog> getLogQueue() {
        return logQueue;
    }

    // ======================== internal scheduling ========================

    /**
     * Parses the cron expression of the given task and submits a self-rescheduling
     * runnable to the executor.
     */
    private void scheduleTask(ScheduledTask task) {
        CronExpression cron;
        try {
            cron = CronExpression.parse(task.getCronExpression());
        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression for task [{}] id={}: '{}'",
                    task.getName(), task.getId(), task.getCronExpression(), e);
            return;
        }

        // Cancel any existing schedule for this task
        ScheduledFuture<?> existing = scheduledFutures.remove(task.getId());
        if (existing != null) {
            existing.cancel(false);
        }

        long initialDelay = computeDelay(cron);
        if (initialDelay < 0) {
            log.warn("Cron expression yields no future execution for task [{}] id={}.",
                    task.getName(), task.getId());
            return;
        }

        SelfReschedulingRunnable runnable =
                new SelfReschedulingRunnable(task, cron);

        ScheduledFuture<?> future = executor.schedule(runnable, initialDelay,
                TimeUnit.MILLISECONDS);
        scheduledFutures.put(task.getId(), future);
    }

    /**
     * Computes the delay in milliseconds from now to the next cron fire time.
     */
    private long computeDelay(CronExpression cron) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = cron.next(now);
        if (next == null) {
            return -1;
        }
        return Duration.between(now, next).toMillis();
    }

    // ======================== execution ========================

    /**
     * Executes the control command for the given task and records a log entry.
     */
    private void executeTask(ScheduledTask task) {
        long startTime = System.currentTimeMillis();
        ScheduledTaskLog logEntry = new ScheduledTaskLog();
        logEntry.setTaskId(task.getId());
        logEntry.setTaskName(task.getName());
        logEntry.setControlId(task.getControlId());
        logEntry.setCommand(task.getCommand());
        logEntry.setExecuteTime(startTime);

        try {
            Asset<?> asset = assetStore.findAsset(task.getControlId());
            if (asset == null) {
                throw new IllegalStateException(
                        "Control asset not found (id=" + task.getControlId() + ").");
            }
            if (!(asset instanceof Control control)) {
                throw new IllegalStateException(
                        "Asset is not a Control (id=" + task.getControlId()
                                + ", kind=" + asset.getKind() + ").");
            }
            control.execute(task.getCommand());

            logEntry.setSuccess(true);
            log.info("Executed scheduled task [{}] id={} on control id={}, command='{}'.",
                    task.getName(), task.getId(), task.getControlId(), task.getCommand());
        } catch (Exception e) {
            logEntry.setSuccess(false);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Failed to execute scheduled task [{}] id={} on control id={}: {}",
                    task.getName(), task.getId(), task.getControlId(), e.getMessage(), e);
        }

        // Offer to log queue; drop oldest if full
        if (!logQueue.offer(logEntry)) {
            logQueue.poll(); // discard oldest
            logQueue.offer(logEntry);
        }

        // Persist to database; wrap to keep scheduler alive if DB write fails
        if (logRepository != null) {
            try {
                logRepository.saveLog(logEntry);
            } catch (Exception e) {
                log.error("Failed to persist execution log for task '{}': {}",
                        logEntry.getTaskName(), e.getMessage());
            }
        }
    }

    // ======================== inner classes ========================

    /**
     * A {@link Runnable} that executes a control command and then reschedules
     * itself for the next cron fire time.
     */
    private class SelfReschedulingRunnable implements Runnable {

        private final ScheduledTask task;
        private final CronExpression cron;

        SelfReschedulingRunnable(ScheduledTask task, CronExpression cron) {
            this.task = task;
            this.cron = cron;
        }

        @Override
        public void run() {
            // Execute only if the task is still enabled
            if (!task.isEnabled()) {
                scheduledFutures.remove(task.getId());
                return;
            }

            try {
                executeTask(task);
            } finally {
                // Reschedule for the next cron fire time
                long delay = computeDelay(cron);
                if (delay >= 0 && task.isEnabled()) {
                    ScheduledFuture<?> nextFuture = executor.schedule(this, delay,
                            TimeUnit.MILLISECONDS);
                    scheduledFutures.put(task.getId(), nextFuture);
                } else {
                    scheduledFutures.remove(task.getId());
                }
            }
        }
    }
}
