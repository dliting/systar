package com.systar.server.scheduler;

import com.systar.common.config.SystemConfigManager;
import com.systar.data.service.DataRetentionService;
import com.systar.data.service.retention.RetentionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    private final DataRetentionService retentionService;
    private final SystemConfigManager configManager;

    public DataRetentionScheduler(DataRetentionService retentionService,
                                  SystemConfigManager configManager) {
        this.retentionService = retentionService;
        this.configManager   = configManager;
    }

    @Scheduled(cron = "${systar.retention.cron:0 0 3 * * ?}")
    public void executeRetention() {
        if (!configManager.getBoolValue("data_retention.enabled", true)) {
            log.debug("Data retention disabled; skipping scheduled cleanup");
            return;
        }

        log.info("Starting scheduled data retention cleanup...");
        try {
            RetentionSummary summary = retentionService.executeAll();
            log.info("Data retention complete: {}", summary);
        } catch (Exception e) {
            log.error("Data retention failed: {}", e.getMessage(), e);
        }
    }
}
