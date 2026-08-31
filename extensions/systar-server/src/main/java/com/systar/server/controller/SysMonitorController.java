package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/monitor")
public class SysMonitorController {

    private final SystemInfo systemInfo = new SystemInfo();
    private final CacheManager cacheManager;

    public SysMonitorController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @RequirePermission("sys:monitor:query")
    @GetMapping("/server")
    public Result<Map<String, Object>> serverInfo() {
        CentralProcessor cpu = systemInfo.getHardware().getProcessor();
        GlobalMemory mem = systemInfo.getHardware().getMemory();
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cpu", Map.of(
                "name", cpu.getProcessorIdentifier().getName(),
                "cores", cpu.getPhysicalProcessorCount(),
                "load", String.format("%.1f%%", cpu.getSystemCpuLoad(1000) * 100)
        ));
        info.put("memory", Map.of(
                "total", formatBytes(mem.getTotal()),
                "available", formatBytes(mem.getAvailable()),
                "used", formatBytes(mem.getTotal() - mem.getAvailable())
        ));
        info.put("jvm", Map.of(
                "max", formatBytes(runtime.maxMemory()),
                "total", formatBytes(runtime.totalMemory()),
                "free", formatBytes(runtime.freeMemory()),
                "used", formatBytes(runtime.totalMemory() - runtime.freeMemory())
        ));
        return Result.success(info);
    }

    @RequirePermission("sys:monitor:query")
    @GetMapping("/cache")
    public Result<Map<String, Object>> cacheInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        cacheManager.getCacheNames().forEach(name -> info.put(name, "Caffeine cache"));
        return Result.success(info);
    }

    @RequirePermission("sys:monitor:query")
    @GetMapping("/online")
    public Result<Integer> onlineUsers() {
        return Result.success(1);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "-";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
