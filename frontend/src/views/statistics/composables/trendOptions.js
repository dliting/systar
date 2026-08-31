import * as echarts from 'echarts'
import {
  formatMonitorValue,
  formatMonitorAxisTime,
  formatAxisTimeAdaptive,
  DEFAULT_PERIOD_WINDOW,
} from '@/config/monitor'
import { groupSeriesByUnit, pickColor } from './palette'

const GRID_COLOR = '#2a4a7f'
const LABEL_COLOR = '#8892b0'

const darkTooltip = {
  backgroundColor: 'rgba(10,14,23,0.95)',
  borderColor   : '#1e3a5f',
  textStyle     : { color: '#8892b0', fontSize: 12 },
}

function zoomStartForWindow(periodCount) {
  if (periodCount <= 0 || periodCount <= DEFAULT_PERIOD_WINDOW) return 0
  return 100 - Math.round((DEFAULT_PERIOD_WINDOW / periodCount) * 100)
}

function escapeHtml(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#x27;')
}

function allNull(arr) {
  if (!Array.isArray(arr) || arr.length === 0) return true
  return arr.every(v => v === null || v === undefined)
}

/**
 * Find the index of the dataPoint whose time bucket contains anomalyTime.
 *
 * Bucket matching uses LOCAL-day / LOCAL-hour truncation (not UTC day), because
 * the front-end Date constructor parses backend LocalDateTime (no timezone) as
 * local time. Dividing UTC-ms by 86_400_000 would compute UTC-day index and
 * mismatch around timezone boundaries. Using setHours/setMinutes zeroes out
 * the local time fields correctly.
 *
 * HOUR bucket = same local hour.
 * DAY bucket  = same local day.
 *
 * WEEK/MONTH granularities are matched by day too — in practice the front-end
 * does NOT call detectAnomalies at WEEK/MONTH granularity (see MonitorTrend.vue
 * phase-2 plan), so anomalies never arrive for these granularities. Even if a
 * stray anomaly is passed in, day-aligned match returns -1 (skip) rather than
 * mis-render.
 *
 * @returns {number} bucket index, or -1 if not found
 */
