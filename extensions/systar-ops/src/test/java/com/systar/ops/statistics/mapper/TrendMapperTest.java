package com.systar.ops.statistics.mapper;

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
class TrendMapperTest {

    private static final int NONEXISTENT_ID = 99999;
    private static final int SAMPLE_LIMIT   = 1000;

    @Autowired
    private TrendMapper mapper;

    @Test
    void findRawSamples_shouldReturnEmptyForNonexistentMonitor() {
        List<Map<String, Object>> result = mapper.findRawSamples(
                "t_sample_float", NONEXISTENT_ID,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), SAMPLE_LIMIT);
        assertThat(result).isEmpty();
    }

    // Note: findStats() uses t_monitor_stats table which is in a separate stats datasource,
    // not available in the main test datasource. Skipping that test.

    @Test
    void aggregateOHLC_shouldReturnEmptyForNonexistentMonitor() {
        List<Map<String, Object>> result = mapper.aggregateOHLC(
                "t_sample_float", NONEXISTENT_ID,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                "HOUR(moment)");
        assertThat(result).isEmpty();
    }
}
