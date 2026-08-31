package com.systar.monitor.result;

/**
 * Interface for carrying monitor detection results.
 * <p>
 * Implementations hold the sampled value, error information, and timestamp.
 */
public interface IMonitorResult {

    void setValue(Object value);

    Object getValue();

    void setError(String error);

    String getError();

    void setSampleTime(long timestamp);

    long getSampleTime();
}
