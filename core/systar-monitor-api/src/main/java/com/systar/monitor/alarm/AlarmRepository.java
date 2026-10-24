package com.systar.monitor.alarm;

/**
 * Persistence interface for alarm messages.
 * <p>
 * Implementations persist alarm events produced by {@code AlarmHandler}
 * to the database.
 */
public interface AlarmRepository {

    void saveAlarm(ErrorMessageLog message);
}
