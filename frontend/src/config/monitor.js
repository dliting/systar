/**
 * Centralized monitoring display configuration.
 * All monitoring-related formatting and precision settings live here.
 */

/** Datetime format for monitoring data display */
export const MONITOR_DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/** Number of decimal places for monitoring values (fallback when unit unknown) */
export const MONITOR_VALUE_PRECISION = 3

/**
 * Default time window size in periods for chart X-axis.
 * The chart shows the most recent N periods (e.g. 60 hours, 60 days)
 * regardless of how many data points exist. Older data is reachable via dataZoom.
 */
export const DEFAULT_PERIOD_WINDOW = 60

/**
 * Per-unit decimal precision overrides.
 * Unknown units fall back to MONITOR_VALUE_PRECISION (3).
 * When adding new units, register them here to avoid precision drift.
 */
export const UNIT_PRECISION_MAP = {
  '°C': 1, C: 1, '℃': 1,
  '%': 0, '%RH': 0,
  V: 2, mV: 0,
  A: 2, mA: 2,
  kPa: 2, Pa: 0, MPa: 3,
  'm³/h': 1, 'L/min': 1,
  rpm: 0,
  Wh: 0, kWh: 2,
}

/**
 * Resolve decimal precision for a given unit.
 * @param {string|undefined} unit
 * @returns {number}
 */
export function precisionForUnit(unit) {
  if (!unit) return MONITOR_VALUE_PRECISION
  const mapped = UNIT_PRECISION_MAP[unit]
  return mapped ?? MONITOR_VALUE_PRECISION
}

/**
 * Format a monitoring value with configured precision.
 * Returns '--' for null/undefined values.
 *
 * Pure utility functions without unit context may call with a single argument;
 * in that case precision falls back to MONITOR_VALUE_PRECISION (3) — note this
 * in JSDoc of the call site so future maintainers know the unit was not silently
 * dropped.
 *
 * @param {*} val - value to format
 * @param {string} [unit] - engineering unit (e.g. '°C', '%', 'V'); drives precision
 * @returns {string}
 */
export function formatMonitorValue(val, unit) {
  if (val === null || val === undefined) return '--'
  if (typeof val === 'number') {
    if (Number.isInteger(val)) return val.toString()
    return val.toFixed(precisionForUnit(unit))
  }
  return String(val)
}

/**
 * Format a Date object to the standard monitoring datetime string.
 * @param {Date} d
 * @returns {string}
 */
export function formatMonitorDateTime(d) {
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * Format a time string for axis labels.
 * Extracts HH:mm from a full datetime string, or returns the value as-is.
 * @param {string} timeStr - e.g. "2025-01-15 14:30:00" or "14:30"
 * @returns {string}
 */
export function formatMonitorAxisTime(timeStr) {
  if (!timeStr) return ''
  const sep = timeStr.includes('T') ? 'T' : (timeStr.includes(' ') ? ' ' : null)
  if (sep) {
    const [date, time] = timeStr.split(sep)
    if (!time) return date.substring(5)
    const [h, m] = time.split(':')
    return `${h}:${m}`
  }
  return timeStr
}

/**
 * Compute the range in days between two datetime strings.
 *
 * @param {[string, string] | null | undefined} dateRange - pair of datetime strings
 *   in "YYYY-MM-DD HH:mm:ss" or "YYYY-MM-DDTHH:mm:ss" format.
 * @returns {number} days between start and end; 0 if range invalid or inverted.
 */
export function computeRangeDays(dateRange) {
  if (!Array.isArray(dateRange) || dateRange.length !== 2) return 0
  const start = new Date(String(dateRange[0]).replace(' ', 'T'))
  const end   = new Date(String(dateRange[1]).replace(' ', 'T'))
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0
  return Math.max(0, (end.getTime() - start.getTime()) / 86_400_000)
}

/**
 * Adaptive axis time formatter for aggregated granularities (HOUR/DAY/WEEK/MONTH).
 *
 * Replaces the older formatLineAxisTime (used only by lineOption).
 * intradayOption keeps formatMonitorAxisTime (HH:mm) because INTRADAY range
 * is always < 1 day.
 *
 * Threshold notes: rangeDays thresholds use 1.5 and 365.5 (not 1 / 365) to avoid
 * boundary jitter — a 1.0-day range flipping between "same day" and "cross day"
 * modes at 1.01 days would cause UI flicker.
 *
 * @param {string} timeStr - "YYYY-MM-DDTHH:mm:ss" or "YYYY-MM-DD HH:mm:ss"
 *                           (no timezone offset; backend Jackson default for LocalDateTime).
 *                           **Must be zero-padded** (e.g. "06" not "6") — substring indices
 *                           assume fixed-width fields.
 * @param {string} granularity - one of INTRADAY/HOUR/DAY/WEEK/MONTH
 * @param {number} rangeDays - total range in days, typically from computeRangeDays
 * @returns {string} short label suitable for X-axis ticks
 */
export function formatAxisTimeAdaptive(timeStr, granularity, rangeDays) {
  if (!timeStr) return ''
  const clean = timeStr.replace('T', ' ').split('.')[0]
  if (granularity === 'MONTH') return clean.substring(0, 7)                                       // 2026-01
  if (granularity === 'WEEK' || granularity === 'DAY') {
    return rangeDays > 365.5 ? clean.substring(2, 10) : clean.substring(5, 10)                    // cross-year YY-MM-DD, else MM-DD
  }
  if (granularity === 'HOUR') {
    return rangeDays > 1.5 ? clean.substring(5, 13) : clean.substring(11, 16)                     // cross-day MM-DD HH, else HH:mm
  }
  return clean.substring(11, 16)                                                                  // INTRADAY: HH:mm
}
