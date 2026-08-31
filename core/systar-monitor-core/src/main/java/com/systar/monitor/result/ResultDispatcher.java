package com.systar.monitor.result;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

/**
 * Two-phase result dispatcher -- the core engine of the monitoring pipeline.
 *
 * <h3>Phase 1: Synchronous pre-processing (this class)</h3>
 * <ol>
 *   <li>{@code normalizeResult} -- sanity-checks value/error fields</li>
 *   <li>{@code setMonitorValue} or {@code setMonitorError} -- transform, change-detect, warn-evaluate</li>
 *   <li>{@code updateDetectTime} -- update last-detect timestamp (ignoring out-of-order data)</li>
 *   <li>{@code publishEvent} -- publish a {@link MonitorResultEvent}</li>
 * </ol>
 *
 * <h3>Phase 2: Asynchronous handling (Spring @EventListener beans)</h3>
 * <p>
 * Handlers such as {@link ResultPersistHandler}, AlarmHandler, and
 * LinkageHandler subscribe to {@link MonitorResultEvent} and perform
 * their work outside the dispatch thread.
 */
@Component
public class ResultDispatcher implements ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(ResultDispatcher.class);

    private ApplicationEventPublisher publisher;

    // ======================== ApplicationEventPublisherAware ========================

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    // ======================== dispatch entry point ========================

    /**
     * Dispatches a single monitor result through the full pre-processing pipeline.
     *
     * @param result the result to process
     */
    public void dispatch(MonitorResult result) {
        if (result == null || result.getMonitor() == null) {
            return;
        }

        normalizeResult(result);

        if (result.hasError()) {
            setMonitorError(result);
        } else {
            setMonitorValue(result);
        }

        updateDetectTime(result);
        publishEvent(result);
    }

    // ======================== Phase 1 internals ========================

    /**
     * Normalises null/empty fields so downstream logic can rely on
     * consistent input.
     * <ul>
     *   <li>If both value and error are null, error is left as-is
     *       (the result is simply empty).</li>
     *   <li>If error is a blank string, it is replaced with
     *       "unknown error".</li>
     * </ul>
     */
    private void normalizeResult(MonitorResult result) {
        String msg = result.getError();
        if (msg != null && msg.isBlank()) {
            result.setError("unknown error");
        }
    }

    /**
     * Processes a normal (non-error) value result.
     * <ol>
     *   <li>Apply SpEL value transformation via {@link Monitor#applyTransform}.</li>
     *   <li>Detect change by comparing with the previous value.</li>
     *   <li>Evaluate warning condition via {@link Monitor#evaluateWarnCondition}.</li>
     *   <li>Set monitor state to WARNING or NORMAL accordingly.</li>
     *   <li>Store the new value on the monitor.</li>
     *   <li>Clear any previous runtime description.</li>
     * </ol>
     */
    private void setMonitorValue(MonitorResult result) {
        Monitor<?> monitor = result.getMonitor();
        Object rawValue = result.getValue();

        // 1. Transform
        Object newValue = monitor.applyTransform(rawValue);
        result.setValue(newValue);

        // 2. Change detection
        Object oldValue = monitor.getValue();
        if (oldValue == null || !oldValue.equals(newValue)) {
            result.setChanged(true);
        }

        // 3. Warning evaluation
        boolean shouldWarn = monitor.evaluateWarnCondition(newValue);

        // 4. Determine status
        AssetState status = shouldWarn ? AssetState.WARNING : AssetState.NORMAL;
        result.setStatus(status);

        // 5. Store value and state on monitor (Asset.setState handles bubbling)
        monitor.setValue(newValue);
        monitor.setState(status);

        // 6. Clear runtime description
        monitor.setRuntimeDesc(null);
    }

    /**
     * Processes an error result.
     * <ol>
     *   <li>Set status to ERROR.</li>
     *   <li>If monitor state changed (or the error message changed), mark changed and
     *       update the runtime description.</li>
     *   <li>Propagate ERROR state to the monitor.</li>
     * </ol>
     */
    private void setMonitorError(MonitorResult result) {
        Monitor<?> monitor = result.getMonitor();
        AssetState current = monitor.getState();
        String error = result.getError();

        AssetState targetStatus = result.getStatus();
        if (targetStatus == null) {
            targetStatus = AssetState.ERROR;
        }
        result.setStatus(targetStatus);

        if (current != targetStatus) {
            monitor.setState(targetStatus);
            monitor.setRuntimeDesc(error);
            result.setChanged(true);
        } else {
            String existingDesc = monitor.getRuntimeDesc();
            if (existingDesc == null || !existingDesc.equals(error)) {
                monitor.setRuntimeDesc(error);
                result.setChanged(true);
            }
        }
    }

    /**
     * Updates the monitor's last-detect timestamp, but only if the sample
     * time is newer than the stored value. This prevents out-of-order /
     * historical data from overwriting the current timestamp.
     */
    private void updateDetectTime(MonitorResult result) {
        long sampleTime = result.getSampleTime();
        if (sampleTime <= 0) {
            sampleTime = System.currentTimeMillis();
        }
        Monitor<?> monitor = result.getMonitor();
        if (sampleTime > monitor.getLastDetectTimeMs()) {
            monitor.setLastDetectTimeMs(sampleTime);
        }
    }

    // ======================== event publishing ========================

    private void publishEvent(MonitorResult result) {
        if (publisher != null) {
            publisher.publishEvent(new MonitorResultEvent(this, result));
        } else {
            log.warn("ApplicationEventPublisher not set; dropping event for monitor: {}",
                    result.getMonitor());
        }
    }
}
