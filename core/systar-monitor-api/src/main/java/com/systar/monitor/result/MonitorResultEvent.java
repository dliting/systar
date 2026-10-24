package com.systar.monitor.result;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} published after the
 * {@link ResultDispatcher} has completed its synchronous pre-processing.
 * <p>
 * Phase-2 handlers (alarm, linkage, persistence, etc.) listen for this
 * event to perform their asynchronous work.
 */
public class MonitorResultEvent extends ApplicationEvent {

    private final MonitorResult result;

    public MonitorResultEvent(Object source, MonitorResult result) {
        super(source);
        this.result = result;
    }

    public MonitorResult getResult() {
        return result;
    }
}
