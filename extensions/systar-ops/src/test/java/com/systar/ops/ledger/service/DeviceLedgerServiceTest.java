package com.systar.ops.ledger.service;

import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;
import com.systar.ops.ledger.entity.DeviceAttributeEntity;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import com.systar.ops.ledger.mapper.DeviceAttributeMapper;
import com.systar.ops.ledger.mapper.MaintenanceRecordMapper;
import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DeviceLedgerServiceTest {

    @Autowired
    private DeviceLedgerService service;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbc;

    @Autowired
    private DeviceAttributeMapper attributeMapper;

    @Autowired
    private MaintenanceRecordMapper maintenanceRecordMapper;

    @Test
    void getDeviceLedger_filtersBySpaceCatalogAndLifecycle() {
        insertDevice(1101, 10, (short) 201, "IN_SERVICE", LocalDate.of(2026, 7, 1), null);
        insertDevice(1102, 10, (short) 202, "IN_SERVICE", LocalDate.of(2026, 7, 1), null);
        insertDevice(1103, 11, (short) 201, "RETIRED", LocalDate.of(2026, 7, 1), null);

        PagedResult<DeviceDto> page = service.getDeviceLedger(1, 10, 10, (short) 201, "IN_SERVICE");

        assertThat(page.records()).extracting(DeviceDto::id).containsExactly(1101);
    }

    @Test
    void getDeviceDetail_returnsDeviceAttributesAndRecentMaintenanceRecords() {
        insertDevice(1201, 10, (short) 201, "IN_SERVICE", LocalDate.of(2026, 7, 1), null);
        service.batchSetAttributes(1201, Map.of("ip", "192.168.1.10"));
        insertMaintenanceRecord(1201, LocalDateTime.of(2026, 5, 1, 9, 0));

        Map<String, Object> detail = service.getDeviceDetail(1201);

        assertThat(((DeviceDto) detail.get("device")).id()).isEqualTo(1201);
        assertThat((List<?>) detail.get("attributes")).hasSize(1);
        assertThat((List<?>) detail.get("maintenanceRecords")).hasSize(1);
    }

    @Test
    void batchSetAttributes_upsertsExistingAttribute() {
        insertDevice(1301, 10, (short) 201, "IN_SERVICE", LocalDate.of(2026, 7, 1), null);

        service.batchSetAttributes(1301, Map.of("ip", "192.168.1.10"));
        service.batchSetAttributes(1301, Map.of("ip", "192.168.1.11"));

        List<DeviceAttributeEntity> attributes = attributeMapper.selectList(null);
        assertThat(attributes).hasSize(1);
        assertThat(attributes.get(0).getAttrValue()).isEqualTo("192.168.1.11");
    }

    @Test
    void deleteAttribute_removesDeviceAttributeByKey() {
        insertDevice(1401, 10, (short) 201, "IN_SERVICE", LocalDate.of(2026, 7, 1), null);
        service.batchSetAttributes(1401, Map.of("ip", "192.168.1.10", "port", "502"));

        service.deleteAttribute(1401, "ip");

        List<DeviceAttributeEntity> attributes = attributeMapper.selectList(null);
        assertThat(attributes).extracting(DeviceAttributeEntity::getAttrKey).containsExactly("port");
    }

    @Test
    void getWarrantyExpiringDevices_returnsDevicesWithinRange() {
        insertDevice(1501, 10, (short) 201, "IN_SERVICE", LocalDate.now().plusDays(5), null);
        insertDevice(1502, 10, (short) 201, "IN_SERVICE", LocalDate.now().plusDays(40), null);

        List<DeviceDto> devices = service.getWarrantyExpiringDevices(LocalDate.now().plusDays(30));

        assertThat(devices).extracting(DeviceDto::id).contains(1501).doesNotContain(1502);
    }

    private void insertDevice(Integer id, Integer parentId, Short catalog, String lifecycleStatus,
                              LocalDate warrantyDate, Integer maintenanceCycle) {
        jdbc.update(
                "INSERT INTO t_device (id, name, parent, catalog, lifecycle_status, warranty_date, maintenance_cycle) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, "device_" + id, parentId, catalog, lifecycleStatus, warrantyDate, maintenanceCycle);
    }

    private void insertMaintenanceRecord(Integer deviceId, LocalDateTime performedAt) {
        MaintenanceRecordEntity record = new MaintenanceRecordEntity();
        record.setDeviceId(deviceId);
        record.setType("MAINTENANCE");
        record.setTitle("Monthly maintenance");
        record.setPerformerId(1L);
        record.setCreatorId(2L);
        record.setPerformedAt(performedAt);
        record.setCreatedAt(performedAt);
        record.setUpdatedAt(performedAt);
        maintenanceRecordMapper.insert(record);
    }
}
