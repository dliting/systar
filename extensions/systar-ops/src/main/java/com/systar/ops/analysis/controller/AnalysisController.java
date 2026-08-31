package com.systar.ops.analysis.controller;

import com.systar.ops.analysis.model.*;
import com.systar.ops.analysis.service.AnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ops/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/trend/{monitorId}")
    public TrendPrediction predictTrend(
            @PathVariable int monitorId,
            @RequestParam(defaultValue = "DAY") String granularity,
            @RequestParam(defaultValue = "7") int futurePeriods) {
        return analysisService.predictTrend(monitorId, granularity, futurePeriods);
    }

    @GetMapping("/anomaly/{monitorId}")
    public List<AnomalyPoint> detectAnomalies(
            @PathVariable int monitorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return analysisService.detectAnomalies(monitorId, start, end);
    }

    @GetMapping("/health/{deviceId}")
    public HealthAssessment assessDeviceHealth(@PathVariable int deviceId) {
        return analysisService.assessDeviceHealth(deviceId);
    }

    @GetMapping("/health")
    public List<HealthAssessment> assessAllDevices() {
        return analysisService.assessAllDevices();
    }
}
