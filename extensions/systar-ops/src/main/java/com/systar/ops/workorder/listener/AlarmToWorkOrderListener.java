package com.systar.ops.workorder.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.common.config.SystemConfigManager;
import com.systar.data.event.AlarmPersistedEvent;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.mapper.WorkOrderMapper;
import com.systar.ops.workorder.service.WorkOrderService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AlarmToWorkOrderListener {

    private static final String CONFIG_TRIGGER_LEVELS = "ops.workorder.alarm_trigger_levels";

    private final WorkOrderService workOrderService;
    private final WorkOrderMapper workOrderMapper;
    private final AssetStore assetStore;
    private final SystemConfigManager configManager;

    public AlarmToWorkOrderListener(WorkOrderService workOrderService,
                                    WorkOrderMapper workOrderMapper,
                                    AssetStore assetStore,
                                    SystemConfigManager configManager) {
        this.workOrderService = workOrderService;
        this.workOrderMapper = workOrderMapper;
        this.assetStore = assetStore;
        this.configManager = configManager;
    }

    @EventListener
    public void onAlarmPersisted(AlarmPersistedEvent event) {
        Set<Integer> triggerLevels = parseTriggerLevels();
        if (!triggerLevels.contains(event.getEventRankId())) {
            return;
        }

        if (isDuplicate(event.getAlarmMessageId())) {
            return;
        }

        Integer deviceId = resolveDeviceId(event.getAssetId());
        if (deviceId == null) {
            return;
        }

        WorkOrderEntity order = new WorkOrderEntity();
        order.setTitle("Alarm-triggered work order");
        order.setType("REPAIR");
        order.setSource("ALARM_AUTO");
        order.setAlarmMessageId(event.getAlarmMessageId());
        order.setDeviceId(deviceId);
        order.setPriority(event.getEventRankId());
        order.setCreatorId(0L);
        workOrderService.createWorkOrder(order);
    }

    private Set<Integer> parseTriggerLevels() {
        String levels = configManager.getValue(CONFIG_TRIGGER_LEVELS, "2,3,4");
        return Arrays.stream(levels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    private boolean isDuplicate(int alarmMessageId) {
        LambdaQueryWrapper<WorkOrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrderEntity::getAlarmMessageId, alarmMessageId);
        return workOrderMapper.selectCount(wrapper) > 0;
    }

    private Integer resolveDeviceId(int assetId) {
        Asset<?> asset = assetStore.findAsset(assetId);
        while (asset != null) {
            if (asset.getKind() == AssetKind.DEVICE) {
                return asset.getId();
            }
            asset = asset.getParent();
        }
        return null;
    }
}
