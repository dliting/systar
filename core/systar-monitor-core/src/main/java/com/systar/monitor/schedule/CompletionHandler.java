package com.systar.monitor.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handler invoked after each {@link DetectTask} completes.
 * <p>
 * Responsible for re-scheduling the next detection cycle based on the
 * monitor's detect interval. When the monitor is in ERROR state the retry
 * interval is used instead (whichever is larger).
 */
public class CompletionHandler {

    private static final Logger log = LoggerFactory.getLogger(CompletionHandler.class);

    /** Reference back to the scheduler for re-scheduling. */
    private final MonitorScheduler scheduler;

    public CompletionHandler(MonitorScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Called when a detection task has finished execution.
     * <p>
     * Re-schedules the task for the next detection cycle. The delay is
     * computed via {@link DetectTask#getNextDelayMs()} which accounts for
     * error-state retry intervals.
     *
     * @param task the completed task
     */
    public void onComplete(DetectTask task) {
        try {
            long delayMs = task.getNextDelayMs();
            if (delayMs > 0) {
                scheduler.reschedule(task, delayMs);
            } else {
                log.warn("Monitor {} has zero/negative interval; skipping reschedule.",
                        task.getMonitor());
            }
        } catch (Exception e) {
            log.error("Failed to reschedule task for monitor {}: {}",
                    task.getMonitor(), e.getMessage(), e);
        }
    }
}
