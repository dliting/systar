package com.systar.ops.statistics.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.ops.statistics.model.TrendQuery;
import com.systar.ops.statistics.model.TrendResponseVO;
import com.systar.ops.statistics.service.TrendService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/trend")
public class TrendController {

    private final TrendService trendService;

    public TrendController(TrendService trendService) {
        this.trendService = trendService;
    }

    @RequirePermission("iot:monitor:query")
    @GetMapping("/data")
    public Result<TrendResponseVO> getTrendData(
            @RequestParam int monitorId,
            @RequestParam String monitorKind,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "HOUR") String granularity) {
        TrendQuery query = new TrendQuery(monitorId, monitorKind, startTime, endTime, granularity);
        return Result.success(trendService.getTrendData(query));
    }

    @RequirePermission("iot:monitor:query")
    @GetMapping("/default")
    public Result<TrendResponseVO> getDefaultView(
            @RequestParam int monitorId,
            @RequestParam String monitorKind) {
        return Result.success(trendService.getDefaultView(monitorId, monitorKind));
    }

    @RequirePermission("iot:monitor:query")
    @GetMapping("/metadata")
    public Result<Map<String, Object>> getMetadata(
            @RequestParam int monitorId,
            @RequestParam String monitorKind) {
        return Result.success(trendService.getMetadata(monitorId, monitorKind));
    }
}
