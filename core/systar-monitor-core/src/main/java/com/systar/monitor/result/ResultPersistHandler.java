package com.systar.monitor.result;

import com.systar.monitor.asset.Monitor;
import com.systar.monitor.asset.type.DataType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Phase-2 handler that persists monitor results to the database.
 * <p>
 * Uses a {@link BlockingQueue} to decouple the synchronous dispatch thread
 * from the potentially slow I/O of database writes. A background consumer
 * drains the queue in batches.
 *
 * <h3>Throttle strategy</h3>
 * A result is written only when one of the following is true:
 * <ul>
 *   <li>The {@code savingInterval} has elapsed since the last write for
 *       this monitor (time-based throttle).</li>
 *   <li>The value changed <b>and</b> the data type is BOOLEAN
 *       (state-change events should always be persisted).</li>
 * </ul>
 *
 * <h3>Routing by DataType</h3>
 * Results are routed to type-specific persistence logic (float, int,
 * boolean, exception) via the {@link SampleRepository} interface.
 */
@Component
public class ResultPersistHandler {

    private static final Logger log = LoggerFactory.getLogger(ResultPersistHandler.class);

    /** Maximum number of results to drain per batch. */
    private static final int BATCH_SIZE = 200;

    /** Queue capacity. */
    private static final int QUEUE_CAPACITY = 10_000;

    private final SampleRepository sampleRepository;
    private final BlockingQueue<MonitorResult> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private volatile boolean running;
    private Thread consumerThread;

    /**
     * Creates a new handler with the given sample repository.
     *
     * @param sampleRepository the repository for persisting sample data
     */
    public ResultPersistHandler(SampleRepository sampleRepository) {
        if (sampleRepository == null) {
            throw new NullPointerException("sampleRepository must not be null");
        }
        this.sampleRepository = sampleRepository;
    }

    /** Returns the current number of results waiting in the queue (for testing). */
    int getQueueSize() {
        return queue.size();
    }

    // ======================== lifecycle ========================

    @PostConstruct
    public void start() {
        running = true;
        consumerThread = new Thread(this::consumeLoop, "result-persist");
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

    // ======================== event listener (Phase 2) ========================

    /**
     * Receives {@link MonitorResultEvent} and decides whether the result
     * should be queued for persistence.
     */
    @EventListener
    public void onMonitorResultEvent(MonitorResultEvent event) {
        MonitorResult result = event.getResult();
        if (result == null) {
            return;
        }

        Monitor<?> monitor = result.getMonitor();
        if (monitor == null) {
            return;
        }

        if (!shouldPersist(result, monitor)) {
            return;
        }

        boolean offered = queue.offer(result);
        if (!offered) {
            log.warn("Persist queue full; dropping result for monitor: {}", monitor);
        }
    }

    // ======================== throttle logic ========================

    /**
     * Determines whether the result should be persisted.
     * <p>
     * Persists when:
     * <ul>
     *   <li>The saving interval has elapsed, <b>or</b></li>
     *   <li>The value changed and the monitor data type is BOOLEAN.</li>
     * </ul>
     */
    private boolean shouldPersist(MonitorResult result, Monitor<?> monitor) {
        long now = System.currentTimeMillis();
        if (monitor.shouldSave(now)) {
            return true;
        }
        if (result.isChanged()) {
            DataType dataType = resolveDataType(monitor);
            if (dataType == DataType.BOOLEAN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the data type for the monitor.
     * Falls back to STRING if no type metadata is available.
     */
    private DataType resolveDataType(Monitor<?> monitor) {
        Object raw = monitor.getMetadata("dataType");
        if (raw instanceof DataType dt) {
            return dt;
        }
        // Infer from current value
        Object value = monitor.getValue();
        if (value instanceof Boolean) {
            return DataType.BOOLEAN;
        }
        if (value instanceof Integer || value instanceof Long) {
            return DataType.INT;
        }
        if (value instanceof Float || value instanceof Double) {
            return DataType.FLOAT;
        }
        return DataType.STRING;
    }

    // ======================== background consumer ========================

    private void consumeLoop() {
        List<MonitorResult> batch = new ArrayList<>(BATCH_SIZE);
        while (running || !queue.isEmpty()) {
            try {
                // Block until at least one result is available
                MonitorResult first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);

                persistBatch(batch);

                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    drainBatchOnShutdown(batch);
                }
                break;
            } catch (Exception e) {
                log.error("Error persisting monitor result batch", e);
                batch.clear();
            }
        }
    }

    /**
     * Persists any remaining items in the current batch and drains the queue
     * during graceful shutdown.
     */
    private void drainBatchOnShutdown(List<MonitorResult> batch) {
        persistBatch(batch);
        batch.clear();
        List<MonitorResult> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        persistBatch(remaining);
    }

    // ======================== persistence ========================

    /**
     * Routes each result in the batch to a type-specific persistence method.
     */
    private void persistBatch(List<MonitorResult> batch) {
        for (MonitorResult result : batch) {
            try {
                persistOne(result);
            } catch (Exception e) {
                log.error("Failed to persist result for monitor: {}",
                        result.getMonitor(), e);
            }
        }
    }

    private void persistOne(MonitorResult result) {
        Monitor<?> monitor = result.getMonitor();
        DataType dataType = resolveDataType(monitor);

        switch (dataType) {
            case FLOAT -> persistFloat(monitor, result);
            case INT -> persistInt(monitor, result);
            case BOOLEAN -> persistBoolean(monitor, result);
            case STRING, TIMESPAN -> persistString(monitor, result);
        }

        // Update the last-saving timestamp after successful write
        monitor.setLastSavingTimeMs(System.currentTimeMillis());
    }

    // ---- type-specific persistence ----

    private void persistFloat(Monitor<?> monitor, MonitorResult result) {
        Object value = result.getValue();
        if (value instanceof Number num) {
            sampleRepository.saveFloat(monitor.getId(), num.floatValue(), result.getSampleTime());
        }
    }

    private void persistInt(Monitor<?> monitor, MonitorResult result) {
        Object value = result.getValue();
        if (value instanceof Number num) {
            sampleRepository.saveInt(monitor.getId(), num.intValue(), result.getSampleTime());
        }
    }

    private void persistBoolean(Monitor<?> monitor, MonitorResult result) {
        Object value = result.getValue();
        if (value instanceof Boolean bool) {
            sampleRepository.saveBoolean(monitor.getId(), bool, result.getSampleTime());
        }
    }

    private void persistString(Monitor<?> monitor, MonitorResult result) {
        if (result.hasError()) {
            sampleRepository.saveException(monitor.getId(), result.getError(), result.getSampleTime());
        }
    }
}
