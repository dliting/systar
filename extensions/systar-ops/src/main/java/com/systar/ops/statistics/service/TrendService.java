package com.systar.ops.statistics.service;

import com.systar.common.database.DatabaseDialect;
import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.type.DataType;
import com.systar.ops.statistics.mapper.TrendBucketExpr;
import com.systar.ops.statistics.mapper.TrendMapper;
import com.systar.ops.statistics.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrendService {

    private static final int DEFAULT_WINDOW_MULTIPLIER = 60;
    private static final int MAX_INTRADAY_POINTS = 5000;
    private static final long ONE_DAY_SECONDS = 86_400L;
    private static final long THIRTY_ONE_DAYS_SECONDS = 2_678_400L;
    private static final long ONE_EIGHTY_DAYS_SECONDS = 15_552_000L;
    private static final long TWO_YEARS_SECONDS = 63_072_000L;

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
        if (query.granularity().equals("INTRADAY")) {
            String tableName = resolveSampleTableName(query.monitorKind(), query.monitorId());
            if (tableName == null) return emptyResponse("INTRADAY");
            return handleIntraday(query, tableName);
        }
        int granCode = granularityCode(query.granularity());
        return handleAggregated(query, granCode);
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
        List<Map<String, Object>> rows = trendMapper.findRawSamples(
                tableName, query.monitorId(), query.startTime(), query.endTime(), MAX_INTRADAY_POINTS);

        List<IntradayPointVO> points = rows.stream()
                .map(this::mapToIntradayPoint)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity("INTRADAY");
        response.setIntradayPoints(points);
        response.setDataPoints(Collections.emptyList());
        response.setAvg5(Collections.emptyList());
        response.setAvg10(Collections.emptyList());
        response.setAvg20(Collections.emptyList());
        response.setSummary(new TrendSummaryVO(
                points.isEmpty() ? 0 : points.get(points.size() - 1).value(),
                points.stream().mapToDouble(IntradayPointVO::value).max().orElse(0),
                points.stream().mapToDouble(IntradayPointVO::value).min().orElse(0),
                points.size()));
        return response;
    }

    // ==================== Aggregated (HOUR / DAY / WEEK / MONTH) ====================

    private TrendResponseVO handleAggregated(TrendQuery query, int granCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveEnd = query.endTime().isAfter(now) ? now : query.endTime();

        List<Map<String, Object>> statsRows = statsJdbc.queryForList(
                "SELECT bucket_start, bucket_end, avg_val, max_val, min_val, sample_count "
                + "FROM t_monitor_stats "
                + "WHERE monitor = ? AND granularity = ? AND bucket_start BETWEEN ? AND ? "
                + "ORDER BY bucket_start",
                query.monitorId(), granCode, query.startTime(), effectiveEnd);

        List<Map<String, Object>> onTheFlyRows = computeCurrentPeriodOTF(
                query, granCode, effectiveEnd);

        List<TrendBarVO> bars = mergeStats(statsRows, onTheFlyRows);

        List<Double> avg5 = computeMA(bars, 5);
        List<Double> avg10 = computeMA(bars, 10);
        List<Double> avg20 = computeMA(bars, 20);

        TrendResponseVO response = new TrendResponseVO();
        response.setGranularity(query.granularity());
        response.setDataPoints(bars);
        response.setAvg5(avg5);
        response.setAvg10(avg10);
        response.setAvg20(avg20);
        response.setSummary(buildSummary(bars));
        return response;
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

    // ==================== Moving Averages ====================

    List<Double> computeMA(List<TrendBarVO> bars, int period) {
        List<Double> result = new ArrayList<>(bars.size());
        double sum = 0;
        for (int i = 0; i < bars.size(); i++) {
            sum += bars.get(i).avg();
            if (i >= period) {
                sum -= bars.get(i - period).avg();
            }
            if (i >= period - 1) {
                result.add(Math.round(sum / period * 100.0) / 100.0);
            } else {
                result.add(null);
            }
        }
        return result;
    }

    // ==================== Summary ====================

    TrendSummaryVO buildSummary(List<TrendBarVO> bars) {
        if (bars == null || bars.isEmpty()) {
            return new TrendSummaryVO(0, 0, 0, 0);
        }
        double currentValue = bars.get(bars.size() - 1).avg();
        double periodMax = bars.stream().mapToDouble(TrendBarVO::max).max().orElse(currentValue);
        double periodMin = bars.stream().mapToDouble(TrendBarVO::min).min().orElse(currentValue);
        long totalSamples = bars.stream().mapToLong(TrendBarVO::sampleCount).sum();
        return new TrendSummaryVO(
                round2(currentValue), round2(periodMax), round2(periodMin), totalSamples);
    }

    // ==================== Merge ====================

    private List<TrendBarVO> mergeStats(List<Map<String, Object>> statsRows,
                                         List<Map<String, Object>> onTheFlyRows) {
        List<TrendBarVO> bars = statsRows.stream()
                .map(row -> mapToTrendBar(row, "bucket_start"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        List<TrendBarVO> otfBars = onTheFlyRows.stream()
                .map(row -> mapToTrendBar(row, "bucket"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<LocalDateTime> otfTimes = otfBars.stream()
                .map(TrendBarVO::time)
                .collect(Collectors.toSet());
        bars.removeIf(bar -> otfTimes.contains(bar.time()));

        bars.addAll(otfBars);
        bars.sort(Comparator.comparing(TrendBarVO::time));
        return bars;
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

    String determineGranularity(long windowSeconds) {
        if (windowSeconds <= ONE_DAY_SECONDS) return "INTRADAY";
        if (windowSeconds <= THIRTY_ONE_DAYS_SECONDS) return "HOUR";
        if (windowSeconds <= ONE_EIGHTY_DAYS_SECONDS) return "DAY";
        if (windowSeconds <= TWO_YEARS_SECONDS) return "WEEK";
        return "MONTH";
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

    private TrendBarVO mapToTrendBar(Map<String, Object> row, String timeKey) {
        LocalDateTime time = toLocalDateTime(StatisticsService.getValue(row, timeKey));
        double avgVal = toDouble(StatisticsService.getValue(row, "avg_val"));
        double maxVal = toDouble(StatisticsService.getValue(row, "max_val"));
        double minVal = toDouble(StatisticsService.getValue(row, "min_val"));
        long sampleCount = toLong(StatisticsService.getValue(row, "sample_count"));
        if (time == null) return null;
        return new TrendBarVO(time, round2(avgVal), round2(maxVal), round2(minVal), sampleCount);
    }

    private IntradayPointVO mapToIntradayPoint(Map<String, Object> row) {
        LocalDateTime time = toLocalDateTime(StatisticsService.getValue(row, "moment"));
        double value = toDouble(StatisticsService.getValue(row, "value"));
        if (time == null) return null;
        return new IntradayPointVO(time, value);
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
        response.setDataPoints(Collections.emptyList());
        response.setIntradayPoints(Collections.emptyList());
        response.setAvg5(Collections.emptyList());
        response.setAvg10(Collections.emptyList());
        response.setAvg20(Collections.emptyList());
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
