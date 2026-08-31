package com.systar.monitor.result;

/**
 * Persistence interface for monitor sample data.
 * <p>
 * Implementations route typed sample values to the appropriate database table.
 */
public interface SampleRepository {

    void saveFloat(int monitorId, float value, long sampleTimeMs);

    void saveInt(int monitorId, int value, long sampleTimeMs);

    void saveBoolean(int monitorId, boolean value, long sampleTimeMs);

    void saveException(int monitorId, String error, long sampleTimeMs);
}
