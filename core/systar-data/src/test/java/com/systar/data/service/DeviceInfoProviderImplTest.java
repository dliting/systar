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
        insertDevice(7001, 10, (short) 201, "IN_SERVICE", LocalDate.of(2026, 7, 1));

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
        insertDevice(7002, 10, (short) 201, "IN_SERVICE", null);
        insertDevice(7003, 10, (short) 202, "IN_SERVICE", null);
        insertDevice(7004, 11, (short) 201, "RETIRED", null);

        PagedResult<DeviceDto> result = provider.listDevices(null, (short) 201, "IN_SERVICE", 1, 10);

        assertThat(result.records()).extracting(DeviceDto::id).contains(7002).doesNotContain(7003, 7004);
    }

    @Test
    void findWarrantyExpiring_returnsOnlyWithinRange() {
        insertDevice(7005, 10, (short) 201, "IN_SERVICE", LocalDate.now().plusDays(5));
        insertDevice(7006, 10, (short) 201, "IN_SERVICE", LocalDate.now().plusDays(40));

        List<DeviceDto> devices = provider.findWarrantyExpiring(LocalDate.now().plusDays(30));

        assertThat(devices).extracting(DeviceDto::id).contains(7005).doesNotContain(7006);
    }

    @Test
    void resolveSpaceId_returnsNullForNullDeviceId() {
        assertThat(provider.resolveSpaceId(null)).isNull();
    }

    @Test
    void resolveSpaceId_returnsNullForNonexistentDevice() {
        assertThat(provider.resolveSpaceId(9999)).isNull();
    }

    @Test
    void resolveSpaceId_resolvesWhenParentIsSpace() {
        jdbc.update(
                "INSERT INTO t_space (id, name, parent, sequence) VALUES (?, ?, ?, ?)",
                601, "test_space", 0, 1);

        insertDevice(7010, 601, (short) 201, "IN_SERVICE", null);

        assertThat(provider.resolveSpaceId(7010)).isEqualTo(601);
    }

    private void insertDevice(Integer id, Integer parentId, Short catalog, String lifecycleStatus,
                              LocalDate warrantyDate) {
        jdbc.update(
                "INSERT INTO t_device (id, name, parent, catalog, lifecycle_status, warranty_date) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "device_" + id, parentId, catalog, lifecycleStatus, warrantyDate);
    }
}
