/**
 * Duration utility functions for TimeSpan format (10s, 5m, 2h, 1d).
 * Mirrors the backend TimeSpan class in core/systar-common/src/main/java/com/systar/common/util/TimeSpan.java.
 */

const UNIT_SECONDS = {
  s: 1,
  m: 60,
  h: 3600,
  d: 86400,
}

const UNIT_LABELS = {
  s: '秒',
  m: '分钟',
  h: '小时',
  d: '天',
}

const UNIT_OPTIONS = Object.entries(UNIT_LABELS).map(([value, label]) => ({ value, label }))

const TIMESPAN_RE = /^(\d+)([smhd])$/
const HHMMSS_RE  = /^(\d+):(\d+):(\d+)$/

/**
 * Parse a TimeSpan or HH:mm:ss string into { amount, unit }.
 * Returns null for empty/invalid input.
 * HH:mm:ss is automatically converted to the best TimeSpan unit.
 */
function parseDuration(str) {
  if (!str || typeof str !== 'string') return null

  const trimmed = str.trim()
  if (!trimmed) return null

  // Special case: "0"
  if (trimmed === '0') return { amount: 0, unit: 's' }

  // Try TimeSpan format: "10s", "5m", "2h", "1d"
  const tsMatch = trimmed.match(TIMESPAN_RE)
  if (tsMatch) {
    const amount = Number(tsMatch[1])
    const unit   = tsMatch[2]
    return smartUnit(amount * UNIT_SECONDS[unit])
  }

  // Try HH:mm:ss format: "00:00:10"
  const hhMatch = trimmed.match(HHMMSS_RE)
  if (hhMatch) {
    const totalSeconds = Number(hhMatch[1]) * 3600
                     + Number(hhMatch[2]) * 60
                     + Number(hhMatch[3])
    return smartUnit(totalSeconds)
  }

  return null
}

/**
 * Format { amount, unit } into a TimeSpan string.
 * "0" for zero duration, otherwise "5m", "10s", etc.
 */
function formatDuration(amount, unit) {
  if (amount == null || amount === 0) return '0'
  return `${amount}${unit}`
}

/**
 * Convert a value from one unit to another.
 * Returns the converted numeric value (may be fractional).
 */
function convertUnit(amount, fromUnit, toUnit) {
  if (fromUnit === toUnit) return amount
  const fromSec = UNIT_SECONDS[fromUnit]
  const toSec   = UNIT_SECONDS[toUnit]
  return (amount * fromSec) / toSec
}

/**
 * Convert { amount, unit } to total seconds.
 */
function toSeconds(amount, unit) {
  return amount * UNIT_SECONDS[unit]
}

/**
 * Choose the best unit for a given total-seconds value.
 * Prefers the largest unit that divides evenly, falling back to seconds.
 * e.g. 120 → { amount: 2, unit: 'm' }, 90 → { amount: 90, unit: 's' }
 */
function smartUnit(totalSeconds) {
  if (totalSeconds <= 0) return { amount: 0, unit: 's' }

  // Try largest unit first
  for (const unit of ['d', 'h', 'm']) {
    const divisor = UNIT_SECONDS[unit]
    if (totalSeconds >= divisor && totalSeconds % divisor === 0) {
      return { amount: totalSeconds / divisor, unit }
    }
  }

  return { amount: totalSeconds, unit: 's' }
}

export { parseDuration, formatDuration, convertUnit, toSeconds, smartUnit, UNIT_SECONDS, UNIT_LABELS, UNIT_OPTIONS }
