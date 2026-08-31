package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.monitor.asset.AssetException;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.controller.vo.ControlRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class ControlController {

    private final MonitorServer monitorServer;

    public ControlController(MonitorServer monitorServer) {
        this.monitorServer = monitorServer;
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
            monitorServer.controlImmediately(id, request.getCommand());
            return Result.success(Map.of("status", "accepted"));
        } catch (IllegalStateException e) {
            return Result.error(Result.CODE_CONFLICT, e.getMessage());
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }
}
