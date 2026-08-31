package com.systar.monitor.control;

import java.util.List;

/**
 * Persistence interface for {@link ScheduledTask} definitions.
 * <p>
 * Implementations load/save scheduled task definitions from the
 * {@code t_scheduled_task} table.
 */
public interface ScheduledTaskRepository {

    /** Returns all scheduled task definitions. */
    List<ScheduledTask> findAll();

    /** Inserts a new task; populates {@link ScheduledTask#getId()} on success. */
    void save(ScheduledTask task);

    /** Updates an existing task by id. */
    void update(ScheduledTask task);

    /** Deletes a task by id. */
    void deleteById(int taskId);

    /** Finds a task by id; returns null if not found. */
    ScheduledTask findById(int taskId);
}
