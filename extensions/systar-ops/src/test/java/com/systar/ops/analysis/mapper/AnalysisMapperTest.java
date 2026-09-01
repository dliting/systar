package com.systar.ops.analysis.mapper;

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
class AnalysisMapperTest {

    private static final int NONEXISTENT_ID = 99999;

    private static final int DEVICE_ID       = 9101;
    private static final int DEVICE_ASSET_ID = 9101;
    private static final int PROBE_ASSET_ID  = 9102;
    private static final int PROBE_ID        = 9201;

    @Autowired
    private AnalysisMapper mapper;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Test
    void getFloatHistory_shouldReturnEmptyForNonexistentMonitor() {
        List<Map<String, Object>> result = mapper.getFloatHistory(
                NONEXISTENT_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(result).isEmpty();
    }

    @Test
    void getIntHistory_shouldReturnEmptyForNonexistentMonitor() {
        List<Map<String, Object>> result = mapper.getIntHistory(
                NONEXISTENT_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(result).isEmpty();
    }

    @Test
    void listActiveDevices_shouldNotThrow() {
        List<Map<String, Object>> result = mapper.listActiveDevices();
        assertThat(result).isNotNull();
    }

    @Test
    void countAlarmsForDevice_shouldReturnZeroForNonexistentDevice() {
        long count = mapper.countAlarmsForDevice(
                NONEXISTENT_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countAlarmsForDevice_shouldBridgeMonitorIdDomain() {
        // t_error_message_log.asset_id stores the MONITOR id (t_probe.id), not
        // the t_asset id — health scoring must bridge via t_asset.probe_id.
        // Regression guard: alarm counts per device were always zero.
        jdbcTemplate.update(
                "INSERT INTO t_device (id, name, caption, parent, lifecycle_status) "
                        + "VALUES (?, 'analysis_dev', '分析回归设备', 0, 'IN_SERVICE')", DEVICE_ID);
        jdbcTemplate.update(
                "INSERT INTO t_asset (id, name, kind, device_id) VALUES (?, 'analysis_dev_asset', 1, ?)",
                DEVICE_ASSET_ID, DEVICE_ID);
        jdbcTemplate.update(
                "INSERT INTO t_asset (id, name, kind, parent_id, probe_id) VALUES (?, 'analysis_probe_asset', 3, ?, ?)",
                PROBE_ASSET_ID, DEVICE_ASSET_ID, PROBE_ID);
        LocalDateTime alarmTime = LocalDate.now().minusDays(1).atTime(12, 0);
        jdbcTemplate.update(
                "INSERT INTO t_error_message_log "
                        + "(asset_id, monitor_name, error_message, \"value\", state, warn_id, time) "
                        + "VALUES (?, 'L1电压', 'PDU L1电压偏低', '199', 2, 3, ?)", PROBE_ID, alarmTime);
        jdbcTemplate.update(
                "INSERT INTO t_alarm_message "
                        + "(log_id, caption, state, auto, alarm_time, recovered, warn_id, device_id) "
                        + "VALUES ((SELECT MAX(id) FROM t_error_message_log), 'PDU L1电压偏低告警', "
                        + "2, 1, ?, 1, 3, ?)", alarmTime, DEVICE_ID);

        long count = mapper.countAlarmsForDevice(
                DEVICE_ID, LocalDateTime.now().minusDays(3), LocalDateTime.now());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countMaintenanceForDevice_shouldReturnZeroForNonexistentDevice() {
        long count = mapper.countMaintenanceForDevice(
                NONEXISTENT_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void findProbeIdsForDevice_shouldReturnEmptyForNonexistentDevice() {
        List<Integer> ids = mapper.findProbeIdsForDevice(NONEXISTENT_ID);
        assertThat(ids).isEmpty();
    }
}
