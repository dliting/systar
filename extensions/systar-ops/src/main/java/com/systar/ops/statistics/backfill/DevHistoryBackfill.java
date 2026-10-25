package com.systar.ops.statistics.backfill;

import com.systar.common.database.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-profile history backfill: seeds 24h of raw samples plus HOUR/DAY/WEEK/
 * MONTH stats history for the four data-bearing simulator probes, so a fresh
 * dev DB shows real trend curves immediately (finest-granularity default view
 * plus non-empty 小时/日/周/月 views) instead of waiting for the simulator
 * and aggregator to accumulate weeks of runtime data.
 * <p>
 * Gated by {@code systar.statistics.dev-history-backfill=true} — set in the
 * dev profile only; the test override disables it so full-context tests keep
 * clean databases. Runs after startup (ApplicationReadyEvent) so spring.sql.init
 * and the aggregator's cold-start catch-up have already completed.
 * <p>
 * Idempotent: the whole pass is skipped once MONTH-granularity history exists
 * (a previous backfill or real aggregation already covers the long tail).
 * Raw rows use H2 {@code MERGE ... KEY (monitor, moment)} like the seed SQL;
 * on MySQL the guard is what prevents duplicate raw samples.
 */
@Component
@ConditionalOnProperty(name = "systar.statistics.dev-history-backfill", havingValue = "true")
public class DevHistoryBackfill {

    private static final Logger log = LoggerFactory.getLogger(DevHistoryBackfill.class);

    // Granularity codes mirror MonitorStatsAggregator (HOUR=1 … MONTH=4).
    private static final int HOUR  = 1;
    private static final int DAY   = 2;
    private static final int WEEK  = 3;
    private static final int MONTH = 4;

    /** Demo probe with the center/amplitude of its deterministic sine wave. */
    private record Probe(int id, double center, double amp) {}

    // The four data-bearing probes from 03-simulator.sql; 1201's band matches
    // the seed's simulator operating range 16.85..29.15C.
    private static final Probe[] PROBES = {
            new Probe(1201, 23.0, 6.15),   // 送风温度 °C
            new Probe(1206, 78.0, 15.0),   // 电池电量 %
            new Probe(1211, 224.0, 8.0),   // L1电压 V
            new Probe(1216, 15.0, 10.0),   // 室外温度 °C
    };

    private static final int RAW_MINUTES   = 24 * 60;  // 24h @ 1min
    private static final int HOUR_DAYS     = 7;
    private static final int DAY_DAYS      = 90;
    private static final int WEEK_WEEKS    = 52;
    private static final int MONTH_MONTHS  = 60;

    private final JdbcTemplate    mainJdbc;
    private final JdbcTemplate    statsJdbc;
    private final DatabaseDialect dialect;

