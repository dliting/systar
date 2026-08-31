package com.systar.monitor.schedule;

import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.Monitor;
import com.systar.monitor.result.IMonitorResult;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A detection task that samples a single {@link Monitor} and dispatches the
 * result through the two-phase pipeline via {@link ResultDispatcher}.
 * <p>
 * The detect call is wrapped with a timeout based on
 * {@link Monitor#getDetectTimeoutMs()}. If detect() does not return within
 * that time, the underlying thread is interrupted and an error result is
 * dispatched instead.
 * <p>
 * Implements {@link Runnable} so it can be submitted directly to an
 * {@link ExecutorService}.
 */
public class DetectTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DetectTask.class);

    /** Retry interval (ms) used when the monitor is in ERROR state. */
    public static final long RETRY_INTERVAL_MS = 180_000L;

    private static final int MAX_DETECT_THREADS =
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    private static final AtomicLong DETECT_THREAD_SEQ = new AtomicLong(0);

    /** Bounded daemon executor for running detect() with timeout enforcement.
     *  Separated from TaskDispatcher's pool to avoid semaphore nesting.
     *  Uses CallerRunsPolicy so a timeout never silently drops a detect. */
    private static final ThreadPoolExecutor DETECT_EXECUTOR = new ThreadPoolExecutor(
            0, MAX_DETECT_THREADS,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "detect-" + DETECT_THREAD_SEQ.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final Monitor<?>       monitor;
    private final ResultDispatcher resultDispatcher;
    /**
     * True when this task was triggered by a manual refresh (not the periodic scheduler).
     * Manual tasks clear the monitor's {@link Monitor#isDetecting()} flag on completion.
     */
    private final boolean manual;

    /**
     * When non-null, the task will first execute this command on the control
     * (via {@link Control#execute(String)}) before running detection.
     */
    private final String controlCommand;

    /** The most recent result produced by this task. */
    private volatile MonitorResult lastResult;

    public DetectTask(Monitor<?> monitor, ResultDispatcher resultDispatcher) {
        this(monitor, resultDispatcher, false, null);
    }

    public DetectTask(Monitor<?> monitor, ResultDispatcher resultDispatcher, boolean manual) {
        this(monitor, resultDispatcher, manual, null);
    }

    /**
     * Creates a task that optionally executes a control command before detecting.
     *
     * @param monitor the monitor (must be a {@link Control} if command is non-null)
     * @param resultDispatcher dispatches the detection result
     * @param manual true for manual (user-triggered) tasks
     * @param controlCommand the command to execute before detection, or null for detect-only
     */
    public DetectTask(Monitor<?> monitor, ResultDispatcher resultDispatcher,
                      boolean manual, String controlCommand) {
        if (monitor == null) {
            throw new IllegalArgumentException("monitor must not be null");
        }
        if (resultDispatcher == null) {
            throw new IllegalArgumentException("resultDispatcher must not be null");
        }
        if (controlCommand != null) {
            if (controlCommand.isBlank()) {
                throw new IllegalArgumentException("controlCommand must not be blank");
            }
            if (!(monitor instanceof Control)) {
                throw new IllegalArgumentException(
                        "controlCommand requires a Control monitor, got " + monitor.getClass().getSimpleName());
            }
        }
        this.monitor          = monitor;
        this.resultDispatcher = resultDispatcher;
        this.manual           = manual;
        this.controlCommand   = controlCommand;
    }

    // ======================== Runnable ========================

    /**
     * Executes one detection cycle with timeout enforcement:
     * <ol>
     *   <li>Creates a fresh {@link MonitorResult}.</li>
     *   <li>Submits {@link Monitor#detect(IMonitorResult)} to a dedicated executor
     *       with a timeout of {@link Monitor#getDetectTimeoutMs()}.</li>
     *   <li>On timeout, interrupts the detect thread and dispatches an error result.</li>
     *   <li>On success, dispatches the result through {@link ResultDispatcher#dispatch}.</li>
     * </ol>
     */
    @Override
    public void run() {
        MonitorResult result = new MonitorResult(monitor);
        // Sub-thread error captured here; main thread reads after future.get()
        AtomicReference<String> subThreadError = new AtomicReference<>();

        try {
            if (controlCommand != null) {
                ((Control) monitor).execute(controlCommand);
            }

            Future<?> future = DETECT_EXECUTOR.submit(() -> {
                try {
                    monitor.detect(result);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null || msg.isBlank()) msg = e.getClass().getName();
                    subThreadError.set(msg);
                }
            });

            try {
                future.get(monitor.getDetectTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                result.setError("检测超时 (" + monitor.getDetectTimeoutMs() + "ms)");
                log.warn("Detect timeout for monitor {}: {}ms", monitor, monitor.getDetectTimeoutMs());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause != null && subThreadError.get() == null) {
                    String msg = cause.getMessage();
                    if (msg == null || msg.isBlank()) msg = cause.getClass().getName();
                    subThreadError.set(msg);
                }
            }

            // Apply sub-thread error to result (main-thread only, no race)
            if (subThreadError.get() != null && result.getError() == null) {
                result.setError(subThreadError.get());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            result.setError("检测被中断");
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isBlank()) {
                errorMsg = e.getClass().getName();
            }
            result.setError(errorMsg);
            log.trace("Detect task error for monitor {}: {}", monitor, errorMsg, e);
        } finally {
            if (manual) {
                monitor.setDetecting(false);
            }
        }
        lastResult = result;
        resultDispatcher.dispatch(result);
    }

    // ======================== lifecycle ========================

    /**
     * Shuts down the detect executor. Called during application shutdown.
     */
    public static void shutdownExecutor() {
        DETECT_EXECUTOR.shutdownNow();
    }

    // ======================== accessors ========================

    public boolean isManual() {
        return manual;
    }

    /**
     * Returns the monitor associated with this task.
     *
     * @return the monitor (never null)
     */
    public Monitor<?> getMonitor() {
        return monitor;
    }

    /**
     * Returns the result produced by the most recent execution, or null if
     * the task has not yet run.
     *
     * @return the last result, or null
     */
    public MonitorResult getLastResult() {
        return lastResult;
    }

    /**
     * Returns the detect interval for this task's monitor, in milliseconds.
     */
    public long getDetectIntervalMs() {
        return monitor.getDetectInterval() != null
                ? monitor.getDetectInterval().toMillis()
                : 0;
    }

    /**
     * Computes the delay until the next detection cycle.
     * When the monitor is in ERROR state the delay is the larger of
     * {@link #RETRY_INTERVAL_MS} and the configured detect interval.
     *
     * @return delay in milliseconds
     */
    public long getNextDelayMs() {
        long interval = getDetectIntervalMs();
        if (monitor.getState() == com.systar.monitor.asset.AssetState.ERROR) {
            return Math.max(RETRY_INTERVAL_MS, interval);
        }
        return interval;
    }
}
