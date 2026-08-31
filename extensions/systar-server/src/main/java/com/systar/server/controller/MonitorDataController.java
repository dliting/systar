package com.systar.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.entity.SampleFloatEntity;
import com.systar.data.entity.SystemSettingEntity;
import com.systar.data.service.DataRetentionService;
import com.systar.data.service.SampleFloatService;
import com.systar.data.service.SystemSettingService;
import com.systar.data.service.impl.DataRetentionServiceImpl;
import com.systar.data.service.retention.RetentionSummary;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.Monitor;
import com.systar.monitor.server.MonitorServer;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.server.controller.vo.ProbeValueVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/monitor")
public class MonitorDataController {

    private static final Logger log = LoggerFactory.getLogger(MonitorDataController.class);
    private static final int MAX_IDS_LENGTH = 10000;
    private static final int DEFAULT_PAGE_SIZE_MAX = 500;

    private final MonitorServer        monitorServer;
    private final SampleFloatService   sampleFloatService;
    private final DataRetentionService retentionService;
    private final SystemConfigManager  configManager;
    private final SystemSettingService settingService;

    public MonitorDataController(MonitorServer monitorServer,
                                 SampleFloatService sampleFloatService,
                                 DataRetentionService retentionService,
                                 SystemConfigManager configManager,
                                 SystemSettingService settingService) {
        this.monitorServer      = monitorServer;
        this.sampleFloatService = sampleFloatService;
        this.retentionService   = retentionService;
        this.configManager      = configManager;
        this.settingService     = settingService;
    }

    @RequirePermission("iot:monitor:query")
    @GetMapping("/probe-values")
    public Result<List<ProbeValueVO>> getProbeValues(@RequestParam String ids) {
        if (ids == null || ids.isBlank()) {
            return Result.error("Parameter 'ids' is required.");
        }
        if (ids.length() > MAX_IDS_LENGTH) {
            return Result.error("Parameter 'ids' exceeds maximum allowed length of " + MAX_IDS_LENGTH + " characters.");
        }
        String[] parts = ids.split(",");
        List<ProbeValueVO> values = new ArrayList<>();
        for (String part : parts) {
            try {
                int id = Integer.parseInt(part.trim());
                Asset<?> asset = monitorServer.findAsset(id);
                if (asset instanceof Monitor<?> monitor) {
                    ProbeValueVO vo = new ProbeValueVO();
                    vo.setId(monitor.getId());
                    vo.setName(monitor.getName());
                    vo.setCaption(monitor.getCaption());
                    vo.setValue(monitor.getValue());
                    vo.setState(monitor.getState().name());
                    vo.setLastDetectTime(monitor.getLastDetectTimeMs());
                    values.add(vo);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid monitor id in probe-values request: {}", part);
            }
        }
        return Result.success(values);
    }

    @RequirePermission("iot:monitor:query")
    @GetMapping("/probe-history")
    public Result<Map<String, Object>> getProbeHistory(
            @RequestParam Integer monitorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {

        if (page < 1) {
            return Result.error("Page number must be >= 1");
        }
        if (size < 1 || size > DEFAULT_PAGE_SIZE_MAX) {
            return Result.error("Page size must be between 1 and " + DEFAULT_PAGE_SIZE_MAX);
        }

        QueryWrapper<SampleFloatEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("monitor", monitorId);
        if (startTime != null) {
            wrapper.ge("moment", startTime);
        }
        if (endTime != null) {
            wrapper.le("moment", endTime);
        }
        wrapper.orderByDesc("moment");

        Page<SampleFloatEntity> pageResult = sampleFloatService.page(
                new Page<>(page, size), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("total", pageResult.getTotal());
        data.put("page", pageResult.getCurrent());
        data.put("size", pageResult.getSize());
        data.put("records", pageResult.getRecords());
        return Result.success(data);
    }

    // ======================== data retention ========================

    @RequirePermission("iot:monitor:retention")
    @PostMapping("/data/retention/execute")
    public Result<RetentionSummary> executeRetention() {
        log.info("Manual data retention cleanup triggered");
        RetentionSummary summary = retentionService.executeAll();
        return Result.success(summary);
    }

    @RequirePermission("iot:monitor:retention")
    @GetMapping("/data/retention/config")
    public Result<Map<String, Object>> getRetentionConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sampleDays",     configManager.getIntValue("data_retention.sample_days", DataRetentionServiceImpl.DEFAULT_SAMPLE_DAYS));
        config.put("alarmLogDays",   configManager.getIntValue("data_retention.alarm_log_days", DataRetentionServiceImpl.DEFAULT_ALARM_LOG_DAYS));
        config.put("linkageLogDays", configManager.getIntValue("data_retention.linkage_log_days", DataRetentionServiceImpl.DEFAULT_LINKAGE_LOG_DAYS));
        config.put("enabled",        configManager.getBoolValue("data_retention.enabled", true));
        return Result.success(config);
    }

    @RequirePermission("iot:monitor:retention")
    @PutMapping("/data/retention/config")
    public Result<Void> updateRetentionConfig(@RequestBody Map<String, Object> config) {
        Map<String, String> updates = new LinkedHashMap<>();
        if (config.containsKey("sampleDays")) {
            int days = validateRetentionDays(config.get("sampleDays"), "sampleDays");
            updates.put("data_retention.sample_days", String.valueOf(days));
        }
        if (config.containsKey("alarmLogDays")) {
            int days = validateRetentionDays(config.get("alarmLogDays"), "alarmLogDays");
            updates.put("data_retention.alarm_log_days", String.valueOf(days));
        }
        if (config.containsKey("linkageLogDays")) {
            int days = validateRetentionDays(config.get("linkageLogDays"), "linkageLogDays");
            updates.put("data_retention.linkage_log_days", String.valueOf(days));
        }
        if (config.containsKey("enabled")) {
            Object val = config.get("enabled");
            if (!(val instanceof Boolean) && !(val instanceof String)) {
                throw new IllegalArgumentException("enabled must be a boolean");
            }
            updates.put("data_retention.enabled", String.valueOf(val));
        }

        for (Map.Entry<String, String> entry : updates.entrySet()) {
            upsertSetting(entry.getKey(), entry.getValue());
        }

        refreshConfigManager();

        log.info("Data retention config updated: {}", updates);
        return Result.success(null);
    }

    private int validateRetentionDays(Object value, String fieldName) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(fieldName + " must be a number");
        }
        Number num = (Number) value;
        if (num.doubleValue() != num.intValue()) {
            throw new IllegalArgumentException(fieldName + " must be a whole number");
        }
        int days = num.intValue();
        if (days < DataRetentionServiceImpl.MIN_RETENTION_DAYS
                || days > DataRetentionServiceImpl.MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + DataRetentionServiceImpl.MIN_RETENTION_DAYS
                    + " and " + DataRetentionServiceImpl.MAX_RETENTION_DAYS);
        }
        return days;
    }

    private void upsertSetting(String key, String value) {
        QueryWrapper<SystemSettingEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", key);
        SystemSettingEntity existing = settingService.getOne(wrapper);

        if (existing != null) {
            existing.setValue(value);
            settingService.updateById(existing);
        } else {
            SystemSettingEntity entity = new SystemSettingEntity();
            entity.setConfigKey(key);
            entity.setValue(value);
            settingService.save(entity);
        }
    }

    private void refreshConfigManager() {
        List<SystemSettingEntity> settings = settingService.list();
        Map<String, String> configMap = new HashMap<>();
        for (SystemSettingEntity entity : settings) {
            configMap.put(entity.getConfigKey(), entity.getValue());
        }
        configManager.loadConfigs(configMap);
    }
}