function findBucketIndex(dataPoints, anomalyTime, granularity) {
  if (!anomalyTime || !dataPoints || dataPoints.length === 0) return -1
  const anomalyDate = new Date(String(anomalyTime).replace(' ', 'T'))
  if (isNaN(anomalyDate.getTime())) return -1
  // Truncate anomaly to bucket start in LOCAL time
  const bucketKey = granularity === 'HOUR'
    ? new Date(anomalyDate.getFullYear(), anomalyDate.getMonth(), anomalyDate.getDate(), anomalyDate.getHours()).getTime()
    : new Date(anomalyDate.getFullYear(), anomalyDate.getMonth(), anomalyDate.getDate()).getTime()
  for (let i = 0; i < dataPoints.length; i++) {
    const dp = new Date(String(dataPoints[i].time || '').replace(' ', 'T'))
    if (isNaN(dp.getTime())) continue
    const dpKey = granularity === 'HOUR'
      ? new Date(dp.getFullYear(), dp.getMonth(), dp.getDate(), dp.getHours()).getTime()
      : new Date(dp.getFullYear(), dp.getMonth(), dp.getDate()).getTime()
    if (dpKey === bucketKey) return i
  }
  return -1
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
 * Build ECharts markPoint data array for anomaly points.
 *
 * coord[0] is always `formatX(dataPoints[idx].time)` — the same formatX
 * applied to xAxis.data — so ECharts category-axis string equality holds
 * (ECharts 5 markPoint does NOT support dataIndex; string coord is required
 * and must match an xAxis.data entry literally).
 *
 * Anomalies whose time falls outside the dataPoints range are silently
 * skipped (no error, no marker) — the typical cause is a stricter time
 * window than the underlying data set.
 */
function buildAnomalyMarkPoints(anomalyPoints, showAnomalies, dataPoints, granularity, formatX) {
  if (!showAnomalies || !anomalyPoints || anomalyPoints.length === 0) return []
  const result = []
  for (const p of anomalyPoints) {
    const idx = findBucketIndex(dataPoints, p.time, granularity)
    if (idx < 0) continue
    result.push({
      coord   : [formatX(dataPoints[idx].time), p.actual],
      value   : `${p.deviation.toFixed(1)}σ`,
      itemStyle: { color: p.severity === 'high' ? '#ff5252' : '#ffab00' },
      label   : { show: true, fontSize: 9 },
    })
  }
  return result
}

// ==================== Aggregated Line Chart (HOUR/DAY/WEEK/MONTH) ====================

/**
 * Build an ECharts line-chart option for HOUR/DAY/WEEK/MONTH granularities
 * with max / mean / min curves and optional MA overlays.
 *
 * Tooltip header shows the unit once (avoids per-row redundancy).
 * xAxis.data uses formatAxisTimeAdaptive for cross-year / cross-day clarity.
 * dataZoom slider gets a "← drag for older" graphic hint when windowed.
 * Title element renders an "MA insufficient data" hint when all MAs are null.
 *
 * Thresholds and anomalyPoints (phase 2) attach markLine / markPoint to the
 * 均值 series.
 *
 * @param {Array}  dataPoints - [{ time, avg, max, min, sampleCount }]
 * @param {Array}  avg5
 * @param {Array}  avg10
 * @param {Array}  avg20
 * @param {string} unit
 * @param {string} granularity - one of HOUR/DAY/WEEK/MONTH
 * @param {boolean} compact - reduce font/margin for small charts
 * @param {number} [rangeDays] - total span in days (drives adaptive axis format)
 * @param {{min:number|null, max:number|null}} [thresholds]
 * @param {Array}  [anomalyPoints] - [{ time, actual, expected, deviation, severity }]
 * @param {boolean} [showThresholds]
 * @param {boolean} [showAnomalies]
 */
export function lineOption(
  dataPoints,
  avg5,
  avg10,
  avg20,
  unit,
  granularity,
  compact,
  rangeDays = 0,
  thresholds = null,
  anomalyPoints = [],
  showThresholds = false,
  showAnomalies = false,
  infiniteMode = false,
) {
  const times    = dataPoints.map(c => formatAxisTimeAdaptive(c.time, granularity, rangeDays))
  const avgData  = dataPoints.map(c => c.avg)
  const maxData  = dataPoints.map(c => c.max)
  const minData  = dataPoints.map(c => c.min)
  const unitText = unit ? `(${unit})` : ''
  const fontSize  = compact ? 9 : 10
  const gridLeft  = compact ? 48 : 60
  const gridTop   = compact ? 8 : 40
  const gridBottom = compact ? 22 : 50

  const series = [
    {
      name     : '最大值',
      type     : 'line',
      data     : maxData,
      smooth   : true,
      symbol   : 'none',
      lineStyle: { color: '#ff5252', width: 1, type: 'dashed' },
    },
    {
      name     : '均值',
      type     : 'line',
      data     : avgData,
      smooth   : true,
      symbol   : compact ? 'none' : 'circle',
      symbolSize: compact ? 0 : 5,
      lineStyle: { color: '#00d4ff', width: compact ? 1.5 : 2 },
      itemStyle: { color: '#00d4ff' },
      areaStyle: compact ? {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.15)' },
          { offset: 1, color: 'rgba(0,212,255,0)' },
        ]),
      } : {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.35)' },
          { offset: 1, color: 'rgba(0,212,255,0)' },
        ]),
      },
      markLine: {
        symbol   : 'none',
        silent   : false,
        animation: false,
        data     : buildThresholdMarkLines(thresholds, showThresholds, unit),
      },
      markPoint: {
        symbolSize: 30,
        data      : buildAnomalyMarkPoints(anomalyPoints, showAnomalies, dataPoints, granularity, t => formatAxisTimeAdaptive(t, granularity, rangeDays)),
      },
    },
    {
      name     : '最小值',
      type     : 'line',
      data     : minData,
      smooth   : true,
      symbol   : 'none',
      lineStyle: { color: '#69f0ae', width: 1, type: 'dashed' },
    },
  ]

  const legendData = ['最大值', '均值', '最小值']
  const maConfigs = [
    { name: '均线5',  data: avg5,  color: '#ffab00' },
    { name: '均线10', data: avg10, color: '#ce93d8' },
    { name: '均线20', data: avg20, color: 'rgba(255,82,82,0.7)' },
  ]
  for (const ma of maConfigs) {
    if (ma.data && ma.data.length > 0) {
      legendData.push(ma.name)
      series.push({
        name     : ma.name,
        type     : 'line',
        data     : ma.data,
        smooth   : true,
        lineStyle: { color: ma.color, width: 1, type: 'dotted' },
        symbol   : 'none',
      })
    }
  }

  // MA insufficient-data hint (only in full mode)
  const showMaHint = !compact && (allNull(avg5) && allNull(avg10) && allNull(avg20))
  const title = showMaHint ? {
    text     : '均线数据不足（需 ≥ 5 个数据点）',
    right    : 20,
    top      : 4,
    textStyle: { color: '#8892b0', fontSize: 11, fontWeight: 'normal' },
    z        : 100,
  } : undefined

  // dataZoom hint when windowed beyond DEFAULT_PERIOD_WINDOW
  const showZoomHint = !compact && dataPoints.length > DEFAULT_PERIOD_WINDOW
  const graphic = showZoomHint ? [{
    type  : 'text',
    left  : '5%',
    top   : '85%',
    z     : 100,
    style : {
      text  : '← 拖动查看更早',
      fill  : '#8892b0',
      fontSize: 11,
    },
  }] : undefined

  return {
    tooltip: {
      ...darkTooltip,
      trigger: 'axis',
      formatter(params) {
        if (!Array.isArray(params) || params.length === 0) return ''
        const header = unitText
          ? `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(params[0].axisValue)} <span style="color:#8892b0;font-weight:400">${escapeHtml(unitText)}</span></div>`
          : `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(params[0].axisValue)}</div>`
        let rows = ''
        for (const p of params) {
          const color = p.color || '#8892b0'
          rows += `<div>${escapeHtml(p.seriesName)}: <span style="color:${color}">${escapeHtml(formatMonitorValue(p.value, unit))}</span></div>`
        }
        return header + rows
      },
    },
    legend: compact ? undefined : {
      data: legendData,
      textStyle: { color: '#8892b0', fontSize: 11 },
      top: 0,
      right: 20,
    },
    title,
    graphic,
    grid: { left: gridLeft, right: compact ? 10 : 20, top: gridTop, bottom: gridBottom },
    xAxis: {
      type     : 'category',
      data     : times,
      axisLabel: { color: LABEL_COLOR, fontSize: fontSize - 2 },
      axisLine : { lineStyle: { color: GRID_COLOR } },
      splitLine: { show: true, lineStyle: { color: GRID_COLOR } },
    },
    yAxis: {
      type        : 'value',
      name        : unit || '',
      axisLabel   : { show: true, color: LABEL_COLOR, fontSize },
      nameTextStyle: { color: LABEL_COLOR, fontSize },
      splitLine   : { show: true, lineStyle: { color: GRID_COLOR } },
      axisLine    : { lineStyle: { color: GRID_COLOR } },
    },
    dataZoom: compact ? undefined : [
      { type: 'inside', ...(infiniteMode ? {} : { start: zoomStartForWindow(dataPoints.length), end: 100 }) },
      {
        type        : 'slider',
        bottom      : 10,
        height      : 16,
        borderColor : '#1e3a5f',
        fillerColor : 'rgba(0,212,255,0.15)',
        handleStyle : { color: '#00d0ff' },
        textStyle   : { color: '#8892b0' },
      },
    ],
    series,
  }
}

