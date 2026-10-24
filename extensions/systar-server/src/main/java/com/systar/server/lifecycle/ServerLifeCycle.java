package com.systar.server.lifecycle;

import com.systar.common.code.CodeDictManager;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.entity.CodeDictEntity;
import com.systar.data.entity.SystemSettingEntity;
import com.systar.data.service.CodeDictService;
import com.systar.data.service.SystemSettingService;
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
 * Orchestrates the platform startup sequence (loading configuration and
 * dictionaries) and graceful shutdown. Engine-side loading (assets, alarm
 * rules, linkage rules, scheduled tasks) and startup/shutdown are owned by
 * the engine's own bootstrap, not by this class.
 * <p>
 * Implements Spring's {@link InitializingBean} and {@link DisposableBean}
 * so that lifecycle hooks are called automatically by the container.
 */
@Component
public class ServerLifeCycle implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ServerLifeCycle.class);

    private final SystemSettingService systemSettingService;
    private final CodeDictService codeDictService;
    private final SystemConfigManager configManager;
    private final CodeDictManager codeDictManager;

    public ServerLifeCycle(SystemSettingService systemSettingService,
                           CodeDictService codeDictService,
                           SystemConfigManager configManager,
                           CodeDictManager codeDictManager) {
        this.systemSettingService = systemSettingService;
        this.codeDictService      = codeDictService;
        this.configManager        = configManager;
        this.codeDictManager      = codeDictManager;
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
}
