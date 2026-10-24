package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.monitor.asset.AssetException;
import com.systar.monitor.server.MonitorEngine;
import com.systar.server.controller.vo.ControlRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ControlControllerTest {

    private ControlController controller;
    private MonitorEngine engine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        engine = mock(MonitorEngine.class);
        ObjectProvider<MonitorEngine> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(engine);
        controller = new ControlController(provider);
    }

    @Nested
    @DisplayName("POST /api/monitor/control/{id}/execute")
    class ExecuteControl {

        @Test
        @DisplayName("returns error when command is null")
        void nullCommand() {
            ControlRequest req = new ControlRequest();
            req.setCommand(null);
            Result<Map<String, Object>> result = controller.executeControl(1, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
            assertThat(result.getMessage()).contains("Command is required");
        }

        @Test
        @DisplayName("returns error when command is blank")
        void blankCommand() {
            ControlRequest req = new ControlRequest();
            req.setCommand("   ");
            Result<Map<String, Object>> result = controller.executeControl(1, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }

        @Test
        @DisplayName("returns accepted when control executes successfully")
        void executeSuccess() throws Exception {
            doNothing().when(engine).controlImmediately(1, "start");
            ControlRequest req = new ControlRequest();
            req.setCommand("start");
            Result<Map<String, Object>> result = controller.executeControl(1, req);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).containsEntry("status", "accepted");
        }

        @Test
        @DisplayName("returns conflict when control is already executing")
        void executeConflict() throws Exception {
            doThrow(new IllegalStateException("already executing"))
                    .when(engine).controlImmediately(1, "start");
            ControlRequest req = new ControlRequest();
            req.setCommand("start");
            Result<Map<String, Object>> result = controller.executeControl(1, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_CONFLICT);
        }

        @Test
        @DisplayName("returns error when asset not found or not a control")
        void executeAssetError() throws Exception {
            doThrow(new AssetException("not a control"))
                    .when(engine).controlImmediately(99, "start");
            ControlRequest req = new ControlRequest();
            req.setCommand("start");
            Result<Map<String, Object>> result = controller.executeControl(99, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }

        @Test
        @DisplayName("returns conflict when engine absent")
        @SuppressWarnings("unchecked")
        void engineAbsent() {
            ControlRequest req = new ControlRequest();
            req.setCommand("start");
            Result<Map<String, Object>> result =
                    new ControlController(mock(ObjectProvider.class)).executeControl(1, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_CONFLICT);
            assertThat(result.getMessage()).contains("Monitor engine not present");
        }

        @Test
        @DisplayName("returns error when request is null")
        void nullRequest() {
            Result<Map<String, Object>> result = controller.executeControl(1, null);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }
    }
}
