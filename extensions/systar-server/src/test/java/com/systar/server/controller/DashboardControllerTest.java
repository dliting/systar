package com.systar.server.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.service.AlarmMessageService;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetState;
import com.systar.monitor.server.MonitorServer;
import com.systar.common.api.Result;
import com.systar.server.model.DashboardStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DashboardControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnDashboardStats() {
        MonitorServer monitorServer = mock(MonitorServer.class);
        AlarmMessageService alarmService = mock(AlarmMessageService.class);

        Asset device1 = createAsset(AssetKind.DEVICE, AssetState.NORMAL, true);
        Asset device2 = createAsset(AssetKind.DEVICE, AssetState.WARNING, true);
        Asset device3 = createAsset(AssetKind.DEVICE, AssetState.OFFLINE, false);
        Asset probe1 = createAsset(AssetKind.PROBE, AssetState.NORMAL, true);
        Asset probe2 = createAsset(AssetKind.PROBE, AssetState.ERROR, true);

        when(monitorServer.getAssets()).thenReturn(List.of(device1, device2, device3, probe1, probe2));
        when(alarmService.count()).thenReturn(10L);
        when(alarmService.count(any(LambdaQueryWrapper.class))).thenReturn(3L);

        DashboardController controller = new DashboardController(monitorServer, alarmService);
        Result<DashboardStats> result = controller.getDashboard();

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        DashboardStats stats = result.getData();
        assertNotNull(stats);
        assertEquals(2, stats.getTotalDevices());
        assertEquals(1, stats.getOnlineDevices());
        assertEquals(2, stats.getTotalProbes());
        assertEquals(10, stats.getTotalAlarms());
        assertEquals(3, stats.getPendingAlarms());

        Map<String, Integer> stateCounts = stats.getAssetsByState();
        assertEquals(2, stateCounts.get("NORMAL"));
        assertEquals(1, stateCounts.get("WARNING"));
        assertEquals(1, stateCounts.get("ERROR"));
        assertEquals(1, stateCounts.get("OFFLINE"));
    }

    @Test
    void shouldHandleEmptyAssets() {
        MonitorServer monitorServer = mock(MonitorServer.class);
        AlarmMessageService alarmService = mock(AlarmMessageService.class);

        when(monitorServer.getAssets()).thenReturn(List.of());
        when(alarmService.count()).thenReturn(0L);
        when(alarmService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);

        DashboardController controller = new DashboardController(monitorServer, alarmService);
        Result<DashboardStats> result = controller.getDashboard();

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertEquals(0, result.getData().getTotalDevices());
        assertEquals(0, result.getData().getTotalProbes());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Asset createAsset(AssetKind kind, AssetState state, boolean enabled) {
        Asset asset = mock(Asset.class);
        when(asset.getKind()).thenReturn(kind);
        when(asset.getState()).thenReturn(state);
        when(asset.isEnabled()).thenReturn(enabled);
        return asset;
    }
}
