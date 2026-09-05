package com.systar.server.statistics;

import com.systar.ops.statistics.model.AlarmStatsVO;
import com.systar.ops.statistics.model.StatisticsQuery;
import com.systar.ops.statistics.service.StatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo alarm history seeds (03-simulator.sql, both dialects) must support the
 * statistics alarm-overview view: severity levels spread over three ranks,
 * alarms on every recent day (no single-day spike), resolvable top devices,
 * and a seeded PREVIOUS period so the period-comparison chart shows both bars.
 * Bounds are minimums — runtime alarms only ever add rows.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("Alarm seed story for statistics")
class AlarmSeedStatsTest {

    private static final long SEVEN_DAYS = 7;

    @Autowired
    private StatisticsService statisticsService;

    @Test
    @DisplayName("current period: three severity levels, daily spread, top devices")
    void currentPeriodSeedStory() {
        AlarmStatsVO vo = statisticsService.getAlarmStats(lastSevenDays());

        assertThat(vo.getByLevel()).containsKeys(2, 3, 4);
        assertThat(vo.getTrend()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(vo.getPendingAlarms()).isGreaterThanOrEqualTo(2);
        assertThat(vo.getTopDevices()).isNotEmpty();
        assertThat(vo.getTopDevices().get(0).deviceName()).isNotBlank();
    }

    @Test
    @DisplayName("previous period is seeded so the comparison chart has both bars")
    void previousPeriodSeeded() {
        AlarmStatsVO vo = statisticsService.getAlarmStats(lastSevenDays());

        assertThat(vo.getPrevPeriodCount()).isGreaterThanOrEqualTo(10);
    }

    private StatisticsQuery lastSevenDays() {
        LocalDate end   = LocalDate.now();
        LocalDate start = end.minusDays(SEVEN_DAYS);
        return new StatisticsQuery(start, end, null, null, "DAY");
    }
}
