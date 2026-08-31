package com.systar.monitor.schedule;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.Control;
import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.result.IMonitorResult;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for DetectTask when used with a control command (execute-then-detect).
 * Verifies that the command is executed before detection, and that the
 * detecting flag is properly cleared for manual tasks.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ControlDetectTaskTest {

    private ResultDispatcher dispatcher;
    private ControlType type;

    @BeforeEach
    void setUp() {
        type = new ControlType("ct");
        type.setDetectInterval(TimeSpan.ofMinutes(5));
        dispatcher = mock(ResultDispatcher.class);
    }

    private Control createControl(AtomicBoolean executeCalled, AtomicReference<String> capturedCommand) {
        return new Control() {
            @Override
            public void execute(String command) {
                executeCalled.set(true);
                capturedCommand.set(command);
            }

            @Override
            public void detect(IMonitorResult result) {
                result.setValue("on");
            }
        };
    }

    @Test
    @DisplayName("Control task executes command then detects")
    void executesCommandThenDetects() {
        AtomicBoolean executeCalled = new AtomicBoolean();
        AtomicReference<String> capturedCmd = new AtomicReference<>();
        Control control = createControl(executeCalled, capturedCmd);
        control.init(type, 1, "ctrl1");

        DetectTask task = new DetectTask(control, dispatcher, true, "turn_on");
        task.run();

        assertThat(executeCalled.get()).isTrue();
        assertThat(capturedCmd.get()).isEqualTo("turn_on");
        verify(dispatcher).dispatch(any(MonitorResult.class));
    }

    @Test
    @DisplayName("Control task clears detecting flag for manual task")
    void clearsDetectingOnCompletion() {
        Control control = createControl(new AtomicBoolean(), new AtomicReference<>());
        control.init(type, 1, "ctrl1");
        control.setDetecting(true);

        DetectTask task = new DetectTask(control, dispatcher, true, "turn_on");
        task.run();

        assertThat(control.isDetecting()).isFalse();
    }

    @Test
    @DisplayName("Control task clears detecting even when execute throws")
    void clearsDetectingOnExecuteError() {
        Control control = new Control() {
            @Override
            public void execute(String command) {
                throw new RuntimeException("device offline");
            }

            @Override
            public void detect(IMonitorResult result) {
                result.setValue("off");
            }
        };
        control.init(type, 1, "ctrl1");
        control.setDetecting(true);

        DetectTask task = new DetectTask(control, dispatcher, true, "turn_on");
        task.run();

        assertThat(control.isDetecting()).isFalse();
        MonitorResult lastResult = task.getLastResult();
        assertThat(lastResult).isNotNull();
        assertThat(lastResult.hasError()).isTrue();
        assertThat(lastResult.getError()).contains("device offline");
    }

    @Test
    @DisplayName("Control task without command acts as normal detect task")
    void noCommandActsAsNormalDetect() {
        Control control = createControl(new AtomicBoolean(), new AtomicReference<>());
        control.init(type, 1, "ctrl1");

        DetectTask task = new DetectTask(control, dispatcher);
        task.run();

        verify(dispatcher).dispatch(any(MonitorResult.class));
        MonitorResult lastResult = task.getLastResult();
        assertThat(lastResult).isNotNull();
        assertThat(lastResult.hasError()).isFalse();
    }

    @Test
    @DisplayName("Constructor rejects blank command")
    void rejectsBlankCommand() {
        Control control = createControl(new AtomicBoolean(), new AtomicReference<>());
        control.init(type, 1, "ctrl1");

        assertThatThrownBy(() -> new DetectTask(control, dispatcher, true, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command");

        assertThatThrownBy(() -> new DetectTask(control, dispatcher, true, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command");
    }

    @Test
    @DisplayName("Constructor rejects non-Control monitor with command")
    void rejectsNonControlWithCommand() {
        var probe = new com.systar.monitor.asset.Probe();
        probe.init(new com.systar.monitor.asset.type.ProbeType("pt"), 1, "p1");

        assertThatThrownBy(() -> new DetectTask(probe, dispatcher, true, "turn_on"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Control");
    }
}
