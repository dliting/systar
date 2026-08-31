package com.systar.monitor.linkage;

/**
 * Persistence interface for linkage execution logs.
 * <p>
 * Implementations record each linkage trigger event in the database.
 */
public interface LinkageRepository {

    void saveLinkageLog(int ruleId, int causeMonitorId,
                        int effectMonitorId, String effectCommand, boolean success);
}
