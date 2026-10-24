package com.systar.monitor.control;

import java.util.List;

/**
 * Persistence interface for {@link ScheduledTaskLog} execution records.
 */
public interface ScheduledTaskLogRepository {

    /** Persists one execution log entry. */
    void saveLog(ScheduledTaskLog log);

    /**
     * Returns the most recent log entries for a given task,
     * ordered by execute time descending.
     *
     * @param taskId task id to filter on
     * @param limit  maximum number of rows to return
     */
    List<ScheduledTaskLog> findRecent(int taskId, int limit);
}
