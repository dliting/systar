package com.systar.ops.statistics.controller;

import com.systar.ops.statistics.model.*;
import com.systar.ops.statistics.service.TrendService;
import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link TrendController} REST endpoints.
 *
 * <p>Uses {@code @SpringBootTest} with {@code @AutoConfigureMockMvc} and a mocked
 * {@link TrendService} to test controller routing, parameter binding,
 * and response serialization in isolation.</p>
 */
@SpringBootTest(classes = OpsTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class TrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrendService trendService;

    // ==================== GET /api/ops/trend/metadata ====================

    @Test
    void getMetadata_withProbe_returnsCorrectMetadata() throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", "Temperature-01");
        meta.put("caption", "Temperature Sensor 01");
        meta.put("unit", "C");
        meta.put("dataType", "FLOAT");
        meta.put("detectInterval", "00:00:10");

        when(trendService.getMetadata(1, "PROBE")).thenReturn(meta);

        mockMvc.perform(get("/api/ops/trend/metadata")
                        .param("monitorId", "1")
                        .param("monitorKind", "PROBE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Temperature-01"))
                .andExpect(jsonPath("$.data.unit").value("C"))
                .andExpect(jsonPath("$.data.dataType").value("FLOAT"))
                .andExpect(jsonPath("$.data.detectInterval").value("00:00:10"));
    }

    @Test
    void getMetadata_withNonExistentProbe_returnsEmptyData() throws Exception {
        when(trendService.getMetadata(99999, "PROBE")).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/ops/trend/metadata")
                        .param("monitorId", "99999")
                        .param("monitorKind", "PROBE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getMetadata_withControl_returnsMetadata() throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", "FanControl-01");
        meta.put("caption", "Fan Speed Control");
        meta.put("unit", "%");
        meta.put("dataType", null);
        meta.put("detectInterval", "30s");

        when(trendService.getMetadata(2, "CONTROL")).thenReturn(meta);

        mockMvc.perform(get("/api/ops/trend/metadata")
                        .param("monitorId", "2")
                        .param("monitorKind", "CONTROL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("FanControl-01"))
                .andExpect(jsonPath("$.data.dataType").doesNotExist());
    }

    // ==================== GET /api/ops/trend/data ====================

    @Test
    void getTrendData_withValidParams_returns200() throws Exception {
        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("HOUR");
        response.setDataPoints(List.of(
                new TrendBarVO(LocalDateTime.of(2026, 1, 15, 10, 0), 22.0, 30.0, 15.0, 100)
        ));
        response.setAvg5(Collections.singletonList(null));
        response.setAvg10(Collections.singletonList(null));
        response.setAvg20(Collections.singletonList(null));
        response.setSummary(new TrendSummaryVO(22.0, 25.0, 18.0, 100));

        when(trendService.getTrendData(any(TrendQuery.class))).thenReturn(response);

        mockMvc.perform(get("/api/ops/trend/data")
                        .param("monitorId", "1")
                        .param("monitorKind", "PROBE")
                        .param("startTime", "2026-01-15 00:00:00")
                        .param("endTime", "2026-01-15 23:59:59")
                        .param("granularity", "HOUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granularity").value("HOUR"))
                .andExpect(jsonPath("$.data.dataPoints").isArray())
                .andExpect(jsonPath("$.data.dataPoints[0].avg").value(22.0))
                .andExpect(jsonPath("$.data.dataPoints[0].max").value(30.0))
                .andExpect(jsonPath("$.data.dataPoints[0].min").value(15.0))
                .andExpect(jsonPath("$.data.dataPoints[0].sampleCount").value(100));
    }

    @Test
    void getTrendData_withDefaultGranularity_returnsHourlyData() throws Exception {
        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("HOUR");
        response.setDataPoints(Collections.emptyList());
        response.setAvg5(Collections.emptyList());
        response.setAvg10(Collections.emptyList());
        response.setAvg20(Collections.emptyList());
        response.setSummary(new TrendSummaryVO(0, 0, 0, 0));

        when(trendService.getTrendData(any(TrendQuery.class))).thenReturn(response);

        mockMvc.perform(get("/api/ops/trend/data")
                        .param("monitorId", "1")
                        .param("monitorKind", "PROBE")
                        .param("startTime", "2026-01-15 00:00:00")
                        .param("endTime", "2026-01-15 23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granularity").value("HOUR"));
    }

    @Test
    void getTrendData_withIntradayGranularity_returnsIntradayData() throws Exception {
        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("INTRADAY");
        response.setIntradayPoints(List.of(
                new IntradayPointVO(LocalDateTime.of(2026, 1, 15, 10, 0, 0), 22.5)
        ));
        response.setDataPoints(Collections.emptyList());
        response.setAvg5(Collections.emptyList());
        response.setAvg10(Collections.emptyList());
        response.setAvg20(Collections.emptyList());

        when(trendService.getTrendData(any(TrendQuery.class))).thenReturn(response);

        mockMvc.perform(get("/api/ops/trend/data")
                        .param("monitorId", "1")
                        .param("monitorKind", "PROBE")
                        .param("startTime", "2026-01-15 00:00:00")
                        .param("endTime", "2026-01-15 23:59:59")
                        .param("granularity", "INTRADAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granularity").value("INTRADAY"))
                .andExpect(jsonPath("$.data.intradayPoints").isArray())
                .andExpect(jsonPath("$.data.intradayPoints[0].value").value(22.5));
    }

    @Test
    void getTrendData_withoutRequiredParams_returns400() throws Exception {
        // Missing monitorId, monitorKind, startTime, endTime
        mockMvc.perform(get("/api/ops/trend/data"))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /api/ops/trend/default ====================

    @Test
    void getDefaultView_withProbe_returnsAdaptiveDefault() throws Exception {
        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("INTRADAY");
        response.setIntradayPoints(Collections.emptyList());
        response.setDataPoints(Collections.emptyList());
        response.setAvg5(Collections.emptyList());
        response.setAvg10(Collections.emptyList());
        response.setAvg20(Collections.emptyList());
        response.setSummary(new TrendSummaryVO(0, 0, 0, 0));

        when(trendService.getDefaultView(1, "PROBE")).thenReturn(response);

        mockMvc.perform(get("/api/ops/trend/default")
                        .param("monitorId", "1")
                        .param("monitorKind", "PROBE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granularity").value("INTRADAY"));
    }

    @Test
    void getDefaultView_withHourGranularity_returnsDataPoints() throws Exception {
        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("HOUR");
        response.setDataPoints(List.of(
                new TrendBarVO(LocalDateTime.of(2026, 1, 15, 10, 0), 25.0, 30.0, 15.0, 60)
        ));
        response.setIntradayPoints(Collections.emptyList());
        response.setAvg5(Collections.singletonList(null));
        response.setAvg10(Collections.singletonList(null));
        response.setAvg20(Collections.singletonList(null));
        response.setSummary(new TrendSummaryVO(25.0, 30.0, 15.0, 60));

        when(trendService.getDefaultView(1, "PROBE")).thenReturn(response);

        mockMvc.perform(get("/api/ops/trend/default")
                        .param("monitorId", "1")
                        .param("monitorKind", "PROBE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granularity").value("HOUR"))
                .andExpect(jsonPath("$.data.summary.currentValue").value(25.0))
                .andExpect(jsonPath("$.data.summary.periodMax").value(30.0))
                .andExpect(jsonPath("$.data.summary.periodMin").value(15.0))
                .andExpect(jsonPath("$.data.summary.totalSamples").value(60));
    }

    @Test
    void getDefaultView_withoutRequiredParams_returns400() throws Exception {
        mockMvc.perform(get("/api/ops/trend/default"))
                .andExpect(status().isBadRequest());
    }
}
