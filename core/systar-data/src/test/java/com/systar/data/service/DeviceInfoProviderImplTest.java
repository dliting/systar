package com.systar.data.service;

import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;
import com.systar.data.test.DataTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DeviceInfoProviderImplTest {

    @Autowired
    private DeviceInfoProviderImpl provider;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void getById_returnsDtoWhenDeviceExists() {
        insertDevice(7001, (short) 201, "IN_SERVICE", LocalDate.of(2026, 7, 1));

        DeviceDto dto = provider.getById(7001);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(7001);
        assertThat(dto.name()).isEqualTo("device_7001");
        assertThat(dto.lifecycleStatus()).isEqualTo("IN_SERVICE");
        assertThat(dto.warrantyDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void getById_returnsNullWhenNotFound() {
        assertThat(provider.getById(9999)).isNull();
    }

    @Test
    void listDevices_returnsFilteredPagedResult() {
        insertDevice(7002, (short) 201, "IN_SERVICE", null);
        insertDevice(7003, (short) 202, "IN_SERVICE", null);
        insertDevice(7004, (short) 201, "RETIRED", null);

        PagedResult<DeviceDto> result = provider.listDevices((short) 201, "IN_SERVICE", 1, 10);

        assertThat(result.records()).extracting(DeviceDto::id).contains(7002).doesNotContain(7003, 7004);
    }

    @Test
    void findWarrantyExpiring_returnsOnlyWithinRange() {
        insertDevice(7005, (short) 201, "IN_SERVICE", LocalDate.now().plusDays(5));
        insertDevice(7006, (short) 201, "IN_SERVICE", LocalDate.now().plusDays(40));

        List<DeviceDto> devices = provider.findWarrantyExpiring(LocalDate.now().plusDays(30));

        assertThat(devices).extracting(DeviceDto::id).contains(7005).doesNotContain(7006);
    }

    @Test
    void countByLifecycleStatus_groupsCountsIncludingNullStatus() {
        insertDevice(7101, (short) 201, "IN_SERVICE", null);
        insertDevice(7102, (short) 202, "IN_SERVICE", null);
        insertDevice(7103, (short) 201, "UNDER_REPAIR", null);
        insertDevice(7104, (short) 201, "RETIRED", null);
        insertDevice(7105, (short) 201, "IN_STORAGE", null);
        insertDevice(7106, (short) 201, null, null);

        Map<String, Long> counts = provider.countByLifecycleStatus();

        assertThat(counts).hasSize(5);
        assertThat(counts.get("IN_SERVICE")).isEqualTo(2L);
        assertThat(counts.get("UNDER_REPAIR")).isEqualTo(1L);
        assertThat(counts.get("RETIRED")).isEqualTo(1L);
        assertThat(counts.get("IN_STORAGE")).isEqualTo(1L);
        assertThat(counts.get(null)).isEqualTo(1L);
    }

    private void insertDevice(Integer id, Short catalog, String lifecycleStatus,
                              LocalDate warrantyDate) {
        jdbc.update(
                "INSERT INTO t_device (id, name, catalog, lifecycle_status, warranty_date) "
                + "VALUES (?, ?, ?, ?, ?)",
                id, "device_" + id, catalog, lifecycleStatus, warrantyDate);
    }
}