// ==================== Intraday Line Chart ====================

export function intradayOption(points, unit, displayCount = 60, detectIntervalSeconds = 10, compact = false, infiniteMode = false) {
  const count = Math.max(points.length, displayCount)
  const pad   = count - points.length
  const padded = [...Array(pad).fill(null), ...points]

  let firstTime = null
  for (const p of points) {
    if (p && p.time) { firstTime = p.time; break }
  }
  const estimatedTimes = []
  if (firstTime && pad > 0) {
    const baseDate = new Date(firstTime.replace(' ', 'T'))
    for (let i = pad - 1; i >= 0; i--) {
      const d = new Date(baseDate.getTime() - (i + 1) * detectIntervalSeconds * 1000)
      estimatedTimes.push(formatMonitorAxisTime(d.toISOString().replace('T', ' ').split('.')[0]))
    }
  }

  const times    = padded.map((p, i) => (p ? formatMonitorAxisTime(p.time) : estimatedTimes[i] || ''))
  const rawTimes = padded.map(p => (p ? (p.time || '') : ''))
  const values   = padded.map(p => (p ? p.value : null))
  const unitText = unit ? `(${unit})` : ''
  const fontSize  = compact ? 9 : 10
  const gridLeft  = compact ? 48 : 60
  const gridTop   = compact ? 8 : 20

  return {
    tooltip: {
      ...darkTooltip,
      trigger: 'axis',
      formatter(params) {
        if (!Array.isArray(params) || params.length === 0) return ''
        const p = params[0]
        const idx = p.dataIndex
        const rawTime = rawTimes[idx] || p.axisValue
        const header = unitText
          ? `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(rawTime)} <span style="color:#8892b0;font-weight:400">${escapeHtml(unitText)}</span></div>`
          : `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(rawTime)}</div>`
        return header + `<div>监测值: <span style="color:#69f0ae">${escapeHtml(formatMonitorValue(p.value, unit))}</span></div>`
      },
    },
    grid: { left: gridLeft, right: compact ? 10 : 20, top: gridTop, bottom: compact ? 22 : 50 },
    xAxis: {
      type     : 'category',
      data     : times,
      axisLabel: { color: LABEL_COLOR, fontSize: fontSize - 2 },
      axisLine : { lineStyle: { color: GRID_COLOR } },
      splitLine: { show: true, lineStyle: { color: GRID_COLOR } },
    },
    yAxis: {
      type        : 'value',
      name        : unit || '',
      axisLabel   : { show: true, color: LABEL_COLOR, fontSize },
      splitLine   : { show: true, lineStyle: { color: GRID_COLOR } },
      axisLine    : { lineStyle: { color: GRID_COLOR } },
    },
    dataZoom: compact ? undefined : [
      { type: 'inside', ...(infiniteMode ? {} : { start: 0, end: 100 }) },
      {
        type        : 'slider',
        bottom      : 10,
        height      : 16,
        borderColor : '#1e3a5f',
        fillerColor : 'rgba(0,212,255,0.15)',
        handleStyle : { color: '#00d4ff' },
        textStyle   : { color: '#8892b0' },
      },
    ],
    series: [{
      type      : 'line',
      data      : values,
      smooth    : true,
      symbol    : compact ? 'none' : 'circle',
      symbolSize: compact ? 0 : 5,
      lineStyle : { color: '#00d4ff', width: compact ? 1.5 : 2 },
      itemStyle : { color: '#00d4ff' },
      areaStyle : compact ? {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.15)' },
          { offset: 1, color: 'rgba(0,212,255,0)' },
        ]),
      } : {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.35)' },
          { offset: 1, color: 'rgba(0,212,255,0)' },
        ]),
      },
    }],
  }
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
 * Each series shows only the avg (mean) curve for visual clarity —
 * max/min/MA overlays make multi-monitor charts unreadable.
 * markLine (thresholds) and markPoint (anomalies) attach per series.
 *
 * @param {Array} series - [{ id, caption, unit, color, dataPoints, thresholds, anomalies }]
 * @param {string} granularity - HOUR/DAY/WEEK/MONTH
 * @param {number} rangeDays
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

  // Shared X axis: use the first series' dataPoints (assumes aligned buckets)
  const referencePoints = series[0].dataPoints || []
  const formatX = (t) => formatAxisTimeAdaptive(t, granularity, rangeDays)
  const times = referencePoints.map(p => formatX(p.time))

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

  // Build one series per monitor (avg only)
  const echartsSeries = []
  for (let sIdx = 0; sIdx < series.length; sIdx++) {
    const s = series[sIdx]
    const unitIdx = unitNames.indexOf(s.unit || '(无单位)')
    const color = s.color || pickColor(sIdx)
    const dataPoints = s.dataPoints || []
    const avgData = dataPoints.map(p => p.avg)

    const markLine = {
      symbol   : 'none',
      silent   : false,
      animation: false,
      data     : buildThresholdMarkLines(s.thresholds, showThresholds, s.unit),
    }
    const markPoint = {
      symbolSize: 24,
      data      : buildAnomalyMarkPoints(s.anomalies, showAnomalies, dataPoints, granularity, formatX),
    }

    echartsSeries.push({
      name     : s.caption || `monitor-${s.id}`,
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
  }

  return {
    tooltip: {
      ...darkTooltip,
      trigger: 'axis',
      formatter(params) {
        if (!Array.isArray(params) || params.length === 0) return ''
        // Group header by axis value (time)
        let html = `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(params[0].axisValue)}</div>`
        // Each series row — unit appended only when present
        for (const p of params) {
          const s = series.find(x => (x.caption || `monitor-${x.id}`) === p.seriesName)
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

export function emptyFrameOption(granularity, unit, detectIntervalSeconds = 10) {
  const half = Math.floor(DEFAULT_PERIOD_WINDOW / 2)
  const now  = new Date()
  const times = []

  for (let i = -half; i < DEFAULT_PERIOD_WINDOW - half; i++) {
    const d = new Date(now)
    switch (granularity) {
      case 'INTRADAY': d.setSeconds(d.getSeconds() + i * detectIntervalSeconds); break
      case 'HOUR'    : d.setHours(d.getHours() + i); break
      case 'DAY'     : d.setDate(d.getDate() + i); break
      case 'WEEK'    : d.setDate(d.getDate() + i * 7); break
      case 'MONTH'   : d.setMonth(d.getMonth() + i); break
      default        : d.setDate(d.getDate() + i)
    }
    const pad = n => String(n).padStart(2, '0')
    if (granularity === 'MONTH') {
      times.push(`${d.getFullYear()}/${pad(d.getMonth() + 1)}`)
    } else if (granularity === 'INTRADAY' || granularity === 'HOUR') {
      times.push(`${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`)
    } else {
      times.push(`${pad(d.getMonth() + 1)}/${pad(d.getDate())}`)
    }
  }

  return {
    grid: { left: 60, right: 20, top: 20, bottom: 50 },
    xAxis: {
      type     : 'category',
      data     : times,
      axisLabel: { color: LABEL_COLOR, fontSize: 10 },
      axisLine : { lineStyle: { color: GRID_COLOR } },
      splitLine: { show: true, lineStyle: { color: GRID_COLOR } },
    },
    yAxis: {
      type        : 'value',
      min         : 0,
      max         : 100,
      name        : unit || '',
      axisLabel   : { color: LABEL_COLOR },
      nameTextStyle: { color: LABEL_COLOR },
      splitLine   : { show: true, lineStyle: { color: GRID_COLOR } },
      axisLine    : { lineStyle: { color: GRID_COLOR } },
    },
    series: [],
  }
}

