package com.systar.ops.analysis.service;

import com.systar.ops.analysis.model.*;
import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AnalysisServiceTest {

    @Autowired
    private AnalysisService analysisService;

    @Test
    void predictTrend_shouldReturnEmptyWhenNoData() {
        TrendPrediction result = analysisService.predictTrend(9999, "DAY", 7);
        assertThat(result.confidence()).isEqualTo(0);
        assertThat(result.predicted()).isEmpty();
    }

    @Test
    void predictTrend_shouldReturnHistoricalData() {
        TrendPrediction result = analysisService.predictTrend(2001, "DAY", 3);
        assertThat(result.monitorId()).isEqualTo(2001);
        assertThat(result.granularity()).isEqualTo("DAY");
        assertThat(result.historical()).isNotNull();
    }

    @Test
    void detectAnomalies_shouldReturnEmptyWithInsufficientData() {
        List<AnomalyPoint> result = analysisService.detectAnomalies(
                9999, LocalDateTime.now().minusDays(7), LocalDateTime.now());
        assertThat(result).isEmpty();
    }

    @Test
    void detectAnomalies_shouldHandleEmptyRange() {
        List<AnomalyPoint> result = analysisService.detectAnomalies(
                2001, LocalDateTime.now(), LocalDateTime.now().minusDays(1));
        assertThat(result).isEmpty();
    }

    @Test
    void assessDeviceHealth_shouldReturnValidScore() {
        HealthAssessment result = analysisService.assessDeviceHealth(1001);
        assertThat(result.deviceId()).isEqualTo(1001);
        assertThat(result.healthScore()).isBetween(0.0, 100.0);
        assertThat(result.level()).isIn("good", "fair", "poor");
    }

    @Test
    void assessAllDevices_shouldReturnList() {
        List<HealthAssessment> results = analysisService.assessAllDevices();
        assertThat(results).isNotNull();
        assertThat(results).allMatch(h -> h.healthScore() >= 0 && h.healthScore() <= 100);
    }
}
