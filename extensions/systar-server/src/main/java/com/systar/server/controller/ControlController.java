package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.monitor.asset.AssetException;
import com.systar.monitor.server.MonitorEngine;
import com.systar.server.controller.vo.ControlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/monitor")
public class ControlController {

    private static final Logger log = LoggerFactory.getLogger(ControlController.class);

    private final ObjectProvider<MonitorEngine> engine;
    private final AtomicBoolean engineWarned = new AtomicBoolean();

    public ControlController(ObjectProvider<MonitorEngine> engine) {
        this.engine = engine;
    }

    /** WARN once per injection point when the optional engine is absent. */
    private void warnEngineAbsent(String operation) {
        if (engineWarned.compareAndSet(false, true)) {
            log.warn("Monitor engine not present; {} skipped.", operation);
        }
    }

    @RequirePermission("iot:control:execute")
    @PostMapping("/control/{id}/execute")
    public Result<Map<String, Object>> executeControl(
            @PathVariable int id,
            @RequestBody ControlRequest request) {
        if (request == null || request.getCommand() == null || request.getCommand().isBlank()) {
            return Result.error(Result.CODE_BAD_REQUEST, "Command is required.");
        }
        try {
            MonitorEngine e = engine.getIfAvailable();
            if (e == null) {
                warnEngineAbsent("control execution");
                return Result.error(Result.CODE_CONFLICT, "Monitor engine not present; control execution skipped.");
            }
            e.controlImmediately(id, request.getCommand());
            return Result.success(Map.of("status", "accepted"));
        } catch (IllegalStateException e) {
            return Result.error(Result.CODE_CONFLICT, e.getMessage());
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }
}
