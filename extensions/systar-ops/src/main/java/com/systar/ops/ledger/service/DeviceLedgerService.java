package com.systar.ops.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.common.database.DatabaseDialect;
import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;
import com.systar.common.service.DeviceInfoProvider;
import com.systar.ops.ledger.entity.DeviceAttributeEntity;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import com.systar.ops.ledger.mapper.DeviceAttributeMapper;
import com.systar.ops.ledger.mapper.MaintenanceRecordMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceLedgerService {

    private static final int RECENT_MAINTENANCE_LIMIT = 10;
    private static final String DEFAULT_ATTRIBUTE_TYPE = "STRING";

    private final DeviceInfoProvider deviceInfo;
    private final DeviceAttributeMapper attributeMapper;
    private final MaintenanceRecordMapper maintenanceRecordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseDialect databaseDialect;

    public DeviceLedgerService(DeviceInfoProvider deviceInfo,
                               DeviceAttributeMapper attributeMapper,
                               MaintenanceRecordMapper maintenanceRecordMapper,
                               @Qualifier("mainJdbcTemplate") JdbcTemplate jdbcTemplate,
                               DatabaseDialect databaseDialect) {
        this.deviceInfo = deviceInfo;
        this.attributeMapper = attributeMapper;
        this.maintenanceRecordMapper = maintenanceRecordMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseDialect = databaseDialect;
    }

    public PagedResult<DeviceDto> getDeviceLedger(int page, int size, Integer spaceId, Short catalog, String lifecycleStatus) {
        return deviceInfo.listDevices(spaceId, catalog, lifecycleStatus, page, size);
    }

    public Map<String, Object> getDeviceDetail(Integer deviceId) {
        DeviceDto device = deviceInfo.getById(deviceId);
        List<DeviceAttributeEntity> attributes = attributeMapper.selectList(new LambdaQueryWrapper<DeviceAttributeEntity>()
                .eq(DeviceAttributeEntity::getDeviceId, deviceId)
                .orderByAsc(DeviceAttributeEntity::getAttrKey));
        List<MaintenanceRecordEntity> records = maintenanceRecordMapper.selectList(new LambdaQueryWrapper<MaintenanceRecordEntity>()
                .eq(MaintenanceRecordEntity::getDeviceId, deviceId)
                .orderByDesc(MaintenanceRecordEntity::getPerformedAt)
                .last("LIMIT " + RECENT_MAINTENANCE_LIMIT));
        Map<String, Object> detail = new HashMap<>();
        detail.put("device", device);
        detail.put("attributes", attributes);
        detail.put("maintenanceRecords", records);
        return detail;
    }

    public List<DeviceAttributeEntity> getAttributes(Integer deviceId) {
        return attributeMapper.selectList(new LambdaQueryWrapper<DeviceAttributeEntity>()
                .eq(DeviceAttributeEntity::getDeviceId, deviceId)
                .orderByAsc(DeviceAttributeEntity::getAttrKey));
    }

    @Transactional
    public void batchSetAttributes(Integer deviceId, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            DeviceAttributeEntity attribute = new DeviceAttributeEntity();
            attribute.setDeviceId(deviceId);
            attribute.setAttrKey(entry.getKey());
            attribute.setAttrValue(entry.getValue());
            attribute.setAttrType(DEFAULT_ATTRIBUTE_TYPE);
            saveOrUpdateAttribute(attribute);
        }
    }

    @Transactional
    public void batchSetAttributeEntities(Integer deviceId, List<DeviceAttributeEntity> attributes) {
        for (DeviceAttributeEntity attribute : attributes) {
            attribute.setDeviceId(deviceId);
            if (attribute.getAttrType() == null || attribute.getAttrType().isBlank()) {
                attribute.setAttrType(DEFAULT_ATTRIBUTE_TYPE);
            }
            saveOrUpdateAttribute(attribute);
        }
    }

    public void deleteAttribute(Integer deviceId, String attrKey) {
        attributeMapper.delete(new LambdaQueryWrapper<DeviceAttributeEntity>()
                .eq(DeviceAttributeEntity::getDeviceId, deviceId)
                .eq(DeviceAttributeEntity::getAttrKey, attrKey));
    }

    public List<DeviceDto> getWarrantyExpiringDevices(LocalDate before) {
        return deviceInfo.findWarrantyExpiring(before);
    }

    private void saveOrUpdateAttribute(DeviceAttributeEntity attribute) {
        jdbcTemplate.update(databaseDialect.getUpsertAttributeSql(),
                attribute.getDeviceId(),
                attribute.getAttrKey(),
                attribute.getAttrValue(),
                attribute.getAttrType());
    }
}
