/**
 * Anomaly marker construction shared by every trend option builder
 * (candlestick, intraday, multi-series comparison).
 *
 * The analysis API client (src/api/iot/analysis.js) normalizes backend
 * AnomalyPoint records ({timestamp, actualValue, expectedValue, deviation,
 * severity}) to { time, actual, expected, deviation, severity } — markers
 * must read THOSE keys. The candlestick/intraday builders once read a.t /
 * a.value and silently dropped every marker (final review Important #1).
 */

/**
 * Find the index of the point whose time bucket contains anomalyTime.
 *
 * Points carry the unified TrendPointVO `t` field (backend LocalDateTime,
 * no timezone). Bucket matching uses LOCAL-day / LOCAL-hour truncation (not
 * UTC day), because the front-end Date constructor parses LocalDateTime as
 * local time. Dividing UTC-ms by 86_400_000 would compute UTC-day index and
 * mismatch around timezone boundaries. Using setHours/setMinutes zeroes out
 * the local time fields correctly.
 *
 * INTRADAY bucket = the most recent sample at or before the anomaly (the
 * client cannot know the RAW sample interval, so floor-match is the honest
 * semantics for raw/BUCKETED intraday points).
 * HOUR bucket     = same local hour.
 * DAY bucket      = same local day.
 *
 * WEEK/MONTH granularities are matched by day too — in practice the front-end
 * does NOT call detectAnomalies at WEEK/MONTH granularity, so anomalies never
 * arrive for these granularities. Even if a stray anomaly is passed in,
 * day-aligned match returns -1 (skip) rather than mis-render.
 *
 * @returns {number} bucket index, or -1 if not found
 */
export function findBucketIndex(points, anomalyTime, granularity) {
  if (!anomalyTime || !points || points.length === 0) return -1
  const anomalyDate = new Date(String(anomalyTime).replace(' ', 'T'))
  if (isNaN(anomalyDate.getTime())) return -1
  if (granularity === 'INTRADAY') {
    let best = -1
    for (let i = 0; i < points.length; i++) {
      const dp = new Date(String(points[i].t || '').replace(' ', 'T'))
      if (isNaN(dp.getTime())) continue
      if (dp.getTime() <= anomalyDate.getTime()) best = i
    }
    return best
  }
  // Truncate anomaly to bucket start in LOCAL time
  const bucketKey = granularity === 'HOUR'
    ? new Date(anomalyDate.getFullYear(), anomalyDate.getMonth(), anomalyDate.getDate(), anomalyDate.getHours()).getTime()
    : new Date(anomalyDate.getFullYear(), anomalyDate.getMonth(), anomalyDate.getDate()).getTime()
  for (let i = 0; i < points.length; i++) {
    const dp = new Date(String(points[i].t || '').replace(' ', 'T'))
    if (isNaN(dp.getTime())) continue
    const dpKey = granularity === 'HOUR'
      ? new Date(dp.getFullYear(), dp.getMonth(), dp.getDate(), dp.getHours()).getTime()
      : new Date(dp.getFullYear(), dp.getMonth(), dp.getDate()).getTime()
    if (dpKey === bucketKey) return i
  }
  return -1
}

/**
 * Build ECharts markPoint data arrays for anomaly points.
 *
 * coord[0] is always `formatX(points[idx].t)` — the same formatter applied to
 * xAxis.data — so ECharts category-axis string equality holds (ECharts 5
 * markPoint does NOT support dataIndex; the string coord is required and must
 * match an xAxis.data entry literally).
 *
 * Anomalies whose time falls outside the points range are silently skipped
 * (no error, no marker) — the typical cause is a stricter time window than
 * the underlying data set.
 */
export function anomalyMarkerData(anomalyPoints, points, granularity, formatX) {
  if (!Array.isArray(anomalyPoints) || anomalyPoints.length === 0) return []
  if (!Array.isArray(points) || points.length === 0) return []
  const result = []
  for (const p of anomalyPoints) {
    const idx = findBucketIndex(points, p.time, granularity)
    if (idx < 0) continue
    result.push({
      coord: [formatX(points[idx].t), p.actual],
      value: p.deviation != null ? `${Number(p.deviation).toFixed(1)}σ` : '',
      itemStyle: { color: p.severity === 'high' ? '#ff5252' : '#ffab00' },
      label: { show: true, fontSize: 9 },
    })
  }
  return result
}
