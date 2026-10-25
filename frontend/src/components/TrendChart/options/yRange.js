/**
 * Shared y-axis range + threshold visibility rule (spec D4, Grafana
 * convention): a threshold line is drawn only when it sits inside the data
 * range or within 2x the data span of its edge — farther out it would pin to
 * the axis edge and mislead. Included out-of-range thresholds extend the
 * returned range. lo/hi are undefined for empty/all-null data (auto-scale).
 */
export function valueRange(points, thresholds, showThresholds = true) {
  let lo = Infinity
  let hi = -Infinity
  for (const p of points) {
    if (p.min != null && p.min < lo) lo = p.min
    if (p.max != null && p.max > hi) hi = p.max
  }
  if (!Number.isFinite(lo) || !Number.isFinite(hi)) {
    return { lo: undefined, hi: undefined, showMax: false, showMin: false }
  }
  const span = (hi - lo) || Math.abs(hi || 1) * 0.05
  let showMax = false
  let showMin = false
  if (showThresholds && thresholds) {
    if (thresholds.max != null && nearRange(thresholds.max, lo, hi, span)) {
      showMax = true
      if (thresholds.max > hi) hi = thresholds.max
    }
    if (thresholds.min != null && nearRange(thresholds.min, lo, hi, span)) {
      showMin = true
      if (thresholds.min < lo) lo = thresholds.min
    }
  }
  return { lo, hi, showMax, showMin }
}

function nearRange(value, lo, hi, span) {
  if (value >= lo && value <= hi) return true
  const dist = value > hi ? value - hi : lo - value
  return dist <= 2 * span
}
