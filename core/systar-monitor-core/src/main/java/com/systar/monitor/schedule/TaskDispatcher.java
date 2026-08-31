package com.systar.monitor.schedule;

import com.systar.monitor.asset.MonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Concurrent execution controller that limits the number of parallel
 * detection tasks per {@link MonitorService} type.
 * <p>
 * Internally maintains a {@link Semaphore} per concrete MonitorService class
 * so that no single driver can monopolise the thread pool.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #submit(DetectTask)} -- acquires a permit and executes the task</li>
 *   <li>On completion the permit is released and the {@link CompletionHandler}
 *       is notified for re-scheduling</li>
 *   <li>{@link #shutdown()} -- gracefully shuts down the internal thread pool</li>
 * </ul>
 */
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);

    /** Maximum parallel detections per MonitorService type. */
    public static final int RUNNING_LIMIT_PER_TYPE = 10;

    /** Timeout in seconds to wait for executor termination on shutdown. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    /** Semaphore registry keyed by the concrete MonitorService class. */
    private final ConcurrentHashMap<Class<?>, Semaphore> semaphores = new ConcurrentHashMap<>();

    /** Worker thread pool. */
    private final ExecutorService executor;

    /** Callback invoked after each task completes. */
    private final CompletionHandler completionHandler;

    // ======================== constructor ========================

    public TaskDispatcher(CompletionHandler completionHandler) {
        this(completionHandler, defaultExecutor());
    }

    TaskDispatcher(CompletionHandler completionHandler, ExecutorService executor) {
        this.completionHandler = completionHandler;
        this.executor = executor;
    }

    private static ExecutorService defaultExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "detect-worker-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    // ======================== permit management ========================

    /**
     * Returns (or creates) the semaphore for the given service type.
     */
    private Semaphore semaphoreFor(Class<?> serviceType) {
        return semaphores.computeIfAbsent(serviceType,
                k -> new Semaphore(RUNNING_LIMIT_PER_TYPE, true));
    }

    // ======================== task submission ========================

    /**
     * Submits a detection task for execution.
     * <p>
     * The task will block until a permit for its service type is available,
     * then execute on the thread pool. After execution the permit is released
     * and the {@link CompletionHandler} is notified.
     *
     * @param task the task to execute
     */
    public void submit(DetectTask task) {
        Class<?> serviceType = resolveServiceType(task.getMonitor());
        Semaphore sem = semaphoreFor(serviceType);

        executor.execute(() -> {
            try {
                sem.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                task.run();
            } finally {
                sem.release();
                completionHandler.onComplete(task);
            }
        });
    }

    /**
     * Tries to submit a detection task without blocking.
     * If no permit is available the task is queued internally and will be
     * submitted when a permit becomes available.
     *
     * @param task the task to execute
     * @return {@code true} if submitted immediately, {@code false} if queued
     */
    public boolean trySubmit(DetectTask task) {
        Class<?> serviceType = resolveServiceType(task.getMonitor());
        Semaphore sem = semaphoreFor(serviceType);

        if (sem.tryAcquire()) {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    sem.release();
                    completionHandler.onComplete(task);
                }
            });
            return true;
        }
        // No permit available -- submit as blocking
        submit(task);
        return false;
    }

    // ======================== helpers ========================

    /**
     * Resolves the MonitorService class for a given monitor.
     * Falls back to the monitor's own class if no source service is set.
     */
    private Class<?> resolveServiceType(com.systar.monitor.asset.Monitor<?> monitor) {
        MonitorService source = monitor.getSource();
        return source != null ? source.getClass() : monitor.getClass();
    }

    // ======================== lifecycle ========================

    /**
     * Gracefully shuts down the thread pool.
     * Waits up to 10 seconds for running tasks to complete.
     */
    public void shutdown() {
        log.info("Shutting down TaskDispatcher...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("TaskDispatcher forced shutdown after {} s.", SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
