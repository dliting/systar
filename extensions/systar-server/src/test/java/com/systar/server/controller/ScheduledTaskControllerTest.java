package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.control.*;
import com.systar.server.controller.vo.ScheduledTaskLogVO;
import com.systar.server.controller.vo.ScheduledTaskVO;
import com.systar.server.dto.ScheduledTaskCreateRequest;
import com.systar.server.dto.ScheduledTaskUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ScheduledTaskControllerTest {

    private ScheduledTaskController controller;
    private ScheduledTaskRepository taskRepo;
    private ScheduledTaskLogRepository logRepo;
    private TimeControlService timeControlService;
    private AssetStore assetStore;

    @BeforeEach
    void setUp() {
        taskRepo           = mock(ScheduledTaskRepository.class);
        logRepo            = mock(ScheduledTaskLogRepository.class);
        timeControlService = mock(TimeControlService.class);
        assetStore         = mock(AssetStore.class);
        controller         = new ScheduledTaskController(taskRepo, logRepo, timeControlService, assetStore);
    }

    private ScheduledTask sampleTask() {
        ScheduledTask t = new ScheduledTask();
        t.setId(1);
        t.setName("Daily ON");
        t.setControlId(10);
        t.setCommand("ON");
        t.setCronExpression("0 0 8 * * ?");
        t.setEnabled(true);
        t.setDescription("Turn on at 8am");
        return t;
    }

    // ---- list ----

    @Nested
    @DisplayName("GET /scheduled-tasks")
    class ListTasks {
        @Test
        @DisplayName("returns all tasks")
        void listAll() {
            when(taskRepo.findAll()).thenReturn(List.of(sampleTask()));
            Result<List<ScheduledTaskVO>> result = controller.listTasks(null, null);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getName()).isEqualTo("Daily ON");
        }

        @Test
        @DisplayName("filters by controlId")
        void listByControlId() {
            ScheduledTask t1 = sampleTask();
            t1.setControlId(10);
            ScheduledTask t2 = sampleTask();
            t2.setId(2);
            t2.setControlId(20);
            when(taskRepo.findAll()).thenReturn(List.of(t1, t2));

            Result<List<ScheduledTaskVO>> result = controller.listTasks(10, null);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getControlId()).isEqualTo(10);
        }

        @Test
        @DisplayName("filters by keyword in task name")
        void listByKeyword() {
            ScheduledTask t1 = sampleTask();    // name = "Daily ON"
            ScheduledTask t2 = sampleTask();
            t2.setId(2);
            t2.setName("Weekly OFF");
            when(taskRepo.findAll()).thenReturn(List.of(t1, t2));

            Result<List<ScheduledTaskVO>> result = controller.listTasks(null, "weekly");
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getName()).isEqualTo("Weekly OFF");
        }

        @Test
        @DisplayName("returns all when keyword is blank")
        void blankKeyword() {
            when(taskRepo.findAll()).thenReturn(List.of(sampleTask()));
            Result<List<ScheduledTaskVO>> result = controller.listTasks(null, "   ");
            assertThat(result.getData()).hasSize(1);
        }

        @Test
        @DisplayName("resolves targetName from AssetStore")
        void targetNameResolved() {
            Asset<?> asset = mock(Asset.class);
            when(asset.getCaption()).thenReturn("空调控制");
            doReturn(asset).when(assetStore).findAsset(10);
            when(taskRepo.findAll()).thenReturn(List.of(sampleTask()));

            Result<List<ScheduledTaskVO>> result = controller.listTasks(null, null);
            assertThat(result.getData().get(0).getTargetName()).isEqualTo("空调控制");
        }

        @Test
        @DisplayName("targetName is null when asset not found")
        void targetNameNullWhenMissing() {
            doReturn(null).when(assetStore).findAsset(10);
            when(taskRepo.findAll()).thenReturn(List.of(sampleTask()));

            Result<List<ScheduledTaskVO>> result = controller.listTasks(null, null);
            assertThat(result.getData().get(0).getTargetName()).isNull();
        }
    }

    // ---- getById ----

    @Nested
    @DisplayName("GET /scheduled-tasks/{id}")
    class GetTask {
        @Test
        @DisplayName("returns task when found")
        void found() {
            when(taskRepo.findById(1)).thenReturn(sampleTask());
            Result<ScheduledTaskVO> result = controller.getTask(1);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData().getName()).isEqualTo("Daily ON");
        }

        @Test
        @DisplayName("returns 404 when not found")
        void notFound() {
            when(taskRepo.findById(999)).thenReturn(null);
            Result<ScheduledTaskVO> result = controller.getTask(999);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }
    }

    // ---- create ----

    @Nested
    @DisplayName("POST /scheduled-tasks")
    class CreateTask {
        private ScheduledTaskCreateRequest validRequest() {
            ScheduledTaskCreateRequest req = new ScheduledTaskCreateRequest();
            req.setName("New task");
            req.setControlId(10);
            req.setCommand("ON");
            req.setCronExpression("0 0 8 * * ?");
            req.setDescription("desc");
            return req;
        }

        @Test
        @DisplayName("creates task successfully")
        void create() {
            Result<Integer> result = controller.createTask(validRequest());
            assertThat(result.getCode()).isEqualTo(0);
            verify(taskRepo).save(any(ScheduledTask.class));
            verify(timeControlService).addTask(any(ScheduledTask.class));
        }

        @Test
        @DisplayName("rejects null name")
        void nullName() {
            ScheduledTaskCreateRequest req = validRequest();
            req.setName(null);
            Result<Integer> result = controller.createTask(req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }

        @Test
        @DisplayName("rejects blank command")
        void blankCommand() {
            ScheduledTaskCreateRequest req = validRequest();
            req.setCommand("   ");
            Result<Integer> result = controller.createTask(req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }

        @Test
        @DisplayName("rejects invalid cron expression")
        void invalidCron() {
            ScheduledTaskCreateRequest req = validRequest();
            req.setCronExpression("not-a-cron");
            Result<Integer> result = controller.createTask(req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }

        @Test
        @DisplayName("rejects controlId <= 0")
        void invalidControlId() {
            ScheduledTaskCreateRequest req = validRequest();
            req.setControlId(0);
            Result<Integer> result = controller.createTask(req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }
    }

    // ---- update ----

    @Nested
    @DisplayName("PUT /scheduled-tasks/{id}")
    class UpdateTask {
        @Test
        @DisplayName("updates existing task")
        void update() {
            when(taskRepo.findById(1)).thenReturn(sampleTask());
            ScheduledTaskUpdateRequest req = new ScheduledTaskUpdateRequest();
            req.setName("Updated");
            Result<Void> result = controller.updateTask(1, req);
            assertThat(result.getCode()).isEqualTo(0);
            verify(taskRepo).update(any(ScheduledTask.class));
            verify(timeControlService).removeTask(1);
            verify(timeControlService).addTask(any(ScheduledTask.class));
        }

        @Test
        @DisplayName("returns 404 for missing task")
        void notFound() {
            when(taskRepo.findById(999)).thenReturn(null);
            ScheduledTaskUpdateRequest req = new ScheduledTaskUpdateRequest();
            req.setName("X");
            Result<Void> result = controller.updateTask(999, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }
    }

    // ---- delete ----

    @Nested
    @DisplayName("DELETE /scheduled-tasks/{id}")
    class DeleteTask {
        @Test
        @DisplayName("deletes existing task")
        void delete() {
            Result<Void> result = controller.deleteTask(1);
            assertThat(result.getCode()).isEqualTo(0);
            verify(timeControlService).removeTask(1);
            verify(taskRepo).deleteById(1);
        }
    }

    // ---- cron preview ----

    @Nested
    @DisplayName("GET /scheduled-tasks/cron-preview")
    class CronPreview {
        @Test
        @DisplayName("returns next fire time for valid cron")
        void validCron() {
            Result<?> result = controller.previewCron("0 0 8 * * ?");
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isNotNull();
        }

        @Test
        @DisplayName("rejects invalid cron expression")
        void invalidCron() {
            Result<?> result = controller.previewCron("not-valid");
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }
    }

    // ---- enable/disable ----

    @Nested
    @DisplayName("PUT /scheduled-tasks/{id}/enable|disable")
    class ToggleTask {
        @Test
        @DisplayName("enable delegates to service")
        void enable() {
            Result<Void> result = controller.enableTask(1);
            assertThat(result.getCode()).isEqualTo(0);
            verify(timeControlService).enableTask(1);
        }

        @Test
        @DisplayName("disable delegates to service")
        void disable() {
            Result<Void> result = controller.disableTask(1);
            assertThat(result.getCode()).isEqualTo(0);
            verify(timeControlService).disableTask(1);
        }
    }

    // ---- logs ----

    @Nested
    @DisplayName("GET /scheduled-tasks/{id}/logs")
    class GetLogs {
        @Test
        @DisplayName("returns logs with default limit")
        void defaultLimit() {
            ScheduledTaskLog logEntry = new ScheduledTaskLog();
            logEntry.setId(1);
            logEntry.setTaskId(10);
            logEntry.setTaskName("t");
            logEntry.setControlId(20);
            logEntry.setCommand("ON");
            logEntry.setExecuteTime(1000L);
            logEntry.setSuccess(true);
            when(logRepo.findRecent(10, 50)).thenReturn(List.of(logEntry));

            Result<List<ScheduledTaskLogVO>> result = controller.getTaskLogs(10, null);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getTaskName()).isEqualTo("t");
        }

        @Test
        @DisplayName("respects custom limit")
        void customLimit() {
            when(logRepo.findRecent(10, 20)).thenReturn(List.of());
            Result<List<ScheduledTaskLogVO>> result = controller.getTaskLogs(10, 20);
            assertThat(result.getCode()).isEqualTo(0);
            verify(logRepo).findRecent(10, 20);
        }

        @Test
        @DisplayName("clamps limit to max 500")
        void limitMax() {
            when(logRepo.findRecent(10, 500)).thenReturn(List.of());
            Result<List<ScheduledTaskLogVO>> result = controller.getTaskLogs(10, 9999);
            verify(logRepo).findRecent(10, 500);
        }
    }
}
