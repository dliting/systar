package com.systar.server.lifecycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.systar.common.code.CodeDictManager;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.entity.*;
import com.systar.data.service.AlarmRuleService;
import com.systar.data.service.CodeDictService;
import com.systar.data.service.LinkageRuleCauseService;
import com.systar.data.service.LinkageRuleEffectService;
import com.systar.data.service.LinkageRuleService;
import com.systar.data.service.SystemSettingService;
import com.systar.monitor.alarm.AlarmRule;
import com.systar.monitor.asset.AssetLoader;
import com.systar.monitor.control.ScheduledTask;
import com.systar.monitor.control.ScheduledTaskRepository;
import com.systar.monitor.control.TimeControlService;
import com.systar.monitor.linkage.CauseType;
import com.systar.monitor.linkage.LinkageRuleBean;
import com.systar.monitor.linkage.LinkageRuleCauseBean;
import com.systar.monitor.linkage.LinkageRuleEffectBean;
import com.systar.monitor.server.MonitorServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application lifecycle manager.
 * <p>
 * Orchestrates the server startup sequence (loading configuration, dictionaries,
 * assets, alarm rules, and linkage rules) and graceful shutdown.
 * <p>
 * Implements Spring's {@link InitializingBean} and {@link DisposableBean}
 * so that lifecycle hooks are called automatically by the container.
 */
