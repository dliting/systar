package com.systar.server.controller;

import com.systar.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SysMonitorControllerTest {

    private SysMonitorController controller;

    @BeforeEach
    void setUp() {
        CacheManager cacheManager = new ConcurrentMapCacheManager("dashboard");
        controller = new SysMonitorController(cacheManager);
    }

    @Test
    void serverInfo_shouldReturnCpuMemoryJvm() {
        Result<Map<String, Object>> result = controller.serverInfo();

        assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
        Map<String, Object> data = result.getData();
        assertThat(data).containsKeys("cpu", "memory", "jvm");

        @SuppressWarnings("unchecked")
        Map<String, Object> cpu = (Map<String, Object>) data.get("cpu");
        assertThat(cpu).containsKey("name");
        assertThat(cpu).containsKey("cores");

        @SuppressWarnings("unchecked")
        Map<String, Object> memory = (Map<String, Object>) data.get("memory");
        assertThat(memory).containsKey("total");

        @SuppressWarnings("unchecked")
        Map<String, Object> jvm = (Map<String, Object>) data.get("jvm");
        assertThat(jvm).containsKey("max");
    }

    @Test
    void cacheInfo_shouldReturnCacheNames() {
        Result<Map<String, Object>> result = controller.cacheInfo();

        assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
        assertThat(result.getData()).containsKey("dashboard");
    }

    @Test
    void onlineUsers_shouldReturnAtLeastOne() {
        Result<Integer> result = controller.onlineUsers();

        assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
        assertThat(result.getData()).isGreaterThanOrEqualTo(1);
    }
}
