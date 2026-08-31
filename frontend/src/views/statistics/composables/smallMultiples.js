import {
  formatMonitorValue,
  formatAxisTimeAdaptive,
} from '@/config/monitor'
import { pickColor } from './palette'

const GRID_COLOR = '#2a4a7f'
const LABEL_COLOR = '#8892b0'

const darkTooltip = {
  backgroundColor: 'rgba(10,14,23,0.95)',
  borderColor   : '#1e3a5f',
  textStyle     : { color: '#8892b0', fontSize: 12 },
}

function escapeHtml(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#x27;')
}

/**
 * Build ECharts option for small-multiples layout (one mini-chart per monitor,
 * stacked vertically, sharing a common X-axis time range).
 *
 * Used when 3+ unit groups make a single chart with multi-Y-axis unreadable.
 * Each monitor gets its own grid + yAxis + xAxis + series, so unit differences
 * are isolated rather than competing for axis real estate.
 *
 * @param {Array} series - [{ id, caption, unit, color, dataPoints, thresholds, anomalies }]
 * @param {string} granularity - HOUR/DAY/WEEK/MONTH
 * @param {number} rangeDays
 * @param {boolean} [showThresholds=true]
 * @param {boolean} [showAnomalies=true]
 */
export function smallMultiplesOption(series, granularity, rangeDays, showThresholds = true, showAnomalies = true) {
  if (!Array.isArray(series) || series.length === 0) {
    return { series: [], grid: [], xAxis: [], yAxis: [], title: [] }
  }

  const n = series.length
  // Allocate each mini-chart a vertical slice, leaving 6% gap between them
  const totalHeight = 100
  const gapPct      = 6
  const chartHeight = (totalHeight - gapPct * (n - 1)) / n

  const grids   = []
  const xAxes   = []
  const yAxes   = []
  const titles  = []
  const echartsSeries = []

  for (let i = 0; i < n; i++) {
    const s = series[i]
    const color = s.color || pickColor(i)
    const dataPoints = s.dataPoints || []
    const formatX = (t) => formatAxisTimeAdaptive(t, granularity, rangeDays)
    const times = dataPoints.map(p => formatX(p.time))
    const avgData = dataPoints.map(p => p.avg)

    const topPct    = i * (chartHeight + gapPct)
    const bottomPct = 100 - (topPct + chartHeight)

    grids.push({
      left       : 60,
      right      : 20,
      top        : `${topPct}%`,
      bottom     : `${bottomPct}%`,
      containLabel: true,
    })

    xAxes.push({
      type     : 'category',
      data     : times,
      gridIndex: i,
      axisLabel: { color: LABEL_COLOR, fontSize: 9, hideOverlap: true },
      axisLine : { lineStyle: { color: GRID_COLOR } },
      splitLine: { show: false },  // mini-charts don't show vertical splits
    })

    yAxes.push({
      type        : 'value',
      name        : s.unit || '',
      gridIndex   : i,
      axisLabel   : { color: LABEL_COLOR, fontSize: 9 },
      nameTextStyle: { color: LABEL_COLOR, fontSize: 10 },
      splitLine   : { show: true, lineStyle: { color: GRID_COLOR } },
      axisLine    : { lineStyle: { color: GRID_COLOR } },
    })

    titles.push({
      text     : s.caption || `monitor-${s.id}`,
      subtext  : s.unit || '',
      left     : 'center',
      top      : `${topPct}%`,
      textStyle: { color: LABEL_COLOR, fontSize: 11, fontWeight: 'normal' },
      subtextStyle: { color: LABEL_COLOR, fontSize: 9 },
    })

    // Thresholds markLine
    const markLine = {
      symbol   : 'none',
      silent   : false,
      animation: false,
      data     : showThresholds && s.thresholds
        ? [
            ...(s.thresholds.max != null
              ? [{ yAxis: s.thresholds.max, lineStyle: { color: '#ff5252', type: 'dashed' }, label: { formatter: `上限 ${formatMonitorValue(s.thresholds.max, s.unit)}`, position: 'end', fontSize: 9 } }]
              : []),
            ...(s.thresholds.min != null
              ? [{ yAxis: s.thresholds.min, lineStyle: { color: '#69f0ae', type: 'dashed' }, label: { formatter: `下限 ${formatMonitorValue(s.thresholds.min, s.unit)}`, position: 'end', fontSize: 9 } }]
              : []),
          ]
        : [],
    }

    // Anomaly markPoint — same bucket-matching strategy as multiSeriesOption
    const markPoint = {
      symbolSize: 20,
      data      : showAnomalies && s.anomalies
        ? s.anomalies.map(p => {
            const idx = findBucketLocal(dataPoints, p.time, granularity)
            if (idx < 0) return null
            return {
              coord   : [formatX(dataPoints[idx].time), p.actual],
              value   : `${p.deviation.toFixed(1)}σ`,
              itemStyle: { color: p.severity === 'high' ? '#ff5252' : '#ffab00' },
              label   : { show: false },  // mini-chart: hide labels to avoid clutter
            }
          }).filter(Boolean)
        : [],
    }

    echartsSeries.push({
      name     : s.caption || `monitor-${s.id}`,
      type     : 'line',
      data     : avgData,
      xAxisIndex: i,
      yAxisIndex: i,
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
        const p = params[0]
        const s = series.find(x => (x.caption || `monitor-${x.id}`) === p.seriesName)
        const unitLabel = s && s.unit ? ` (${s.unit})` : ''
        return `<div style="color:#00d4ff;font-weight:600;margin-bottom:4px">${escapeHtml(p.axisValue)}</div>`
          + `<div>${escapeHtml(p.seriesName)}: <span style="color:${p.color || '#8892b0'}">${escapeHtml(formatMonitorValue(p.value, s?.unit))}</span><span style="color:#8892b0;font-size:11px">${escapeHtml(unitLabel)}</span></div>`
      },
    },
    grid   : grids,
    xAxis  : xAxes,
    yAxis  : yAxes,
    title  : titles,
    dataZoom: n > 1 ? [
      // Single shared dataZoom (inside + slider) for all grids — control all at once
      { type: 'inside', start: 0, end: 100, xAxisIndex: xAxes.map((_, i) => i) },
      {
        type       : 'slider',
        bottom     : 4,
        height     : 14,
        xAxisIndex : xAxes.map((_, i) => i),
        borderColor: '#1e3a5f',
        fillerColor: 'rgba(0,212,255,0.15)',
        handleStyle: { color: '#00d4ff' },
        textStyle  : { color: '#8892b0' },
      },
    ] : undefined,
    series : echartsSeries,
  }
}

/**
 * Local-time bucket matching for small multiples anomaly markPoint.
 * Mirrors trendOptions.findBucketIndex logic but inlined to avoid circular import.
 */
function findBucketLocal(dataPoints, anomalyTime, granularity) {
  if (!anomalyTime || !dataPoints || dataPoints.length === 0) return -1
  const a = new Date(String(anomalyTime).replace(' ', 'T'))
  if (isNaN(a.getTime())) return -1
  const aKey = granularity === 'HOUR'
    ? new Date(a.getFullYear(), a.getMonth(), a.getDate(), a.getHours()).getTime()
    : new Date(a.getFullYear(), a.getMonth(), a.getDate()).getTime()
  for (let i = 0; i < dataPoints.length; i++) {
    const d = new Date(String(dataPoints[i].time || '').replace(' ', 'T'))
    if (isNaN(d.getTime())) continue
    const dKey = granularity === 'HOUR'
      ? new Date(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours()).getTime()
      : new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
    if (dKey === aKey) return i
  }
  return -1
}