@Component
public class ServerLifeCycle implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ServerLifeCycle.class);

    private final MonitorServer monitorServer;
    private final SystemSettingService systemSettingService;
    private final CodeDictService codeDictService;
    private final AlarmRuleService alarmRuleService;
    private final LinkageRuleCauseService linkageRuleCauseService;
    private final LinkageRuleEffectService linkageRuleEffectService;
    private final LinkageRuleService linkageRuleService;
    private final SystemConfigManager configManager;
    private final CodeDictManager codeDictManager;
    private final AssetLoader assetLoader;
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final TimeControlService timeControlService;

    public ServerLifeCycle(MonitorServer monitorServer,
                           SystemSettingService systemSettingService,
                           CodeDictService codeDictService,
                           AlarmRuleService alarmRuleService,
                           LinkageRuleCauseService linkageRuleCauseService,
                           LinkageRuleEffectService linkageRuleEffectService,
                           LinkageRuleService linkageRuleService,
                           SystemConfigManager configManager,
                           CodeDictManager codeDictManager,
                           AssetLoader assetLoader,
                           ScheduledTaskRepository scheduledTaskRepository,
                           TimeControlService timeControlService) {
        this.monitorServer              = monitorServer;
        this.systemSettingService       = systemSettingService;
        this.codeDictService            = codeDictService;
        this.alarmRuleService           = alarmRuleService;
        this.linkageRuleCauseService    = linkageRuleCauseService;
        this.linkageRuleEffectService   = linkageRuleEffectService;
        this.linkageRuleService         = linkageRuleService;
        this.configManager              = configManager;
        this.codeDictManager            = codeDictManager;
        this.assetLoader                = assetLoader;
        this.scheduledTaskRepository    = scheduledTaskRepository;
        this.timeControlService         = timeControlService;
    }

    // ======================== startup ========================

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing Systar server...");

        try {
            // Step 1: Load system settings into SystemConfigManager
            loadSystemConfig();

            // Step 2: Load code dictionary into CodeDictManager
            loadCodeDict();

            // Step 3: Load assets from database and build asset tree
            monitorServer.loadAssets(assetLoader);

            // Step 4: Load alarm rules from database
            loadAlarmRules();

            // Step 5: Load linkage rules from database
            loadLinkageRules();

            // Step 6: Load scheduled control tasks from database (AssetStore must be ready)
            loadScheduledTasks();

            // Step 7: Start the MonitorServer
            String testMode = configManager.getValue("test");
            if (!"true".equals(testMode)) {
                monitorServer.startUp();
            } else {
                log.info("Test mode detected -- skipping MonitorServer startup.");
            }

            log.info("Systar server initialized successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize Systar server", e);
            throw e;
        }
    }

    // ======================== shutdown ========================

    @Override
    public void destroy() throws Exception {
        log.info("Shutting down Systar server...");
        monitorServer.shutDown();
        log.info("Systar server shut down complete.");
    }

    // ======================== private loading methods ========================

    /**
     * Loads all system settings from the database into the in-memory
     * {@link SystemConfigManager}.
     */
    private void loadSystemConfig() {
        log.info("Loading system configuration...");
        List<SystemSettingEntity> settings = systemSettingService.list();
        Map<String, String> configMap = new HashMap<>();
        for (SystemSettingEntity entity : settings) {
            configMap.put(entity.getConfigKey(), entity.getValue());
        }
        configManager.loadConfigs(configMap);
        log.info("System configuration loaded: {} entries.", configMap.size());
    }

    /**
     * Loads all code dictionary entries from the database into the in-memory
     * {@link CodeDictManager}.
     */
    private void loadCodeDict() {
        log.info("Loading code dictionary...");
        List<CodeDictEntity> entries = codeDictService.list();
        Map<Integer, List<CodeDictEntity>> grouped = entries.stream()
                .collect(Collectors.groupingBy(CodeDictEntity::getCatalogId));

        Map<Integer, com.systar.common.code.CodeCatalog> catalogMap = new HashMap<>();
        for (Map.Entry<Integer, List<CodeDictEntity>> entry : grouped.entrySet()) {
            com.systar.common.code.CodeCatalog catalog = new com.systar.common.code.CodeCatalog();
            catalog.setId(entry.getKey());
            for (CodeDictEntity dictEntity : entry.getValue()) {
                com.systar.common.code.CodeItem item = new com.systar.common.code.CodeItem();
                item.setId(dictEntity.getId());
                item.setName(dictEntity.getName());
                item.setCaption(dictEntity.getCaption());
                item.setParentId(dictEntity.getParentId());
                catalog.addItem(item);
            }
            catalogMap.put(entry.getKey(), catalog);
        }
        codeDictManager.loadCatalogs(catalogMap);
        log.info("Code dictionary loaded: {} catalogs, {} total entries.",
                catalogMap.size(), entries.size());
    }

    /**
     * Loads alarm rules from the database and passes them to the MonitorServer.
     */
    private void loadAlarmRules() {
        log.info("Loading alarm rules...");
        List<AlarmRuleEntity> entities = alarmRuleService.list(
                new QueryWrapper<AlarmRuleEntity>().eq("enabled", 1));
        List<AlarmRule> rules = entities.stream()
                .map(this::toAlarmRule)
                .collect(Collectors.toList());
        monitorServer.loadAlarmRules(rules);
        log.info("Alarm rules loaded: {} active rules.", rules.size());
    }

    /**
     * Loads linkage rule causes and effects from the database and passes them
     * to the MonitorServer.
     */
    private void loadLinkageRules() {
        log.info("Loading linkage rules...");
        List<LinkageRuleEntity> ruleEntities = linkageRuleService.list();
        List<LinkageRuleCauseEntity> causeEntities = linkageRuleCauseService.list();
        List<LinkageRuleEffectEntity> effectEntities = linkageRuleEffectService.list();

        List<LinkageRuleBean> rules = ruleEntities.stream()
                .map(this::toLinkageRule)
                .collect(Collectors.toList());

        List<LinkageRuleCauseBean> causes = causeEntities.stream()
                .map(this::toLinkageCause)
                .collect(Collectors.toList());

        List<LinkageRuleEffectBean> effects = effectEntities.stream()
                .map(this::toLinkageEffect)
                .collect(Collectors.toList());

        monitorServer.loadLinkageRules(rules, causes, effects);
        log.info("Linkage rules loaded: {} rules, {} causes, {} effects.",
                rules.size(), causes.size(), effects.size());
    }

    /**
     * Loads scheduled control tasks from the database and registers them with
     * the {@link TimeControlService}. Called after assets are loaded so that
     * task execution can resolve {@code controlId} via the {@code AssetStore}.
     */
    private void loadScheduledTasks() {
        log.info("Loading scheduled control tasks...");
        List<ScheduledTask> tasks = scheduledTaskRepository.findAll();
        timeControlService.loadTasks(tasks);
        log.info("Scheduled tasks loaded: {} total.", tasks.size());
    }

    // ======================== entity converters ========================

    private AlarmRule toAlarmRule(AlarmRuleEntity entity) {
        AlarmRule rule = new AlarmRule();
        rule.setId(entity.getId());
        rule.setMonitorId(entity.getMonitorId());
        rule.setStrategy(entity.getStrategy());
        rule.setEventRankId(entity.getEventRankId());
        rule.setMessageTemplate(entity.getMessageTemplate());
        rule.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        return rule;
    }

    private LinkageRuleBean toLinkageRule(LinkageRuleEntity entity) {
        LinkageRuleBean bean = new LinkageRuleBean();
        bean.setId(entity.getId());
        bean.setName(entity.getName());
        bean.setCauseType(CauseType.valueOf(entity.getCauseType()));
        bean.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        bean.setCaption(entity.getCaption());
        return bean;
    }

    private LinkageRuleCauseBean toLinkageCause(LinkageRuleCauseEntity entity) {
        LinkageRuleCauseBean bean = new LinkageRuleCauseBean();
        bean.setId(entity.getId());
        bean.setRuleId(entity.getRuleId());
        bean.setCauseMonitorId(entity.getCauseMonitorId());
        bean.setTriggerValue(entity.getTriggerValue());
        return bean;
    }

    private LinkageRuleEffectBean toLinkageEffect(LinkageRuleEffectEntity entity) {
        LinkageRuleEffectBean bean = new LinkageRuleEffectBean();
        bean.setId(entity.getId());
        bean.setRuleId(entity.getRuleId());
        bean.setEffectMonitorId(entity.getEffectMonitorId());
        bean.setEffectCommand(entity.getEffectCommand());
        return bean;
    }
}
