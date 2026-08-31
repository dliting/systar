package com.systar.ops.ledger.service;

import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MaintenanceRecordServiceTest {

    @Autowired
    private MaintenanceRecordService service;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbc;

    @Test
    void create_updatesDeviceLastMaintenanceDateAndCalculatesNextDate() {
        insertDevice(2101, 30);
        MaintenanceRecordEntity record = newRecord(2101, LocalDateTime.of(2026, 5, 10, 8, 30));

        service.create(record);

        LocalDate lastMaintDate = getDeviceLastMaintenanceDate(2101);
        assertThat(lastMaintDate).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(record.getNextMaintenanceDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(record.getId()).isNotNull();
    }

    @Test
    void create_preservesExplicitNextMaintenanceDate() {
        insertDevice(2102, 30);
        MaintenanceRecordEntity record = newRecord(2102, LocalDateTime.of(2026, 5, 10, 8, 30));
        record.setNextMaintenanceDate(LocalDate.of(2026, 12, 31));

        service.create(record);

        assertThat(record.getNextMaintenanceDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(getDeviceLastMaintenanceDate(2102)).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    private void insertDevice(Integer id, Integer maintenanceCycle) {
        jdbc.update(
                "INSERT INTO t_device (id, name, parent, lifecycle_status, maintenance_cycle) VALUES (?, ?, ?, ?, ?)",
                id, "device_" + id, 10, "IN_SERVICE", maintenanceCycle);
    }

    private LocalDate getDeviceLastMaintenanceDate(Integer deviceId) {
        return jdbc.queryForObject(
                "SELECT last_maintenance_date FROM t_device WHERE id = ?",
                LocalDate.class, deviceId);
    }

    private MaintenanceRecordEntity newRecord(Integer deviceId, LocalDateTime performedAt) {
        MaintenanceRecordEntity record = new MaintenanceRecordEntity();
        record.setDeviceId(deviceId);
        record.setType("MAINTENANCE");
        record.setTitle("Monthly maintenance");
        record.setPerformerId(1L);
        record.setCreatorId(2L);
        record.setPerformedAt(performedAt);
        return record;
    }
}
