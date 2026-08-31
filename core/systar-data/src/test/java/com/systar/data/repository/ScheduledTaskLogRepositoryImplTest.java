package com.systar.data.repository;

import com.systar.data.test.DataTestApplication;
import com.systar.monitor.control.ScheduledTaskLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ScheduledTaskLogRepositoryImplTest {

    @Autowired
    private ScheduledTaskLogRepositoryImpl repository;

    private ScheduledTaskLog makeLog(int taskId, String taskName, int controlId,
                                     String command, boolean success) {
        ScheduledTaskLog log = new ScheduledTaskLog();
        log.setTaskId(taskId);
        log.setTaskName(taskName);
        log.setControlId(controlId);
        log.setCommand(command);
        log.setExecuteTime(System.currentTimeMillis());
        log.setSuccess(success);
        log.setErrorMessage(success ? null : " simulated failure");
        return log;
    }

    @Test
    void saveLogSetsId() {
        ScheduledTaskLog entry = makeLog(1, "Test task", 5001, "ON", true);
        assertThat(entry.getId()).isEqualTo(0L);

        repository.saveLog(entry);

        assertThat(entry.getId()).isGreaterThan(0L);
    }

    @Test
    void findRecentReturnsLogsForTask() {
        // Insert two logs for task 10, one for task 11
        repository.saveLog(makeLog(10, "Task A", 5002, "ON",  true));
        repository.saveLog(makeLog(10, "Task A", 5002, "OFF", false));
        repository.saveLog(makeLog(11, "Task B", 5003, "ON",  true));

        List<ScheduledTaskLog> recent = repository.findRecent(10, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent).allMatch(l -> l.getTaskId() == 10);
        // Ordered by execute_time DESC
        assertThat(recent.get(0).getExecuteTime())
                .isGreaterThanOrEqualTo(recent.get(1).getExecuteTime());
    }

    @Test
    void findRecentRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            repository.saveLog(makeLog(20, "Limit test", 5004, "CMD" + i, true));
        }

        List<ScheduledTaskLog> recent = repository.findRecent(20, 2);
        assertThat(recent).hasSize(2);
    }

    @Test
    void findRecentReturnsEmptyForUnknownTask() {
        List<ScheduledTaskLog> recent = repository.findRecent(99999, 10);
        assertThat(recent).isEmpty();
    }

    @Test
    void saveFailureLog() {
        ScheduledTaskLog entry = makeLog(30, "Fail task", 5005, "BAD", false);
        repository.saveLog(entry);

        List<ScheduledTaskLog> recent = repository.findRecent(30, 1);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).isSuccess()).isFalse();
        assertThat(recent.get(0).getErrorMessage()).isNotNull();
    }

    @Test
    void findRecentRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> repository.findRecent(1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }
}
