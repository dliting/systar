import { formatMonitorValue } from '@/config/monitor'
import { groupSeriesByUnit, pickColor } from '@/views/statistics/composables/palette'
import { darkTooltip, labelFor, MA_COLORS } from '@/components/TrendChart/theme'
import { anomalyMarkerData } from './anomalies'

// Bucket matching lives in ./anomalies (shared with the candlestick/intraday
// builders); re-exported here for existing consumers.
export { findBucketIndex } from './anomalies'

const GRID_COLOR = '#2a4a7f'
const LABEL_COLOR = '#8892b0'

function escapeHtml(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#x27;')
}

/**
 * Build ECharts markLine data array for min/max thresholds.
 * Returns [] when disabled or no thresholds configured.
 *
 * label.formatter is a STATIC string (pre-computed via formatMonitorValue) —
 * NOT a function and NOT a template using {c}. This avoids ECharts replacing
 * our precision-controlled label with the raw yAxis value.
 */
function buildThresholdMarkLines(thresholds, showThresholds, unit) {
  if (!showThresholds || !thresholds) return []
  const lines = []
  if (thresholds.max != null) {
    lines.push({
      yAxis   : thresholds.max,
      name    : '上限',
      lineStyle: { color: '#ff5252', type: 'dashed' },
      label   : { formatter: `上限 ${formatMonitorValue(thresholds.max, unit)}`, position: 'end' },
    })
  }
  if (thresholds.min != null) {
    lines.push({
      yAxis   : thresholds.min,
      name    : '下限',
      lineStyle: { color: '#69f0ae', type: 'dashed' },
      label   : { formatter: `下限 ${formatMonitorValue(thresholds.min, unit)}`, position: 'end' },
    })
  }
  return lines
}

/**
 * Build ECharts markPoint data array for anomaly points — delegates to the
 * shared builder in ./anomalies (same shape contract as the candlestick and
 * intraday builders).
 */
function buildAnomalyMarkPoints(anomalyPoints, showAnomalies, points, granularity, formatX) {
  if (!showAnomalies) return []
  return anomalyMarkerData(anomalyPoints, points, granularity, formatX)
}

/** True when the array carries at least one non-null value (an MA line with
 *  only nulls means "too few points for that period" — render nothing). */
function hasAnyValue(arr) {
  return Array.isArray(arr) && arr.some(v => v !== null && v !== undefined)
}

// ==================== Multi-Series (Comparison Chart) ====================

/**
 * Build an ECharts option for comparing multiple monitors.
 *
 * Layout strategy by unit group count:
 *   1 group  → single Y axis, all series on yAxis[0]
 *   2 groups → dual Y axis (left + right)
 *   3+ groups is handled by the caller (ComparisonChart) via smallMultiplesOption
 *   — multiSeriesOption itself only renders up to 2 Y axes.
 *
 * Each series shows the avg (mean) curve; client-computed moving averages
 * (ma5/ma10/ma20 on the series) render as 5/10/20期平均 overlays in MA_COLORS.
 * With several MA-carrying series the legend names are prefixed with the
 * monitor name to stay distinguishable. markLine (thresholds) and markPoint
 * (anomalies) attach per series.
 *
 * @param {Array} series - [{ monitorId, name, unit, color, points, ma5, ma10, ma20, thresholds, anomalies }]
 * @param {string} granularity - INTRADAY/HOUR/DAY/WEEK/MONTH
 * @param {number} rangeDays - kept for call-site stability; axis labels derive from granularity alone
 * @param {boolean} [showThresholds=true]
 * @param {boolean} [showAnomalies=true]
 */