    public DevHistoryBackfill(@Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbc,
                              @Qualifier("statsJdbcTemplate") JdbcTemplate statsJdbc,
                              DatabaseDialect dialect) {
        this.mainJdbc  = mainJdbc;
        this.statsJdbc = statsJdbc;
        this.dialect   = dialect;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            backfill();
        } catch (Exception e) {
            log.warn("Dev history backfill failed (trend views may stay empty): {}", e.getMessage(), e);
        }
    }

    void backfill() {
        if (alreadyBackfilled()) {
            log.debug("Dev history already present; skipping backfill");
            return;
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        int rawRows   = seedRaw(now);
        int statRows  = seedStats(now);
        log.info("Dev history backfill: {} raw samples (24h) + {} stats rows "
                + "(HOUR {}d / DAY {}d / WEEK {}w / MONTH {}y)",
                rawRows, statRows, HOUR_DAYS, DAY_DAYS, WEEK_WEEKS, MONTH_MONTHS);
    }

    private boolean alreadyBackfilled() {
        Long months = statsJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_monitor_stats WHERE granularity = " + MONTH, Long.class);
        return months != null && months >= 12;
    }

    // ======================== raw samples ========================

    private int seedRaw(LocalDateTime now) {
        String sql = "h2".equals(dialect.getDatabaseType())
                ? "MERGE INTO t_sample_float (monitor, \"value\", moment) KEY (monitor, moment)"
                  + " VALUES (?, ?, ?)"
                : "INSERT INTO t_sample_float (monitor, `value`, moment) VALUES (?, ?, ?)";
        LocalDateTime windowStart = now.minusMinutes(RAW_MINUTES);
        List<Object[]> batch = new ArrayList<>(RAW_MINUTES * PROBES.length);
        for (Probe p : PROBES) {
            // Real collected samples already cover the window — seeding
            // synthetic rows on top would interleave two series at the same
            // cadence (zigzag curve, duplicated axis ticks). Seed only probes
            // with no real history in the window.
            Long real = mainJdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_sample_float WHERE monitor = ? AND moment >= ?",
                    Long.class, p.id(), Timestamp.valueOf(windowStart));
            if (real != null && real > 0) continue;
            for (int m = 0; m <= RAW_MINUTES; m++) {
                LocalDateTime t = now.minusMinutes(m);
                batch.add(new Object[]{p.id(), value(p, t), Timestamp.valueOf(t)});
            }
        }
        if (batch.isEmpty()) return 0;
        int[] done = mainJdbc.batchUpdate(sql, batch);
        return done.length;
    }

    // ======================== stats history ========================

    private int seedStats(LocalDateTime now) {
        List<Object[]> batch = new ArrayList<>();
        for (Probe p : PROBES) {
            LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS);
            for (int h = 0; h < HOUR_DAYS * 24; h++) {
                LocalDateTime s = hourStart.minusHours(h);
                batch.add(statsRow(p, s, s.plusHours(1), HOUR, 360, 6));
            }
            LocalDate dayStart = now.toLocalDate();
            for (int d = 0; d < DAY_DAYS; d++) {
                LocalDate s = dayStart.minusDays(d);
                batch.add(statsRow(p, s.atStartOfDay(), s.plusDays(1).atStartOfDay(), DAY, 8640, 24));
            }
            LocalDate monday = now.toLocalDate().with(java.time.DayOfWeek.MONDAY);
            for (int w = 0; w < WEEK_WEEKS; w++) {
                LocalDate s = monday.minusWeeks(w);
                batch.add(statsRow(p, s.atStartOfDay(), s.plusWeeks(1).atStartOfDay(), WEEK, 60480, 7));
            }
            LocalDate monthStart = now.toLocalDate().withDayOfMonth(1);
            for (int mo = 0; mo < MONTH_MONTHS; mo++) {
                LocalDate s = monthStart.minusMonths(mo);
                batch.add(statsRow(p, s.atStartOfDay(), s.plusMonths(1).atStartOfDay(),
                        MONTH, 30 * 8640, (int) s.lengthOfMonth()));
            }
        }
        String sql = buildStatsInsert();
        // Insert-if-absent: buckets the aggregator already computed from real
        // samples win; the backfill only fills the missing long tail.
        if ("h2".equals(dialect.getDatabaseType())) {
            List<Object[]> keyed = new ArrayList<>(batch.size());
            for (Object[] r : batch) {
                Object[] withKey = new Object[r.length + 3];
                System.arraycopy(r, 0, withKey, 0, r.length);
                withKey[r.length]     = r[0];  // monitor
                withKey[r.length + 1] = r[3];  // granularity
                withKey[r.length + 2] = r[1];  // bucket_start
                keyed.add(withKey);
            }
            batch = keyed;
        }
        int[] done = statsJdbc.batchUpdate(sql, batch);
        return done.length;
    }

    /**
     * OHLC + count for one bucket: open/close are the wave at the bucket
     * edges, max/min/avg from {@code steps} samples across the bucket.
     */
    private Object[] statsRow(Probe p, LocalDateTime start, LocalDateTime end,
                              int gran, int sampleCount, int steps) {
        double open  = value(p, start);
        double close = value(p, end.minusNanos(1));
        double sum = 0, max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < steps; i++) {
            LocalDateTime t = start.plusSeconds(
                    (long) (java.time.Duration.between(start, end).getSeconds() * (i + 0.5) / steps));
            double v = value(p, t);
            sum += v;
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        return new Object[]{p.id(), Timestamp.valueOf(start), Timestamp.valueOf(end), gran,
                sum / steps, max, min, sampleCount, open, close};
    }

    /** Insert-if-absent keyed on (monitor, granularity, bucket_start). */
    private String buildStatsInsert() {
        String cols = " (monitor, bucket_start, bucket_end, granularity,"
                + " avg_val, max_val, min_val, sample_count, open_val, close_val, updated_at)";
        if ("h2".equals(dialect.getDatabaseType())) {
            return "INSERT INTO t_monitor_stats" + cols
                    + " SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()"
                    + " WHERE NOT EXISTS (SELECT 1 FROM t_monitor_stats"
                    + " WHERE monitor = ? AND granularity = ? AND bucket_start = ?)";
        }
        return "INSERT INTO t_monitor_stats" + cols
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())"
                + " ON DUPLICATE KEY UPDATE monitor = VALUES(monitor)";
    }

    // ======================== wave ========================

    /**
     * Deterministic demo wave: daily cycle peaking mid-afternoon plus a slow
     * seasonal term, bounded by center ± amp. No RNG — restarts reproduce the
     * same history shape.
     */
    private static double value(Probe p, LocalDateTime t) {
        double hourOfDay = t.getHour() + t.getMinute() / 60.0;
        double daily     = Math.sin(2 * Math.PI * (hourOfDay - 9) / 24.0);
        double seasonal  = Math.sin(2 * Math.PI * t.getDayOfYear() / 365.0);
        return p.center() + p.amp() * (0.7 * daily + 0.3 * seasonal);
    }
}
