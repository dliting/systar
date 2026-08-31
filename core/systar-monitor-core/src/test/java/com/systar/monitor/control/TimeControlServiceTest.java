package com.systar.monitor.control;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.SpaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class TimeControlServiceTest {

    /** Testable Control subclass. */
    static class TestControl extends Control {
        String lastCommand;

        @Override
        public void execute(String command) {
            lastCommand = command;
        }
    }

    private AssetStore store;
    private TimeControlService service;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new SpaceType("root"), "root");
        service = new TimeControlService(store);
    }

    private TestControl addControl(int id, String name) {
        TestControl ctrl = new TestControl();
        ctrl.init(new ControlType("ct-" + name), id, name);
        store.addAsset(ctrl);
        return ctrl;
    }

    private ScheduledTask makeTask(int id, String name, int controlId, String command, String cron) {
        ScheduledTask task = new ScheduledTask();
        task.setId(id);
        task.setName(name);
        task.setControlId(controlId);
        task.setCommand(command);
        task.setCronExpression(cron);
        task.setEnabled(true);
        return task;
    }

    // ---- ScheduledTask getters/setters ----

    @Test
    @DisplayName("ScheduledTask getters/setters work")
    void scheduledTaskAccessors() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1);
        task.setName("test");
        task.setControlId(10);
        task.setCommand("on");
        task.setCronExpression("0 * * * * *");
        task.setEnabled(false);
        task.setDescription("desc");

        assertThat(task.getId()).isEqualTo(1);
        assertThat(task.getName()).isEqualTo("test");
        assertThat(task.getControlId()).isEqualTo(10);
        assertThat(task.getCommand()).isEqualTo("on");
        assertThat(task.getCronExpression()).isEqualTo("0 * * * * *");
        assertThat(task.isEnabled()).isFalse();
        assertThat(task.getDescription()).isEqualTo("desc");
    }

    // ---- ScheduledTaskLog getters/setters ----

    @Test
    @DisplayName("ScheduledTaskLog getters/setters work")
    void scheduledTaskLogAccessors() {
        ScheduledTaskLog log = new ScheduledTaskLog();
        log.setId(1);
        log.setTaskId(2);
        log.setTaskName("task");
        log.setControlId(3);
        log.setCommand("off");
        log.setExecuteTime(12345L);
        log.setSuccess(true);
        log.setErrorMessage(null);

        assertThat(log.getId()).isEqualTo(1);
        assertThat(log.getTaskId()).isEqualTo(2);
        assertThat(log.getTaskName()).isEqualTo("task");
        assertThat(log.getControlId()).isEqualTo(3);
        assertThat(log.getCommand()).isEqualTo("off");
        assertThat(log.getExecuteTime()).isEqualTo(12345L);
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getErrorMessage()).isNull();
    }

    // ---- addTask ----

    @Test
    @DisplayName("addTask rejects null")
    void addTaskNull() {
        assertThatThrownBy(() -> service.addTask(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("addTask rejects duplicate id")
    void addTaskDuplicate() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        service.addTask(task);

        ScheduledTask dup = makeTask(1, "t2", 10, "off", "0 0 0 31 12 ?");
        assertThatThrownBy(() -> service.addTask(dup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("addTask with valid cron schedules task")
    void addTaskValid() {
        addControl(10, "ctrl1");
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        assertThatNoException().isThrownBy(() -> service.addTask(task));
    }

    // ---- removeTask ----

    @Test
    @DisplayName("removeTask removes existing task")
    void removeTask() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        service.addTask(task);
        assertThatNoException().isThrownBy(() -> service.removeTask(1));
    }

    @Test
    @DisplayName("removeTask for non-existent id is no-op")
    void removeTaskNotFound() {
        assertThatNoException().isThrownBy(() -> service.removeTask(999));
    }

    // ---- enableTask / disableTask ----

    @Test
    @DisplayName("enableTask enables a disabled task")
    void enableTask() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        task.setEnabled(false);
        service.addTask(task);

        service.enableTask(1);
        assertThat(task.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("enableTask for non-existent id is no-op")
    void enableTaskNotFound() {
        assertThatNoException().isThrownBy(() -> service.enableTask(999));
    }

    @Test
    @DisplayName("enableTask for already-enabled task is no-op")
    void enableTaskAlreadyEnabled() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        service.addTask(task);
        service.enableTask(1);
        assertThat(task.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableTask disables an enabled task")
    void disableTask() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        service.addTask(task);

        service.disableTask(1);
        assertThat(task.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("disableTask for non-existent id is no-op")
    void disableTaskNotFound() {
        assertThatNoException().isThrownBy(() -> service.disableTask(999));
    }

    @Test
    @DisplayName("disableTask for already-disabled task is no-op")
    void disableTaskAlreadyDisabled() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        task.setEnabled(false);
        service.addTask(task);
        service.disableTask(1);
        assertThat(task.isEnabled()).isFalse();
    }

    // ---- loadTasks ----

    @Test
    @DisplayName("loadTasks with null is no-op")
    void loadTasksNull() {
        assertThatNoException().isThrownBy(() -> service.loadTasks(null));
    }

    @Test
    @DisplayName("loadTasks with empty list is no-op")
    void loadTasksEmpty() {
        assertThatNoException().isThrownBy(() -> service.loadTasks(List.of()));
    }

    @Test
    @DisplayName("loadTasks replaces previous tasks")
    void loadTasksReplaces() {
        ScheduledTask task1 = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        service.addTask(task1);

        ScheduledTask task2 = makeTask(2, "t2", 10, "off", "0 0 0 31 12 ?");
        service.loadTasks(List.of(task2));

        assertThatNoException().isThrownBy(() -> service.addTask(makeTask(1, "t1-new", 10, "on", "0 0 0 31 12 ?")));
    }

    // ---- shutdown ----

    @Test
    @DisplayName("shutdown completes without error")
    void shutdown() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        service.addTask(task);
        assertThatNoException().isThrownBy(() -> service.shutdown());
    }

    // ---- invalid cron ----

    @Test
    @DisplayName("addTask with invalid cron expression does not throw at add time")
    void addTaskInvalidCron() {
        ScheduledTask task = makeTask(1, "t1", 10, "on", "invalid-cron");
        assertThatNoException().isThrownBy(() -> service.addTask(task));
    }

    // ---- log persistence ----

    @Test
    @DisplayName("executeTask calls logRepository.saveLog on success")
    void executeTaskPersistsSuccessLog() throws InterruptedException {
        TestControl ctrl = addControl(10, "ctrl");
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledTaskLog[] captured = new ScheduledTaskLog[1];

        ScheduledTaskLogRepository logRepo = new ScheduledTaskLogRepository() {
            @Override public void saveLog(ScheduledTaskLog log) {
                captured[0] = log;
                latch.countDown();
            }
            @Override public List<ScheduledTaskLog> findRecent(int taskId, int limit) {
                return List.of();
            }
        };
        TimeControlService svc = new TimeControlService(store, logRepo);

        ScheduledTask task = new ScheduledTask();
        task.setId(1);
        task.setName("success-task");
        task.setControlId(10);
        task.setCommand("TEST");
        task.setCronExpression("* * * * * *");
        task.setEnabled(true);
        svc.addTask(task);

        boolean fired = latch.await(5, TimeUnit.SECONDS);
        svc.shutdown();

        assertThat(fired)
                .as("logRepository.saveLog should have been called within 5 s")
                .isTrue();
        assertThat(captured[0].isSuccess()).isTrue();
        assertThat(captured[0].getCommand()).isEqualTo("TEST");
        assertThat(captured[0].getControlId()).isEqualTo(10);
    }

    @Test
    @DisplayName("executeTask calls logRepository.saveLog on failure")
    void executeTaskPersistsFailureLog() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledTaskLog[] captured = new ScheduledTaskLog[1];

        ScheduledTaskLogRepository logRepo = new ScheduledTaskLogRepository() {
            @Override public void saveLog(ScheduledTaskLog log) {
                captured[0] = log;
                latch.countDown();
            }
            @Override public List<ScheduledTaskLog> findRecent(int taskId, int limit) {
                return List.of();
            }
        };
        TimeControlService svc = new TimeControlService(store, logRepo);

        // No control registered for controlId=9999 → will fail
        ScheduledTask task = new ScheduledTask();
        task.setId(1);
        task.setName("missing-control");
        task.setControlId(9999);
        task.setCommand("ON");
        task.setCronExpression("* * * * * *");
        task.setEnabled(true);
        svc.addTask(task);

        boolean fired = latch.await(5, TimeUnit.SECONDS);
        svc.shutdown();

        assertThat(fired)
                .as("logRepository.saveLog should have been called within 5 s")
                .isTrue();
        assertThat(captured[0].isSuccess()).isFalse();
        assertThat(captured[0].getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("executeTask works without logRepository (null)")
    void executeTaskWithoutLogRepository() throws InterruptedException {
        TestControl ctrl = addControl(10, "ctrl");
        TimeControlService svc = new TimeControlService(store);

        ScheduledTask task = new ScheduledTask();
        task.setId(1);
        task.setName("no-repo");
        task.setControlId(10);
        task.setCommand("FIRE");
        task.setCronExpression("* * * * * *");
        task.setEnabled(true);
        svc.addTask(task);

        boolean found = false;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!svc.getLogQueue().isEmpty()) {
                found = true;
                break;
            }
            Thread.sleep(100);
        }
        svc.shutdown();

        assertThat(found)
                .as("Task should have executed within 5 s")
                .isTrue();
        assertThat(ctrl.lastCommand).isEqualTo("FIRE");
    }

    // ---- logQueue ----

    @Test
    @DisplayName("getLogQueue returns the log queue")
    void getLogQueue() {
        assertThat(service.getLogQueue()).isNotNull();
    }

    // ---- enable/disable persistence ----

    @Test
    @DisplayName("enableTask persists enabled=true via repository")
    void enableTaskPersists() {
        ScheduledTaskRepository taskRepo = mock(ScheduledTaskRepository.class);
        ScheduledTaskLogRepository logRepo = mock(ScheduledTaskLogRepository.class);
        TimeControlService svc = new TimeControlService(store, logRepo, taskRepo);

        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        task.setEnabled(false);
        svc.addTask(task);

        svc.enableTask(1);
        assertThat(task.isEnabled()).isTrue();
        verify(taskRepo).update(task);
    }

    @Test
    @DisplayName("disableTask persists enabled=false via repository")
    void disableTaskPersists() {
        ScheduledTaskRepository taskRepo = mock(ScheduledTaskRepository.class);
        ScheduledTaskLogRepository logRepo = mock(ScheduledTaskLogRepository.class);
        TimeControlService svc = new TimeControlService(store, logRepo, taskRepo);

        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        svc.addTask(task);

        svc.disableTask(1);
        assertThat(task.isEnabled()).isFalse();
        verify(taskRepo).update(task);
    }

    @Test
    @DisplayName("enableTask survives repository failure")
    void enableTaskRepoFailure() {
        ScheduledTaskRepository taskRepo = mock(ScheduledTaskRepository.class);
        doThrow(new RuntimeException("DB down")).when(taskRepo).update(any());
        ScheduledTaskLogRepository logRepo = mock(ScheduledTaskLogRepository.class);
        TimeControlService svc = new TimeControlService(store, logRepo, taskRepo);

        ScheduledTask task = makeTask(1, "t1", 10, "on", "0 0 0 31 12 ?");
        task.setEnabled(false);
        svc.addTask(task);

        assertThatNoException().isThrownBy(() -> svc.enableTask(1));
        assertThat(task.isEnabled()).isTrue();
    }
}
