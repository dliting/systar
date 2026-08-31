package com.systar.ops.analysis.mapper;

import com.systar.ops.test.OpsTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private AnalysisMapper mapper;

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
