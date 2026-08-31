/**
 * Color palette and unit-grouping helpers for multi-monitor comparison chart.
 *
 * Palette selected for:
 *  - High contrast against dark theme (#0a0e17 background)
 *  - Distinct hues to differentiate up to 4 monitors side-by-side
 *  - First 4 colors match the single-chart visual signature (cyan mean, red
 *    max, green min, amber MA5) so users recognize the "primary" series
 */

export const COMPARISON_PALETTE = [
  '#00d4ff',  // cyan   — primary
  '#ff5252',  // red    — high severity / max
  '#69f0ae',  // green  — min
  '#ffab00',  // amber  — MA5
  '#ce93d8',  // purple
  '#7fff7f',  // light green
  '#ff79c6',  // pink
  '#f1fa8c',  // yellow
]

/**
 * Pick a palette color for the given series index.
 * Wraps modulo palette length so > 8 monitors cycle through (rare case).
 *
 * Negative index is guarded to avoid JS modulo returning negative values,
 * which would index palette[-1] = undefined.
 */
export function pickColor(index) {
  if (!Number.isFinite(index)) return COMPARISON_PALETTE[0]
  const len = COMPARISON_PALETTE.length
  // ((index % len) + len) % len — JS % returns negative for negative input
  const i = ((index % len) + len) % len
  return COMPARISON_PALETTE[i]
}

/**
 * Group trend series by engineering unit.
 * Null/empty/undefined units collapse into a single '(无单位)' group so
 * ComparisonChart's layout decision (single-axis vs dual-axis vs small-multiples)
 * isn't fragmented by missing metadata.
 *
 * Insertion order is preserved within each group so caller-assigned series
 * order (e.g. user's selection order) is honored.
 *
 * @param {Array<{unit: string|null|undefined}>} series
 * @returns {Map<string, Array>} keyed by unit (or '(无单位)')
 */
export function groupSeriesByUnit(series) {
  const groups = new Map()
  if (!Array.isArray(series)) return groups
  for (const s of series) {
    const key = (s && s.unit) ? s.unit : '(无单位)'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(s)
  }
  return groups
}