export function multiSeriesOption(series, granularity, rangeDays, showThresholds = true, showAnomalies = true) {
  if (!Array.isArray(series) || series.length === 0) {
    return { series: [] }
  }

  const groups = groupSeriesByUnit(series)
  const unitNames = [...groups.keys()]
  // Cap at 2 Y axes — caller routes 3+ unit groups to smallMultiplesOption instead
  const useDualAxis = unitNames.length === 2

  // Shared X axis: use the first series' points (assumes aligned buckets)
  const referencePoints = series[0].points || []
  const formatX = (t) => labelFor(t, granularity)
  const times = referencePoints.map(p => formatX(p.t))

  // Y axes
  const yAxis = unitNames.map(name => ({
    type        : 'value',
    name        : name,
    position    : unitNames.indexOf(name) === 0 ? 'left' : 'right',
    axisLabel   : { color: LABEL_COLOR, fontSize: 10 },
    nameTextStyle: { color: LABEL_COLOR, fontSize: 10 },
    splitLine   : { show: true, lineStyle: { color: GRID_COLOR } },
    axisLine    : { lineStyle: { color: GRID_COLOR } },
  }))

  // Prefix MA legend names only when several series carry MAs — with a single
  // series the plain 5/10/20期平均 names are unambiguous.
  const maCarrierCount = series.filter(
    s => hasAnyValue(s.ma5) || hasAnyValue(s.ma10) || hasAnyValue(s.ma20),
  ).length
  const prefixMaNames = maCarrierCount > 1

  // Build one avg line per monitor, plus MA overlays when carried
  const echartsSeries = []
  for (let sIdx = 0; sIdx < series.length; sIdx++) {
    const s = series[sIdx]
    const unitIdx = unitNames.indexOf(s.unit || '(无单位)')
    const color = s.color || pickColor(sIdx)
    const points = s.points || []
    const avgData = points.map(p => p.avg)
    const name = s.name || `monitor-${s.monitorId}`

    const markLine = {
      symbol   : 'none',
      silent   : false,
      animation: false,
      data     : buildThresholdMarkLines(s.thresholds, showThresholds, s.unit),
    }
    const markPoint = {
      symbolSize: 24,
      data      : buildAnomalyMarkPoints(s.anomalies, showAnomalies, points, granularity, formatX),
    }

    echartsSeries.push({
      name     : name,
      type     : 'line',
      data     : avgData,
      yAxisIndex: unitIdx,
      smooth   : true,
      symbol   : 'none',
      lineStyle: { color, width: 2 },
      itemStyle: { color },
      markLine,
      markPoint,
    })

    const maSpecs = [
      { data: s.ma5,  period: 5,  color: MA_COLORS[0] },
      { data: s.ma10, period: 10, color: MA_COLORS[1] },
      { data: s.ma20, period: 20, color: MA_COLORS[2] },
    ]
    for (const ma of maSpecs) {
      if (!hasAnyValue(ma.data)) continue
      const label = `${ma.period}期平均`
      echartsSeries.push({
        name     : prefixMaNames ? `${name} ${label}` : label,
        type     : 'line',
        data     : ma.data,
        yAxisIndex: unitIdx,
        smooth   : true,
        symbol   : 'none',
        lineStyle: { color: ma.color, width: 1, type: 'dotted' },
        itemStyle: { color: ma.color },
      })
    }
  }

  return {
    tooltip: {
      ...darkTooltip(),
      trigger: 'axis',
      formatter(params) {
        if (!Array.isArray(params) || params.length === 0) return ''
        // Group header by axis value (time)
        let html = `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(params[0].axisValue)}</div>`
        // Each series row — unit appended only when present
        for (const p of params) {
          const s = series.find(x => (x.name || `monitor-${x.monitorId}`) === p.seriesName)
          const unitLabel = s && s.unit ? ` (${s.unit})` : ''
          const color = p.color || '#8892b0'
          html += `<div>${escapeHtml(p.seriesName)}: <span style="color:${color}">${escapeHtml(formatMonitorValue(p.value, s?.unit))}</span><span style="color:#8892b0;font-size:11px">${escapeHtml(unitLabel)}</span></div>`
        }
        return html
      },
    },
    legend: {
      data: echartsSeries.map(s => s.name),
      textStyle: { color: LABEL_COLOR, fontSize: 11 },
      top: 0,
      right: 20,
    },
    grid: { left: 60, right: useDualAxis ? 60 : 20, top: 40, bottom: 50 },
    xAxis: {
      type     : 'category',
      data     : times,
      axisLabel: { color: LABEL_COLOR, fontSize: 9 },
      axisLine : { lineStyle: { color: GRID_COLOR } },
      splitLine: { show: true, lineStyle: { color: GRID_COLOR } },
    },
    yAxis,
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      {
        type       : 'slider',
        bottom     : 10,
        height     : 16,
        borderColor: '#1e3a5f',
        fillerColor: 'rgba(0,212,255,0.15)',
        handleStyle: { color: '#00d4ff' },
        textStyle  : { color: '#8892b0' },
      },
    ],
    series: echartsSeries,
  }
}

// ==================== Empty Frame ====================

export { emptyFrameOption } from './empty'
