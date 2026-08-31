package com.systar.ops.analysis.service;

import com.systar.common.config.SystemConfigManager;
import com.systar.ops.analysis.mapper.AnalysisMapper;
import com.systar.ops.analysis.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private static final int MIN_DATA_POINTS = 10;
    private static final String CFG_ANOMALY_THRESHOLD = "ops.analysis.anomaly_threshold";
    private static final String CFG_MOVING_AVG_WINDOW = "ops.analysis.moving_avg_window";
    private static final String CFG_HEALTH_ALARM = "ops.analysis.health_weight_alarm";
    private static final String CFG_HEALTH_MAINTENANCE = "ops.analysis.health_weight_maintenance";
    private static final String CFG_HEALTH_AVAILABILITY = "ops.analysis.health_weight_availability";

    private final AnalysisMapper mapper;
    private final SystemConfigManager config;

    public AnalysisService(AnalysisMapper mapper, SystemConfigManager config) {
        this.mapper = mapper;
        this.config = config;
    }

    public TrendPrediction predictTrend(int monitorId, String granularity, int futurePeriods) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(90);
        List<DataPoint> historical = loadHistory(monitorId, start, end);
        if (historical.size() < MIN_DATA_POINTS) {
            return new TrendPrediction(monitorId, granularity, historical,
                    Collections.emptyList(), 0);
        }

        int window = config.getIntValue(CFG_MOVING_AVG_WINDOW, 7);
        List<Double> values = historical.stream().map(DataPoint::value).toList();
        List<Double> smoothed = movingAverage(values, window);
        List<DataPoint> predicted = predictNext(smoothed, historical.get(historical.size() - 1).timestamp(),
                granularity, futurePeriods);

        double confidence = values.size() >= 30 ? 0.85 : 0.5;
        return new TrendPrediction(monitorId, granularity, historical, predicted, confidence);
    }

    public List<AnomalyPoint> detectAnomalies(int monitorId, LocalDateTime start, LocalDateTime end) {
        List<DataPoint> data = loadHistory(monitorId, start, end);
        if (data.size() < MIN_DATA_POINTS) return Collections.emptyList();

        int window = config.getIntValue(CFG_MOVING_AVG_WINDOW, 7);
        double threshold = config.getDoubleValue(CFG_ANOMALY_THRESHOLD, 2.0);
        List<Double> values = data.stream().map(DataPoint::value).toList();
        List<Double> smoothed = movingAverage(values, window);
        double stdDev = standardDeviation(values, smoothed);

        if (stdDev == 0) return Collections.emptyList();

        List<AnomalyPoint> anomalies = new ArrayList<>();
        for (int i = window; i < data.size(); i++) {
            double actual = values.get(i);
            double expected = smoothed.get(i - window);
            double deviation = Math.abs(actual - expected) / stdDev;
            if (deviation > threshold) {
                anomalies.add(new AnomalyPoint(data.get(i).timestamp(), actual, expected,
                        deviation, deviation > threshold * 1.5 ? "high" : "medium"));
            }
        }
        return anomalies;
    }

    public HealthAssessment assessDeviceHealth(int deviceId) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);
        long alarmCount = mapper.countAlarmsForDevice(deviceId, start, end);
        long maintenanceCount = mapper.countMaintenanceForDevice(deviceId, start, end);

        List<Integer> probeIds = mapper.findProbeIdsForDevice(deviceId);
        long onlineDays = 0;
        for (int pid : probeIds) {
            List<DataPoint> data = loadHistory(pid, start, end);
            onlineDays = Math.max(onlineDays, data.stream()
                    .map(DataPoint::timestamp).map(LocalDateTime::toLocalDate)
                    .distinct().count());
        }
        double availability = 30 > 0 ? (double) onlineDays / 30 : 0;

        double wAlarm = config.getDoubleValue(CFG_HEALTH_ALARM, 0.4);
        double wMaint = config.getDoubleValue(CFG_HEALTH_MAINTENANCE, 0.3);
        double wAvail = config.getDoubleValue(CFG_HEALTH_AVAILABILITY, 0.3);

        double alarmScore = Math.max(0, 1 - alarmCount / 10.0);
        double maintScore = Math.max(0, 1 - maintenanceCount / 5.0);
        double healthScore = (alarmScore * wAlarm + maintScore * wMaint + availability * wAvail) * 100;
        healthScore = Math.min(100, Math.max(0, healthScore));

        String level = healthScore >= 80 ? "good" : healthScore >= 60 ? "fair" : "poor";
        List<String> riskFactors = new ArrayList<>();
        if (alarmCount > 5) riskFactors.add("high_alarm_frequency");
        if (maintenanceCount > 3) riskFactors.add("frequent_maintenance");
        if (availability < 0.8) riskFactors.add("low_availability");

        String deviceName = getDeviceName(deviceId);
        return new HealthAssessment(deviceId, deviceName, healthScore, level, riskFactors);
    }

    public List<HealthAssessment> assessAllDevices() {
        List<Map<String, Object>> devices = mapper.listActiveDevices();
        return devices.stream()
                .map(d -> assessDeviceHealth(((Number) d.get("id")).intValue()))
                .collect(Collectors.toList());
    }

    // ---- helpers ----

    private List<DataPoint> loadHistory(int monitorId, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> floatRows = mapper.getFloatHistory(monitorId, start, end);
        List<Map<String, Object>> intRows = mapper.getIntHistory(monitorId, start, end);
        List<DataPoint> points = new ArrayList<>();
        for (Map<String, Object> r : floatRows) {
            Object ts = r.get("ts");
            Object val = r.get("val");
            if (ts != null && val != null) {
                points.add(new DataPoint(toLocalDateTime(ts), ((Number) val).doubleValue()));
            }
        }
        for (Map<String, Object> r : intRows) {
            Object ts = r.get("ts");
            Object val = r.get("val");
            if (ts != null && val != null) {
                points.add(new DataPoint(toLocalDateTime(ts), ((Number) val).doubleValue()));
            }
        }
        points.sort(Comparator.comparing(DataPoint::timestamp));
        return points;
    }

    private List<Double> movingAverage(List<Double> values, int window) {
        List<Double> result = new ArrayList<>();
        for (int i = window - 1; i < values.size(); i++) {
            double sum = 0;
            for (int j = i - window + 1; j <= i; j++) sum += values.get(j);
            result.add(sum / window);
        }
        return result;
    }

    private double standardDeviation(List<Double> actual, List<Double> smoothed) {
        int n = Math.min(actual.size(), smoothed.size());
        if (n == 0) return 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double diff = actual.get(i) - smoothed.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum / n);
    }

    private List<DataPoint> predictNext(List<Double> smoothed, LocalDateTime lastTs,
                                         String granularity, int periods) {
        if (smoothed.isEmpty()) return Collections.emptyList();
        List<DataPoint> predicted = new ArrayList<>();
        double last = smoothed.get(smoothed.size() - 1);
        double trend = smoothed.size() > 1
                ? (smoothed.get(smoothed.size() - 1) - smoothed.get(0)) / smoothed.size() : 0;
        ChronoUnit unit = "HOUR".equals(granularity) ? ChronoUnit.HOURS
                : "WEEK".equals(granularity) ? ChronoUnit.WEEKS
                : "MONTH".equals(granularity) ? ChronoUnit.MONTHS : ChronoUnit.DAYS;
        for (int i = 1; i <= periods; i++) {
            predicted.add(new DataPoint(lastTs.plus(i, unit), last + trend * i));
        }
        return predicted;
    }

    private String getDeviceName(int deviceId) {
        List<Map<String, Object>> devices = mapper.listActiveDevices();
        return devices.stream()
                .filter(d -> ((Number) d.get("id")).intValue() == deviceId)
                .findFirst().map(d -> (String) d.get("name")).orElse("Device-" + deviceId);
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val instanceof LocalDateTime) return (LocalDateTime) val;
        if (val instanceof java.sql.Timestamp) return ((java.sql.Timestamp) val).toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(val));
    }
}
