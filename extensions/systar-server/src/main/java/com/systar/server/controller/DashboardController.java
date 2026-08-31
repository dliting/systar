package com.systar.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.service.AlarmMessageService;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetState;
import com.systar.monitor.server.MonitorServer;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.server.model.DashboardStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class DashboardController {

    private final MonitorServer monitorServer;
    private final AlarmMessageService alarmMessageService;

    public DashboardController(MonitorServer monitorServer, AlarmMessageService alarmMessageService) {
        this.monitorServer = monitorServer;
        this.alarmMessageService = alarmMessageService;
    }

    @RequirePermission("iot:dashboard:view")
    @GetMapping("/dashboard")
    public Result<DashboardStats> getDashboard() {
        DashboardStats stats = new DashboardStats();

        int totalDevices = 0;
        int onlineDevices = 0;
        int totalProbes = 0;
        Map<String, Integer> stateCounts = new HashMap<>();
        for (AssetState s : AssetState.values()) {
            stateCounts.put(s.name(), 0);
        }

        for (Asset<?> asset : monitorServer.getAssets()) {
            AssetState state = asset.getState();
            stateCounts.merge(state.name(), 1, Integer::sum);

            if (asset.isEnabled()) {
                AssetKind kind = asset.getKind();
                if (kind == AssetKind.DEVICE) {
                    totalDevices++;
                    if (state == AssetState.NORMAL) {
                        onlineDevices++;
                    }
                } else if (kind == AssetKind.PROBE) {
                    totalProbes++;
                }
            }
        }

        long totalAlarms = alarmMessageService.count();
        long pendingAlarms = alarmMessageService.count(
                new LambdaQueryWrapper<AlarmMessageEntity>()
                        .eq(AlarmMessageEntity::getState, AlarmMessageEntity.STATE_PENDING)
                        .eq(AlarmMessageEntity::getRecovered, AlarmMessageEntity.RECOVERED_NO));

        stats.setTotalDevices(totalDevices);
        stats.setOnlineDevices(onlineDevices);
        stats.setTotalProbes(totalProbes);
        stats.setTotalAlarms(Math.toIntExact(totalAlarms));
        stats.setPendingAlarms(Math.toIntExact(pendingAlarms));
        stats.setAssetsByState(stateCounts);

        return Result.success(stats);
    }
}
