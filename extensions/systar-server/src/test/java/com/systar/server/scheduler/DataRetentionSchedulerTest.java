package com.systar.server.scheduler;

import com.systar.common.config.SystemConfigManager;
import com.systar.data.service.DataRetentionService;
import com.systar.data.service.retention.RetentionResult;
import com.systar.data.service.retention.RetentionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DataRetentionSchedulerTest {

    private SystemConfigManager configManager;
    private DataRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        configManager = new SystemConfigManager();
    }

    private RetentionSummary emptySummary() {
        RetentionResult zero = new RetentionResult("none", 0);
        return new RetentionSummary(zero, zero, zero, zero, zero, zero, zero);
    }

    @Test
    @DisplayName("executes retention when enabled=true")
    void executesWhenEnabled() {
        configManager.loadConfigs(Map.of("data_retention.enabled", "true"));

        boolean[] executed = {false};
        DataRetentionService service = new DataRetentionService() {
            @Override
            public RetentionSummary executeAll() {
                executed[0] = true;
                return emptySummary();
            }
        };

        scheduler = new DataRetentionScheduler(service, configManager);
        scheduler.executeRetention();

        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("skips retention when enabled=false")
    void skipsWhenDisabled() {
        configManager.loadConfigs(Map.of("data_retention.enabled", "false"));

        boolean[] executed = {false};
        DataRetentionService service = new DataRetentionService() {
            @Override
            public RetentionSummary executeAll() {
                executed[0] = true;
                return emptySummary();
            }
        };

        scheduler = new DataRetentionScheduler(service, configManager);
        scheduler.executeRetention();

        assertThat(executed[0]).isFalse();
    }

    @Test
    @DisplayName("defaults to enabled when config key missing")
    void defaultsToEnabled() {
        // No data_retention.enabled key set
        configManager.loadConfigs(Map.of());

        boolean[] executed = {false};
        DataRetentionService service = new DataRetentionService() {
            @Override
            public RetentionSummary executeAll() {
                executed[0] = true;
                return emptySummary();
            }
        };

        scheduler = new DataRetentionScheduler(service, configManager);
        scheduler.executeRetention();

        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("does not throw when service throws exception")
    void handlesServiceException() {
        configManager.loadConfigs(Map.of("data_retention.enabled", "true"));

        DataRetentionService service = new DataRetentionService() {
            @Override
            public RetentionSummary executeAll() {
                throw new RuntimeException("test error");
            }
        };

        scheduler = new DataRetentionScheduler(service, configManager);
        assertThatCode(() -> scheduler.executeRetention()).doesNotThrowAnyException();
    }
}
