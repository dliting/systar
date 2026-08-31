package com.systar.ops.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.dto.DeviceDto;
import com.systar.common.service.DeviceInfoProvider;
import com.systar.common.service.DeviceLifecycleManager;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import com.systar.ops.ledger.mapper.MaintenanceRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class MaintenanceRecordService {

    private final MaintenanceRecordMapper maintenanceRecordMapper;
    private final DeviceInfoProvider deviceInfo;
    private final DeviceLifecycleManager lifecycleManager;

    public MaintenanceRecordService(MaintenanceRecordMapper maintenanceRecordMapper,
                                    DeviceInfoProvider deviceInfo,
                                    DeviceLifecycleManager lifecycleManager) {
        this.maintenanceRecordMapper = maintenanceRecordMapper;
        this.deviceInfo = deviceInfo;
        this.lifecycleManager = lifecycleManager;
    }

    @Transactional
    public MaintenanceRecordEntity create(MaintenanceRecordEntity record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getPerformedAt() == null) {
            record.setPerformedAt(now);
        }
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        DeviceDto device = deviceInfo.getById(record.getDeviceId());
        LocalDate performedDate = record.getPerformedAt().toLocalDate();
        if (record.getNextMaintenanceDate() == null && device != null && device.maintenanceCycle() != null) {
            record.setNextMaintenanceDate(performedDate.plusDays(device.maintenanceCycle()));
        }

        maintenanceRecordMapper.insert(record);
        if (device != null) {
            lifecycleManager.updateLastMaintenanceDate(record.getDeviceId(), performedDate);
        }
        return record;
    }

    public MaintenanceRecordEntity getById(Long id) {
        return maintenanceRecordMapper.selectById(id);
    }

    public Page<MaintenanceRecordEntity> listByDevice(Integer deviceId, int page, int size) {
        LambdaQueryWrapper<MaintenanceRecordEntity> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) wrapper.eq(MaintenanceRecordEntity::getDeviceId, deviceId);
        wrapper.orderByDesc(MaintenanceRecordEntity::getPerformedAt);
        return maintenanceRecordMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
