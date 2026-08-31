package com.systar.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.entity.SampleFloatEntity;
import com.systar.data.service.DataRetentionService;
import com.systar.data.service.SampleFloatService;
import com.systar.data.service.SystemSettingService;
import com.systar.data.service.retention.RetentionResult;
import com.systar.data.service.retention.RetentionSummary;
import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.Space;
import com.systar.monitor.asset.type.SpaceType;
import com.systar.monitor.server.MonitorServer;
import com.systar.common.api.Result;
import com.systar.server.controller.vo.ProbeValueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorDataControllerTest {

    private MonitorDataController controller;
    private MonitorServer monitorServer;
    private SampleFloatService sampleFloatService;
    private DataRetentionService retentionService;
    private SystemConfigManager configManager;

    @BeforeEach
    void setUp() {
        monitorServer      = mock(MonitorServer.class);
        sampleFloatService = mock(SampleFloatService.class);
        retentionService   = mock(DataRetentionService.class);
        configManager      = new SystemConfigManager();
        SystemSettingService settingService = mock(SystemSettingService.class);
        controller = new MonitorDataController(monitorServer, sampleFloatService,
                retentionService, configManager, settingService);
    }

    @SuppressWarnings("unchecked")
    private void stubFindAsset(int id, Asset<?> asset) {
        doReturn(asset).when(monitorServer).findAsset(id);
    }

    @Nested
    @DisplayName("GET /api/monitor/probe-values")
    class GetProbeValues {

        @Test
        @DisplayName("returns error when ids is blank")
        void blankIds() {
            Result<?> result = controller.getProbeValues("  ");
            assertThat(result.getCode()).isEqualTo(1);
            assertThat(result.getMessage()).contains("Parameter 'ids' is required");
        }

        @Test
        @DisplayName("returns error when ids is null")
        void nullIds() {
            Result<?> result = controller.getProbeValues(null);
            assertThat(result.getCode()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns values for valid monitor ids")
        void validIds() {
            Probe probe = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            probe.init(new ProbeType("test"), 1, "probe-1");
            probe.setValue(23.5);
            stubFindAsset(1, probe);
            Result<List<ProbeValueVO>> result = controller.getProbeValues("1");
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getValue()).isEqualTo(23.5);
        }

        @Test
        @DisplayName("skips invalid (non-numeric) ids")
        void skipsInvalidIds() {
            Result<List<ProbeValueVO>> result = controller.getProbeValues("abc");
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("skips non-monitor assets")
        void skipsNonMonitorAssets() {
            Space space = new Space();
            space.setId(1);
            space.setName("s1");
            space.setType(new SpaceType("root"));
            stubFindAsset(1, space);
            Result<List<ProbeValueVO>> result = controller.getProbeValues("1");
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("handles multiple comma-separated ids")
        void multipleIds() {
            Probe p1 = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            p1.init(new ProbeType("t"), 1, "p1");
            p1.setValue(10.0);
            Probe p2 = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            p2.init(new ProbeType("t"), 2, "p2");
            p2.setValue(20.0);
            stubFindAsset(1, p1);
            stubFindAsset(2, p2);
            Result<List<ProbeValueVO>> result = controller.getProbeValues("1,2");
            assertThat(result.getData()).hasSize(2);
        }

        @Test
        @DisplayName("rejects ids exceeding MAX_IDS_LENGTH")
        void rejectsOversizedIds() {
            String longIds = "1".repeat(10001);
            Result<?> result = controller.getProbeValues(longIds);
            assertThat(result.getCode()).isEqualTo(1);
            assertThat(result.getMessage()).contains("exceeds maximum allowed length");
        }
    }

    @Nested
    @DisplayName("GET /api/monitor/probe-history")
    class GetProbeHistory {

        @Test
        @DisplayName("returns paginated history for a monitor")
        @SuppressWarnings("unchecked")
        void returnsPaginatedHistory() {
            Page<SampleFloatEntity> mockPage = mock(Page.class);
            when(mockPage.getTotal()).thenReturn(10L);
            when(mockPage.getCurrent()).thenReturn(1L);
            when(mockPage.getSize()).thenReturn(50L);
            when(mockPage.getRecords()).thenReturn(List.of(new SampleFloatEntity()));

            when(sampleFloatService.page(any(Page.class), any(QueryWrapper.class)))
                    .thenReturn(mockPage);

            Result<Map<String, Object>> result = controller.getProbeHistory(1, null, null, 1, 50);

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).containsKeys("total", "page", "size", "records");
            assertThat(result.getData().get("total")).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("Data Retention API")
    class DataRetentionApi {

        @Test
        @DisplayName("executeRetention returns summary from service")
        void executeRetentionReturnsSummary() {
            RetentionResult zero = new RetentionResult("none", 0);
            RetentionSummary summary = new RetentionSummary(zero, zero, zero, zero, zero, zero, zero);
            when(retentionService.executeAll()).thenReturn(summary);

            Result<RetentionSummary> result = controller.executeRetention();

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isNotNull();
            verify(retentionService).executeAll();
        }

        @Test
        @DisplayName("getRetentionConfig returns defaults from shared constants")
        void getRetentionConfigReturnsDefaults() {
            Result<Map<String, Object>> result = controller.getRetentionConfig();

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).containsEntry("sampleDays", 90);
            assertThat(result.getData()).containsEntry("alarmLogDays", 180);
            assertThat(result.getData()).containsEntry("linkageLogDays", 180);
            assertThat(result.getData()).containsEntry("enabled", true);
        }

        @Test
        @DisplayName("getRetentionConfig returns configured values")
        void getRetentionConfigReturnsConfigured() {
            configManager.loadConfigs(Map.of(
                    "data_retention.sample_days", "30",
                    "data_retention.alarm_log_days", "60",
                    "data_retention.linkage_log_days", "90",
                    "data_retention.enabled", "false"
            ));

            Result<Map<String, Object>> result = controller.getRetentionConfig();

            assertThat(result.getData()).containsEntry("sampleDays", 30);
            assertThat(result.getData()).containsEntry("alarmLogDays", 60);
            assertThat(result.getData()).containsEntry("linkageLogDays", 90);
            assertThat(result.getData()).containsEntry("enabled", false);
        }

        @Test
        @DisplayName("updateRetentionConfig rejects invalid sampleDays")
        void rejectsInvalidSampleDays() {
            Map<String, Object> config = Map.of("sampleDays", 0);
            assertThatThrownBy(() -> controller.updateRetentionConfig(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be between");
        }

        @Test
        @DisplayName("updateRetentionConfig rejects non-numeric value")
        void rejectsNonNumericValue() {
            Map<String, Object> config = Map.of("sampleDays", "not-a-number");
            assertThatThrownBy(() -> controller.updateRetentionConfig(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be a number");
        }

        @Test
        @DisplayName("updateRetentionConfig rejects value exceeding max")
        void rejectsExceedingMax() {
            Map<String, Object> config = Map.of("alarmLogDays", 5000);
            assertThatThrownBy(() -> controller.updateRetentionConfig(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be between");
        }

        @Test
        @DisplayName("updateRetentionConfig rejects fractional value")
        void rejectsFractionalValue() {
            Map<String, Object> config = Map.of("sampleDays", 90.5);
            assertThatThrownBy(() -> controller.updateRetentionConfig(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be a whole number");
        }

        @Test
        @DisplayName("updateRetentionConfig rejects non-boolean enabled")
        void rejectsNonBooleanEnabled() {
            Map<String, Object> config = Map.of("enabled", 42);
            assertThatThrownBy(() -> controller.updateRetentionConfig(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("enabled must be a boolean");
        }
    }
}
