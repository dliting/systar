package com.systar.ops.statistics.service;

import com.systar.common.database.DatabaseDialect;
import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.type.DataType;
import com.systar.ops.statistics.mapper.TrendBucketExpr;
import com.systar.ops.statistics.mapper.TrendMapper;
import com.systar.ops.statistics.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrendService {

    private static final Logger log = LoggerFactory.getLogger(TrendService.class);

    private static final int DEFAULT_WINDOW_MULTIPLIER = 60;
    private static final int MAX_INTRADAY_POINTS = 5000;

    /** Response render modes (B3): raw samples / downsampled buckets / candlesticks. */
    public static final String MODE_RAW = "RAW";
    public static final String MODE_BUCKETED = "BUCKETED";
    public static final String MODE_OHLC = "OHLC";

    // AUTO ladder thresholds (D6): keep bucket counts readable — at most
    // ~336 hourly, 120 daily, ~52 weekly, or a handful of monthly candles
    private static final long ONE_DAY_SECONDS = 86_400L;
    private static final long FOURTEEN_DAYS_SECONDS = 1_209_600L;
    private static final long ONE_TWENTY_DAYS_SECONDS = 10_368_000L;
    private static final long ONE_YEAR_SECONDS = 31_536_000L;

    private final TrendMapper   trendMapper;
    private final DatabaseDialect databaseDialect;
    private final JdbcTemplate  jdbc;
    private final JdbcTemplate  statsJdbc;

    @Value("${systar.statistics.weekStartDay:MONDAY}")
    private String weekStartDay;

    public TrendService(TrendMapper trendMapper,
                        DatabaseDialect databaseDialect,
                        @Qualifier("mainJdbcTemplate") JdbcTemplate jdbc,
                        @Autowired(required = false)
                        @Qualifier("statsJdbcTemplate") JdbcTemplate statsJdbc) {
        this.trendMapper      = trendMapper;
        this.databaseDialect  = databaseDialect;
        this.jdbc             = jdbc;
        this.statsJdbc        = statsJdbc != null ? statsJdbc : jdbc;
    }

    private boolean isWeekStartMonday() {
        return !"SUNDAY".equalsIgnoreCase(weekStartDay);
    }

    // ==================== Public API ====================

    public TrendResponseVO getTrendData(TrendQuery query) {
        // D6: AUTO (null) resolves by span ladder; explicit requests are
        // clamped down until the bucket fits the window. The effective value
        // is echoed back on every response path.
        long spanSeconds = ChronoUnit.SECONDS.between(query.startTime(), query.endTime());
        String gran = resolveGranularity(query.granularity(), spanSeconds);

        if ("INTRADAY".equals(gran)) {
            String tableName = resolveSampleTableName(query.monitorKind(), query.monitorId());
            if (tableName == null) return emptyResponse("INTRADAY");
            return handleIntraday(query, tableName);
        }
        return handleAggregated(query, granularityCode(gran), gran);
    }

    public TrendResponseVO getDefaultView(int monitorId, String monitorKind) {
        long detectIntervalSeconds = resolveDetectIntervalSeconds(monitorId, monitorKind);
        long defaultWindowSeconds = detectIntervalSeconds * DEFAULT_WINDOW_MULTIPLIER;
        String granularity = determineGranularity(defaultWindowSeconds);

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusSeconds(defaultWindowSeconds);

        TrendQuery query = new TrendQuery(monitorId, monitorKind, startTime, endTime, granularity);
        return getTrendData(query);
    }

    public Map<String, Object> getMetadata(int monitorId, String monitorKind) {
        if ("PROBE".equals(monitorKind)) {
            Map<String, Object> probe = queryProbeById(monitorId);
            if (probe == null) return Collections.emptyMap();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", probe.get("name"));
            meta.put("caption", probe.get("caption"));
            meta.put("unit", probe.get("unit"));
            DataType dt = probe.get("dataType") instanceof DataType d ? d : null;
            meta.put("dataType", dt != null ? dt.name() : null);
            meta.put("detectInterval", probe.get("detectInterval"));
            meta.put("minValue", probe.get("minValue"));
            meta.put("maxValue", probe.get("maxValue"));
            meta.put("warnCond", probe.get("warnCond"));
            return meta;
        } else if ("CONTROL".equals(monitorKind)) {
            Map<String, Object> control = queryControlById(monitorId);
            if (control == null) return Collections.emptyMap();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", control.get("name"));
            meta.put("caption", control.get("caption"));
            meta.put("unit", control.get("unit"));
            meta.put("dataType", null);
            meta.put("detectInterval", control.get("detectInterval"));
            meta.put("minValue", control.get("minValue"));
            meta.put("maxValue", control.get("maxValue"));
            meta.put("warnCond", control.get("warnCond"));
            return meta;
        }
        return Collections.emptyMap();
    }

    // ==================== INTRADAY ====================

    private TrendResponseVO handleIntraday(TrendQuery query, String tableName) {
        // D5: above the raw cap, downsample on the fly into ~1200 fixed-width
        // buckets (OHLC per bucket) instead of silently truncating the series.
        long span = ChronoUnit.SECONDS.between(query.startTime(), query.endTime());
        Long count = null;
        try {
            count = trendMapper.countSamples(
                    tableName, query.monitorId(), query.startTime(), query.endTime());
        } catch (DataAccessException e) {
            log.warn("trend sample COUNT failed for monitor {} ({}); falling back to RAW truncation",
                    query.monitorId(), e.getMessage());
        }

        List<TrendPointVO> points;
        String mode;
        Long bucketSeconds;
        if (count != null && count > MAX_INTRADAY_POINTS && span > 0) {
            long n = (long) Math.ceil(span / 1200.0);
            String bucketExpr = TrendBucketExpr.getEpochBucketExpr(n, databaseDialect.getDatabaseType());
            points = trendMapper.aggregateOHLC(
                            tableName, query.monitorId(), query.startTime(), query.endTime(), bucketExpr)
                    .stream()
                    .map(r -> mapToTrendPoint(r, "bucket"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            mode = MODE_BUCKETED;
            bucketSeconds = n;
        } else {
            points = trendMapper.findRawSamples(
                            tableName, query.monitorId(), query.startTime(), query.endTime(), MAX_INTRADAY_POINTS)
                    .stream()
                    .map(this::mapToRawPoint)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            mode = MODE_RAW;
            bucketSeconds = null;
        }

        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("INTRADAY");
        response.setMode(mode);
        response.setBucketSeconds(bucketSeconds);
        response.setPoints(points);
        response.setSummary(buildSummary(points));
        return response;
    }

    // ==================== Aggregated (HOUR / DAY / WEEK / MONTH) ====================

    private TrendResponseVO handleAggregated(TrendQuery query, int granCode, String granularity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveEnd = query.endTime().isAfter(now) ? now : query.endTime();

        String columns = statsHasOpenClose()
                ? "bucket_start, bucket_end, avg_val, max_val, min_val, open_val, close_val, sample_count"
                : "bucket_start, bucket_end, avg_val, max_val, min_val, sample_count";
        List<Map<String, Object>> statsRows = statsJdbc.queryForList(
                "SELECT " + columns + " "
                + "FROM t_monitor_stats "
                + "WHERE monitor = ? AND granularity = ? AND bucket_start BETWEEN ? AND ? "
                + "ORDER BY bucket_start",
                query.monitorId(), granCode, query.startTime(), effectiveEnd);

        List<Map<String, Object>> onTheFlyRows = computeCurrentPeriodOTF(
                query, granCode, effectiveEnd);

        List<TrendPointVO> points = mergeStats(statsRows, onTheFlyRows);

        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity(granularity);
        response.setMode(MODE_OHLC);
        response.setBucketSeconds(bucketSecondsFor(granularity));
        response.setPoints(points);
        response.setSummary(buildSummary(points));
        return response;
    }

    /**
     * B1 guard contract: when the startup ALTER for open_val/close_val fails,
     * the guard WARNs "trend open/close degrade to NULL" — the query side must
     * honor that (null open/close on every point) rather than failing each
     * aggregated request on an unknown column. Probed once per JVM with the
     * guard's zero-row SELECT technique (dialect-agnostic); the volatile write
     * is a benign race — a duplicate probe is idempotent.
     */
    private volatile Boolean statsHasOpenClose;

    private boolean statsHasOpenClose() {
        Boolean known = statsHasOpenClose;
        if (known != null) return known;
        boolean ok;
        try {
            statsJdbc.query("SELECT open_val, close_val FROM t_monitor_stats WHERE 1 = 0", rs -> { });
            ok = true;
        } catch (DataAccessException e) {
            log.warn("t_monitor_stats lacks open_val/close_val ({}); trend open/close degrade to NULL",
                    e.getMessage());
            ok = false;
        }
        statsHasOpenClose = ok;
        return ok;
    }

    private List<Map<String, Object>> computeCurrentPeriodOTF(TrendQuery query, int granCode,
                                                               LocalDateTime effectiveEnd) {
        LocalDateTime now = LocalDateTime.now();
        String granKey = codeToGranularity(granCode);
        LocalDateTime currentPeriodStart = truncateToGranularity(now, granKey);

        if (currentPeriodStart.isBefore(query.startTime())
                || !currentPeriodStart.isBefore(effectiveEnd)) {
            return Collections.emptyList();
        }

        String tableName = resolveSampleTableName(query.monitorKind(), query.monitorId());
        if (tableName == null) return Collections.emptyList();

        String bucketExpr = TrendBucketExpr.getBucketExpr(
                granKey, databaseDialect.getDatabaseType(), isWeekStartMonday());

        return trendMapper.aggregateOHLC(
                tableName, query.monitorId(), currentPeriodStart, effectiveEnd, bucketExpr);
    }

    // ==================== Aggregation (package-private for testing) ====================

    /**
     * Groups bars by time bucket (day/week/month) and computes aggregate avg/max/min.
     * For a single-bar group, returns the original bar unchanged.
     * For multi-bar groups:
     *   avg = mean of avg values
     *   max = max of max values
     *   min = min of min values
     *   sampleCount = sum of sampleCounts
     *   time = truncated to bucket start
     */
    List<TrendBarVO> aggregateToCoarser(List<TrendBarVO> bars, String granularity) {
        if (bars == null || bars.isEmpty()) {
            return Collections.emptyList();
        }

        Map<LocalDateTime, List<TrendBarVO>> grouped = bars.stream()
                .collect(Collectors.groupingBy(
                        bar -> truncateToGranularity(bar.time(), granularity),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<TrendBarVO> group = entry.getValue();
                    if (group.size() == 1) {
                        TrendBarVO single = group.get(0);
                        return new TrendBarVO(entry.getKey(), single.avg(), single.max(), single.min(), single.sampleCount());
                    }
                    double avg = group.stream().mapToDouble(TrendBarVO::avg).average().orElse(0);
                    double max = group.stream().mapToDouble(TrendBarVO::max).max().orElse(0);
                    double min = group.stream().mapToDouble(TrendBarVO::min).min().orElse(0);
                    long sampleCount = group.stream().mapToLong(TrendBarVO::sampleCount).sum();
                    return new TrendBarVO(entry.getKey(), round2(avg), round2(max), round2(min), sampleCount);
                })
                .collect(Collectors.toList());
    }

    // ==================== Summary ====================

    TrendSummaryVO buildSummary(List<TrendPointVO> points) {
        if (points == null || points.isEmpty()) {
            return new TrendSummaryVO(0, 0, 0, 0);
        }
        Double current = null;
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).avg() != null) {
                current = points.get(i).avg();
                break;
            }
        }
        double base = current != null ? current : 0.0;
        double periodMax = points.stream().map(TrendPointVO::max)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(base);
        double periodMin = points.stream().map(TrendPointVO::min)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).min().orElse(base);
        long totalSamples = points.stream().mapToLong(TrendPointVO::count).sum();
        return new TrendSummaryVO(
                round2(base), round2(periodMax), round2(periodMin), totalSamples);
    }

    // ==================== Merge ====================

    private List<TrendPointVO> mergeStats(List<Map<String, Object>> statsRows,
                                          List<Map<String, Object>> onTheFlyRows) {
        List<TrendPointVO> points = statsRows.stream()
                .map(row -> mapToTrendPoint(row, "bucket_start"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        List<TrendPointVO> otfPoints = onTheFlyRows.stream()
                .map(row -> mapToTrendPoint(row, "bucket"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<LocalDateTime> otfTimes = otfPoints.stream()
                .map(TrendPointVO::t)
                .collect(Collectors.toSet());
        points.removeIf(p -> otfTimes.contains(p.t()));

        points.addAll(otfPoints);
        points.sort(Comparator.comparing(TrendPointVO::t));
        return points;
    }

    // ==================== Granularity Helpers ====================

    static int granularityCode(String granularity) {
        return switch (granularity) {
            case "HOUR"  -> 1;
            case "DAY"   -> 2;
            case "WEEK"  -> 3;
            case "MONTH" -> 4;
            default -> throw new IllegalArgumentException("Unsupported granularity: " + granularity);
        };
    }

    static String codeToGranularity(int code) {
        return switch (code) {
            case 1 -> "HOUR";
            case 2 -> "DAY";
            case 3 -> "WEEK";
            case 4 -> "MONTH";
            default -> throw new IllegalArgumentException("Unknown granularity code: " + code);
        };
    }

    static String determineGranularity(long windowSeconds) {
        if (windowSeconds <= ONE_DAY_SECONDS) return "INTRADAY";
        if (windowSeconds <= FOURTEEN_DAYS_SECONDS) return "HOUR";
        if (windowSeconds <= ONE_TWENTY_DAYS_SECONDS) return "DAY";
        if (windowSeconds <= ONE_YEAR_SECONDS) return "WEEK";
        return "MONTH";
    }

    /**
     * D6 granularity resolution. null = AUTO ladder. An explicit request is
     * clamped DOWN the ladder MONTH→WEEK→DAY→HOUR→INTRADAY until its bucket
     * fits the span strictly more than once — a single-bucket candle carries
     * no trend. INTRADAY is always feasible (BUCKETED downsamples, Task 5).
     */
    static String resolveGranularity(String explicit, long spanSeconds) {
        if (explicit == null) return determineGranularity(spanSeconds);
        if ("INTRADAY".equals(explicit)) return "INTRADAY";
        String[] ladder = {"MONTH", "WEEK", "DAY", "HOUR"};
        int start = Arrays.asList(ladder).indexOf(explicit);
        for (int i = Math.max(0, start); i < ladder.length; i++) {
            if (bucketSecondsFor(ladder[i]) < spanSeconds) return ladder[i];
        }
        return "INTRADAY";
    }

    static long bucketSecondsFor(String granularity) {
        return switch (granularity) {
            case "HOUR"  -> 3_600L;
            case "DAY"   -> 86_400L;
            case "WEEK"  -> 604_800L;
            case "MONTH" -> 2_592_000L;
            default -> 0L;
        };
    }

    private static LocalDateTime truncateToGranularity(LocalDateTime time, String granularity) {
        return switch (granularity) {
            case "HOUR"  -> time.withMinute(0).withSecond(0).withNano(0);
            case "DAY"   -> time.toLocalDate().atStartOfDay();
            case "WEEK"  -> time.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay();
            case "MONTH" -> time.toLocalDate().withDayOfMonth(1).atStartOfDay();
            default -> time;
        };
    }

    // ==================== Mapping ====================

    /**
     * Shared stats-row / on-the-fly OHLC row mapper (B3). open_val/close_val
     * are nullable in the stats table (legacy rows predate the columns, D3)
     * and must serialize as JSON null, not 0 — hence boxed Double.
     */
    private TrendPointVO mapToTrendPoint(Map<String, Object> row, String timeKey) {
        LocalDateTime time = toLocalDateTime(StatisticsService.getValue(row, timeKey));
        if (time == null) return null;
        Double avg = round2OrNull(StatisticsService.getValue(row, "avg_val"));
        Double max = round2OrNull(StatisticsService.getValue(row, "max_val"));
        Double min = round2OrNull(StatisticsService.getValue(row, "min_val"));
        Double open = round2OrNull(StatisticsService.getValue(row, "open_val"));
        Double close = round2OrNull(StatisticsService.getValue(row, "close_val"));
        long sampleCount = toLong(StatisticsService.getValue(row, "sample_count"));
        return new TrendPointVO(time, avg, max, min, open, close, sampleCount);
    }

    /** RAW mode: each sample becomes a degenerate candle (open=close=avg=max=min). */
    private TrendPointVO mapToRawPoint(Map<String, Object> row) {
        LocalDateTime time = toLocalDateTime(StatisticsService.getValue(row, "moment"));
        if (time == null) return null;
        double value = toDouble(StatisticsService.getValue(row, "value"));
        return new TrendPointVO(time, value, value, value, value, value, 1L);
    }

    // ==================== Helpers ====================

    String resolveSampleTableName(String monitorKind, int monitorId) {
        DataType dataType = resolveDataType(monitorKind, monitorId);
        return dataTypeToTableName(dataType);
    }

    long resolveDetectIntervalSeconds(int monitorId, String monitorKind) {
        String intervalStr = null;
        if ("PROBE".equals(monitorKind)) {
            Map<String, Object> probe = queryProbeById(monitorId);
            if (probe != null) intervalStr = (String) probe.get("detectInterval");
        } else if ("CONTROL".equals(monitorKind)) {
            Map<String, Object> control = queryControlById(monitorId);
            if (control != null) intervalStr = (String) control.get("detectInterval");
        }
        if (intervalStr == null || intervalStr.isBlank()) return 10L;
        try {
            return TimeSpan.parse(intervalStr.trim()).toSeconds();
        } catch (IllegalArgumentException e) {
            return parseHHmmss(intervalStr.trim());
        }
    }

    private DataType resolveDataType(String monitorKind, int monitorId) {
        if ("PROBE".equals(monitorKind)) {
            Map<String, Object> probe = queryProbeById(monitorId);
            if (probe != null) {
                DataType dt = probe.get("dataType") instanceof DataType d ? d : null;
                if (dt != null) return dt;
            }
        }
        return DataType.FLOAT;
    }

    private static String dataTypeToTableName(DataType dataType) {
        return switch (dataType) {
            case FLOAT -> "t_sample_float";
            case INT -> "t_sample_int";
            case BOOLEAN -> "t_sample_boolean";
            case STRING, TIMESPAN -> null;
        };
    }

    public static double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof Boolean bool) return bool ? 1.0 : 0.0;
        if (value instanceof BigDecimal bd) return bd.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number num) return num.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** SQL NULL stays null (boxed) so JSON serializes it as null, not 0. */
    private static Double round2OrNull(Object value) {
        return value == null ? null : round2(toDouble(value));
    }

    private static LocalDateTime toLocalDateTime(Object val) {
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        if (val instanceof java.util.Date d)
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        if (val instanceof LocalDate ld) return ld.atStartOfDay();
        if (val instanceof String s) {
            try { return LocalDateTime.parse(s.replace(' ', 'T')); }
            catch (Exception e) {
                try { return LocalDate.parse(s).atStartOfDay(); }
                catch (Exception e2) { return null; }
            }
        }
        return null;
    }

    private static long parseHHmmss(String text) {
        String[] parts = text.split(":");
        if (parts.length == 3) {
            try {
                return Long.parseLong(parts[0]) * 3600
                     + Long.parseLong(parts[1]) * 60
                     + Long.parseLong(parts[2]);
            } catch (NumberFormatException ignored) {}
        }
        return 10L;
    }

    private static TrendResponseVO emptyResponse(String granularity) {
        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity(granularity);
        boolean intraday = "INTRADAY".equals(granularity);
        response.setMode(intraday ? MODE_RAW : MODE_OHLC);
        response.setBucketSeconds(intraday ? null : bucketSecondsFor(granularity));
        response.setPoints(Collections.emptyList());
        response.setSummary(new TrendSummaryVO(0, 0, 0, 0));
        return response;
    }

    // ==================== JdbcTemplate Queries ====================

    private Map<String, Object> queryProbeById(int id) {
        // IMPORTANT: queryProbeById is shared by getMetadata, resolveDetectIntervalSeconds,
        // and resolveDataType. Add new columns here but always fetch by column name —
        // never by index — so reordering columns in this SELECT does not break other readers.
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT name, caption, unit, monitor_kind, time_interval, min_value, max_value, warn_cond FROM t_probe WHERE id = ?",
                (rs, i) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", rs.getString("name"));
                    map.put("caption", rs.getString("caption"));
                    map.put("unit", rs.getString("unit"));
                    Number mk = (Number) rs.getObject("monitor_kind");
                    if (mk != null) {
                        int ordinal = mk.intValue();
                        DataType[] values = DataType.values();
                        map.put("dataType", ordinal >= 0 && ordinal < values.length
                                ? values[ordinal] : null);
                    }
                    map.put("detectInterval", rs.getString("time_interval"));
                    // getObject returns null for SQL NULL — explicit null is desired over 0
                    // so frontend can distinguish "no threshold configured" from "threshold is 0".
                    map.put("minValue", rs.getObject("min_value"));
                    map.put("maxValue", rs.getObject("max_value"));
                    map.put("warnCond", rs.getString("warn_cond"));
                    return map;
                }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> queryControlById(int id) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT name, caption, unit, time_interval, min_value, max_value, warn_cond FROM t_control WHERE id = ?",
                (rs, i) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", rs.getString("name"));
                    map.put("caption", rs.getString("caption"));
                    map.put("unit", rs.getString("unit"));
                    map.put("detectInterval", rs.getString("time_interval"));
                    map.put("minValue", rs.getObject("min_value"));
                    map.put("maxValue", rs.getObject("max_value"));
                    map.put("warnCond", rs.getString("warn_cond"));
                    return map;
                }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
