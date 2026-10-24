package com.systar.server.lifecycle;

import com.systar.common.code.CodeDictManager;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.entity.SystemSettingEntity;
import com.systar.data.service.CodeDictService;
import com.systar.data.service.SystemSettingService;
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
import static org.mockito.Mockito.*;

/**
 * The platform lifecycle owns only configuration and dictionary loading;
 * engine-side loading and startup/shutdown belong to the engine bootstrap.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ServerLifeCycleTest {

    private SystemSettingService systemSettingService;
    private CodeDictService codeDictService;
    private SystemConfigManager configManager;
    private CodeDictManager codeDictManager;
    private ServerLifeCycle lifecycle;

    @BeforeEach
    void setUp() {
        systemSettingService = mock(SystemSettingService.class);
        codeDictService = mock(CodeDictService.class);
        configManager = new SystemConfigManager();
        codeDictManager = new CodeDictManager();

        lifecycle = new ServerLifeCycle(
                systemSettingService,
                codeDictService,
                configManager,
                codeDictManager
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
        @DisplayName("startup propagates exceptions")
        void propagatesExceptions() throws Exception {
            when(systemSettingService.list()).thenThrow(new RuntimeException("db error"));

            assertThatThrownBy(() -> lifecycle.afterPropertiesSet())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db error");
        }
    }

    // ======================== destroy (shutdown) ========================

    @Nested
    @DisplayName("destroy - shutdown sequence")
    class Shutdown {

        @Test
        @DisplayName("destroy is a platform-only no-op")
        void destroyDoesNotTouchServices() throws Exception {
            lifecycle.destroy();

            verifyNoInteractions(systemSettingService, codeDictService);
        }
    }

    // ======================== system config loading ========================

    @Nested
    @DisplayName("System config loading")
    class SystemConfigLoading {

        @BeforeEach
        void stubDefaults() {
            when(codeDictService.list()).thenReturn(Collections.emptyList());
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

    // ======================== helpers ========================

    private SystemSettingEntity createSetting(String key, String value) {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey(key);
        entity.setValue(value);
        return entity;
    }
}
