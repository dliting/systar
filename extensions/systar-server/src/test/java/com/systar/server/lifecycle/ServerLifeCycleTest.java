package com.systar.server.lifecycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.systar.common.code.CodeDictManager;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.entity.AlarmRuleEntity;
import com.systar.data.entity.CodeDictEntity;
import com.systar.data.entity.LinkageRuleCauseEntity;
import com.systar.data.entity.LinkageRuleEffectEntity;
import com.systar.data.entity.LinkageRuleEntity;
import com.systar.data.entity.SystemSettingEntity;
import com.systar.data.service.AlarmRuleService;
import com.systar.data.service.CodeDictService;
import com.systar.data.service.LinkageRuleCauseService;
import com.systar.data.service.LinkageRuleEffectService;
import com.systar.data.service.LinkageRuleService;
import com.systar.data.service.SystemSettingService;
import com.systar.monitor.alarm.AlarmRule;
import com.systar.monitor.alarm.AlarmStrategy;
import com.systar.monitor.asset.AssetLoader;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.control.ScheduledTaskLogRepository;
import com.systar.monitor.control.ScheduledTaskRepository;
import com.systar.monitor.control.TimeControlService;
import com.systar.monitor.server.MonitorServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ServerLifeCycleTest {

    private MonitorServer monitorServer;
    private SystemSettingService systemSettingService;
    private CodeDictService codeDictService;
    private AlarmRuleService alarmRuleService;
    private LinkageRuleCauseService linkageRuleCauseService;
    private LinkageRuleEffectService linkageRuleEffectService;
    private LinkageRuleService linkageRuleService;
    private SystemConfigManager configManager;
    private CodeDictManager codeDictManager;
    private AssetLoader assetLoader;
    private ScheduledTaskRepository scheduledTaskRepository;
    private TimeControlService timeControlService;
    private ServerLifeCycle lifecycle;

    @BeforeEach
    void setUp() {
        monitorServer = mock(MonitorServer.class);
        systemSettingService = mock(SystemSettingService.class);
        codeDictService = mock(CodeDictService.class);
        alarmRuleService = mock(AlarmRuleService.class);
        linkageRuleCauseService = mock(LinkageRuleCauseService.class);
        linkageRuleEffectService = mock(LinkageRuleEffectService.class);
        linkageRuleService = mock(LinkageRuleService.class);
        configManager = new SystemConfigManager();
        codeDictManager = new CodeDictManager();
        assetLoader = mock(AssetLoader.class);
        scheduledTaskRepository = mock(ScheduledTaskRepository.class);
        timeControlService = new TimeControlService(
                mock(AssetStore.class), mock(ScheduledTaskLogRepository.class));

        lifecycle = new ServerLifeCycle(
                monitorServer,
                systemSettingService,
                codeDictService,
                alarmRuleService,
                linkageRuleCauseService,
                linkageRuleEffectService,
                linkageRuleService,
                configManager,
                codeDictManager,
                assetLoader,
                scheduledTaskRepository,
                timeControlService
        );
    }

    // ======================== afterPropertiesSet (startup) ========================

    @Nested
    @DisplayName("afterPropertiesSet - startup sequence")
    class Startup {

        @BeforeEach
        void stubDefaults() {
            when(systemSettingService.list()).thenReturn(Collections.emptyList());
            when(codeDictService.list()).thenReturn(Collections.emptyList());
            when(alarmRuleService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(linkageRuleCauseService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleEffectService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleService.list()).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("startup loads system config")
        void loadsSystemConfig() throws Exception {
            lifecycle.afterPropertiesSet();

            verify(systemSettingService).list();
        }

        @Test
        @DisplayName("startup loads code dictionary")
        void loadsCodeDict() throws Exception {
            lifecycle.afterPropertiesSet();

            verify(codeDictService).list();
        }

        @Test
        @DisplayName("startup loads assets via assetLoader")
        void loadsAssets() throws Exception {
            lifecycle.afterPropertiesSet();

            verify(monitorServer).loadAssets(assetLoader);
        }

        @Test
        @DisplayName("startup loads alarm rules")
        void loadsAlarmRules() throws Exception {
            lifecycle.afterPropertiesSet();

            verify(alarmRuleService).list(any(QueryWrapper.class));
            verify(monitorServer).loadAlarmRules(anyList());
        }

        @Test
        @DisplayName("startup loads linkage rules")
        void loadsLinkageRules() throws Exception {
            lifecycle.afterPropertiesSet();

            verify(linkageRuleCauseService).list();
            verify(linkageRuleEffectService).list();
            verify(linkageRuleService).list();
            verify(monitorServer).loadLinkageRules(anyList(), anyList(), anyList());
        }

        @Test
        @DisplayName("startup calls monitorServer.startUp() in non-test mode")
        void startsServer() throws Exception {
            // configManager has no "test" key, so testMode is null
            lifecycle.afterPropertiesSet();

            verify(monitorServer).startUp();
        }

        @Test
        @DisplayName("startup skips monitorServer.startUp() in test mode")
        void skipsStartupInTestMode() throws Exception {
            List<SystemSettingEntity> testSettings = new ArrayList<>();
            testSettings.add(createSetting("test", "true"));
            when(systemSettingService.list()).thenReturn(testSettings);

            lifecycle.afterPropertiesSet();

            verify(monitorServer, never()).startUp();
        }

        @Test
        @DisplayName("startup propagates exceptions")
        void propagatesExceptions() throws Exception {
            when(systemSettingService.list()).thenThrow(new RuntimeException("db error"));

            assertThatThrownBy(() -> lifecycle.afterPropertiesSet())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db error");
        }

        @Test
        @DisplayName("startup loads scheduled tasks via repository and passes to TimeControlService")
        void loadsScheduledTasks() throws Exception {
            lifecycle.afterPropertiesSet();

            verify(scheduledTaskRepository).findAll();
            verifyNoMoreInteractions(scheduledTaskRepository);
        }
    }

    // ======================== destroy (shutdown) ========================

    @Nested
    @DisplayName("destroy - shutdown sequence")
    class Shutdown {

        @Test
        @DisplayName("destroy calls monitorServer.shutDown()")
        void destroyCallsShutdown() throws Exception {
            lifecycle.destroy();

            verify(monitorServer).shutDown();
        }

        @Test
        @DisplayName("destroy does not throw even if shutDown fails")
        void destroyHandlesException() throws Exception {
            doThrow(new RuntimeException("shutdown error")).when(monitorServer).shutDown();

            assertThatThrownBy(() -> lifecycle.destroy())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ======================== system config loading ========================

    @Nested
    @DisplayName("System config loading")
    class SystemConfigLoading {

        @BeforeEach
        void stubDefaults() {
            when(codeDictService.list()).thenReturn(Collections.emptyList());
            when(alarmRuleService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(linkageRuleCauseService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleEffectService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleService.list()).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("loads config entries into SystemConfigManager")
        void loadsConfigEntries() throws Exception {
            List<SystemSettingEntity> settings = new ArrayList<>();
            settings.add(createSetting("key1", "value1"));
            settings.add(createSetting("key2", "value2"));
            when(systemSettingService.list()).thenReturn(settings);

            lifecycle.afterPropertiesSet();

            assertThat(configManager.getValue("key1")).isEqualTo("value1");
            assertThat(configManager.getValue("key2")).isEqualTo("value2");
        }
    }

    // ======================== alarm rule loading ========================

    @Nested
    @DisplayName("Alarm rule loading")
    class AlarmRuleLoading {

        @BeforeEach
        void stubDefaults() {
            when(systemSettingService.list()).thenReturn(Collections.emptyList());
            when(codeDictService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleCauseService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleEffectService.list()).thenReturn(Collections.emptyList());
            when(linkageRuleService.list()).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("loads and converts alarm rule entities")
        void loadsAlarmRules() throws Exception {
            AlarmRuleEntity entity = new AlarmRuleEntity();
            entity.setId(1);
            entity.setMonitorId(10);
            entity.setStrategy(AlarmStrategy.CONTINUOUS);
            entity.setEventRankId(2);
            entity.setMessageTemplate("Value too high");
            entity.setEnabled(1);
            when(alarmRuleService.list(any(QueryWrapper.class))).thenReturn(List.of(entity));

            lifecycle.afterPropertiesSet();

            verify(monitorServer).loadAlarmRules(argThat(rules -> {
                if (rules.size() != 1) return false;
                AlarmRule rule = rules.get(0);
                return rule.getId() == 1
                        && rule.getMonitorId() == 10
                        && rule.getStrategy() == AlarmStrategy.CONTINUOUS
                        && rule.isEnabled();
            }));
        }

        @Test
        @DisplayName("disabled alarm rules have enabled=false")
        void disabledAlarmRule() throws Exception {
            AlarmRuleEntity entity = new AlarmRuleEntity();
            entity.setId(2);
            entity.setMonitorId(20);
            entity.setStrategy(AlarmStrategy.ONLY_ONCE);
            entity.setEventRankId(1);
            entity.setEnabled(0);
            when(alarmRuleService.list(any(QueryWrapper.class))).thenReturn(List.of(entity));

            lifecycle.afterPropertiesSet();

            verify(monitorServer).loadAlarmRules(argThat(rules ->
                    !rules.get(0).isEnabled()));
        }
    }

    // ======================== helpers ========================

    private SystemSettingEntity createSetting(String key, String value) {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey(key);
        entity.setValue(value);
        return entity;
    }
}
