/**
 * OHLC candlestick option (spec F2). Candles degrade to a flat cross at avg
 * when open/close are null (legacy stats rows, D3). Terminology is SCADA:
 * 期初值/期末值/最高值/最低值/波动幅度 — never finance-market wording.
 */
import {
  RISING, FALLING, ALARM, NORMAL, MA_COLORS,
  darkTooltip, axisStyle, labelFor, perfSwitches,
} from '../theme'
import { anomalyMarkerData } from './anomalies'

const fmt = (v) => (v == null ? '--' : Number(v.toFixed(2)).toString())

export function candlestickOption(points, opts = {}) {
  const {
    unit = '', granularity = 'DAY', ma = null, showSamples = false,
    showThresholds = true, showAnomalies = true, thresholds = null,
    anomalyPoints = [], compact = false, infiniteMode = false,
  } = opts

  const labels = points.map((p) => labelFor(p.t, granularity))
  const candles = points.map((p) => [
    p.open ?? p.avg, p.close ?? p.avg, p.min ?? p.avg, p.max ?? p.avg,
  ])
  const perf = perfSwitches(points.length)

  let lo = Infinity, hi = -Infinity
  for (const p of points) {
    if (p.min != null && p.min < lo) lo = p.min
    if (p.max != null && p.max > hi) hi = p.max
  }
  const pad = (hi - lo) * 0.05 || Math.abs(hi || 1) * 0.05

  const series = [{
    name: '趋势',
    type: 'candlestick',
    data: candles,
    itemStyle: { color: RISING, color0: FALLING, borderColor: RISING, borderColor0: FALLING },
    ...(thresholds && showThresholds ? { markLine: thresholdMarkLines(thresholds) } : {}),
    ...(anomalyPoints?.length && showAnomalies
      ? { markPoint: { symbol: 'circle', symbolSize: 24,
          itemStyle: { borderColor: '#1a1a2e', borderWidth: 1 },
          data: anomalyMarkerData(anomalyPoints, points, granularity, (t) => labelFor(t, granularity)) } }
      : {}),
  }]
  if (ma) {
    for (const [key, name] of [['ma5', '5期平均'], ['ma10', '10期平均'], ['ma20', '20期平均']]) {
      if (Array.isArray(ma[key])) {
        series.push({
          name, type: 'line', data: ma[key], smooth: true, showSymbol: false,
          connectNulls: false, sampling: perf.sampling,
          lineStyle: { width: 1.2, color: MA_COLORS[series.length - 1] },
        })
      }
    }
  }
  if (showSamples) {
    series.push({
      name: '采样数', type: 'bar', yAxisIndex: 1,
      data: points.map((p) => p.count ?? 0),
      itemStyle: { color: 'rgba(0, 212, 255, 0.45)' }, barMaxWidth: 12,
    })
  }

  const grids = showSamples
    ? [{ left: 56, right: 16, top: 40, height: '55%' },
       { left: 56, right: 16, top: '72%', height: '16%' }]
    : [{ left: 56, right: 16, top: 40, bottom: compact ? 36 : 56 }]
  const axes = axisStyle()

  return {
    animation: perf.animation,
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'cross' }, ...darkTooltip(),
      formatter: (params) => candleTooltip(params, points, unit),
    },
    legend: { show: !compact, top: 8, textStyle: { color: '#8b9dc3', fontSize: 11 },
      data: series.filter((s) => s.type !== 'candlestick').map((s) => s.name) },
    grid: grids,
    xAxis: [{ type: 'category', data: labels, boundaryGap: true, ...axes,
      axisLabel: { ...axes.axisLabel, hideOverlap: true } }],
    yAxis: [
      { type: 'value', scale: true, min: Number.isFinite(lo) ? lo - pad : undefined,
        max: Number.isFinite(hi) ? hi + pad : undefined, ...axes,
        axisLabel: { ...axes.axisLabel, formatter: (v) => fmt(v) } },
      ...(showSamples ? [{ type: 'value', gridIndex: 1, ...axes, splitNumber: 2,
        axisLabel: { ...axes.axisLabel, fontSize: 10 } }] : []),
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: grids.map((_, i) => i), throttle: 100,
        zoomOnMouseWheel: true, moveOnMouseMove: true },
      ...(compact ? [] : [{ type: 'slider', xAxisIndex: grids.map((_, i) => i),
        height: 18, bottom: 8, borderColor: 'rgba(0,212,255,0.25)',
        fillerColor: 'rgba(0,212,255,0.12)', handleStyle: { color: '#00d4ff' },
        textStyle: { color: '#8b9dc3', fontSize: 10 } }]),
    ],
    series,
  }
}

function candleTooltip(params, points, unit) {
  const p = Array.isArray(params) ? params.find((x) => x.seriesType === 'candlestick') ?? params[0] : params
  // Read OHLC from the source point, not params.value: ECharts prepends the
  // category dimension to params.value for candlestick ([idx, o, c, lo, hi]),
  // which silently misaligns a 4-way destructure (caught by the E2E suite).
  const point = points[p.dataIndex] || {}
  const o = point.open ?? point.avg, c = point.close ?? point.avg
  const lo = point.min ?? point.avg, hi = point.max ?? point.avg
  const rising = c >= o
  const color = rising ? RISING : FALLING
  const lines = [
    `<b>${p.name}</b>`,
    `<span style="color:${color}">${rising ? '上升' : '下降'}</span> ${fmt(Math.abs(c - o))}${unit}`,
    `期初值 ${fmt(o)}${unit}`,
    `期末值 ${fmt(c)}${unit}`,
    `最高值 ${fmt(hi)}${unit}`,
    `最低值 ${fmt(lo)}${unit}`,
    `波动幅度 ${fmt(hi - lo)}${unit}`,
  ]
  if (point.count != null) lines.push(`采样数 ${point.count.toLocaleString()}`)
  return lines.join('<br/>')
}

function thresholdMarkLines(thresholds) {
  const data = []
  if (thresholds.max != null) data.push({ yAxis: thresholds.max, name: '上限' })
  if (thresholds.min != null) data.push({ yAxis: thresholds.min, name: '下限' })
  return { silent: true, symbol: 'none',
    lineStyle: { color: ALARM, type: 'dashed', width: 1 },
    label: { color: ALARM, fontSize: 10 }, data }
}
