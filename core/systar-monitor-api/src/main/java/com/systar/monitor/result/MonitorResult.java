package com.systar.monitor.result;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Monitor;

/**
 * Default implementation of {@link IMonitorResult}.
 * <p>
 * Carries the sampled value or error from a detection cycle, together with
 * the associated monitor, computed status, change flag, and sample timestamp.
 */
public class MonitorResult implements IMonitorResult {

    private final Monitor<?> monitor;

    private volatile Object value;
    private volatile String error;
    private volatile boolean changed;
    private volatile AssetState status;
    private volatile long sampleTime;

    // ======================== constructors ========================

    /**
     * Normal-value result.
     *
     * @param monitor the monitor that produced this result
     * @param value   the sampled value (may be {@code null})
     */
    public MonitorResult(Monitor<?> monitor, Object value) {
        this.monitor = monitor;
        this.value = value;
        this.sampleTime = System.currentTimeMillis();
    }

    /**
     * Error result.
     *
     * @param monitor the monitor that produced this result
     * @param error   the error description (treated as a String when ambiguous)
     */
    public MonitorResult(Monitor<?> monitor, String error) {
        this.monitor = monitor;
        this.error = error;
        this.sampleTime = System.currentTimeMillis();
    }

    /**
     * Empty result -- only the timestamp is set.
     *
     * @param monitor the monitor that produced this result
     */
    public MonitorResult(Monitor<?> monitor) {
        this.monitor = monitor;
        this.sampleTime = System.currentTimeMillis();
    }

    // ======================== convenience ========================

    /** Returns {@code true} when this result carries an error. */
    public boolean hasError() {
        return error != null;
    }

    // ======================== IMonitorResult ========================

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String getError() {
        return error;
    }

    @Override
    public void setError(String error) {
        this.error = error;
    }

    @Override
    public long getSampleTime() {
        return sampleTime;
    }

    @Override
    public void setSampleTime(long timestamp) {
        this.sampleTime = timestamp;
    }

    // ======================== extended accessors ========================

    public Monitor<?> getMonitor() {
        return monitor;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public AssetState getStatus() {
        return status;
    }

    public void setStatus(AssetState status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "MonitorResult{"
                + "monitor=" + monitor
                + ", value=" + value
                + ", error='" + error + '\''
                + ", changed=" + changed
                + ", status=" + status
                + ", sampleTime=" + sampleTime
                + '}';
    }
}
