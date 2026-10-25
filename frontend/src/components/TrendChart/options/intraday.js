/**
 * Intraday (time-share) option (spec F1): value line + area, gap-aware, with
 * an optional 极值带 (min/max envelope) toggle. RAW mode renders the actual
 * measured samples (series 实测值); BUCKETED data arrives pre-aggregated with
 * bucketSeconds set and is an average per bucket (series 均值).
 */
import {
  CYAN, ALARM, darkTooltip, axisStyle, gridStyle, cyanGradient,
  labelFor, perfSwitches,
} from '../theme'
import { anomalyMarkerData } from './anomalies'
import { valueRange } from './yRange'

const fmt = (v) => (v == null ? '--' : Number(v.toFixed(2)).toString())

export function insertGaps(points, bucketSeconds) {
  if (!bucketSeconds || points.length < 2) return points
  const out = []
  for (let i = 0; i < points.length; i++) {
    if (i > 0) {
      const a = new Date(String(points[i - 1].t).replace(' ', 'T')).getTime()
      const b = new Date(String(points[i].t).replace(' ', 'T')).getTime()
      if (Number.isFinite(a) && Number.isFinite(b) && b - a > bucketSeconds * 1500) {
        out.push({ t: new Date((a + b) / 2).toISOString().slice(0, 19).replace('T', ' '),
          avg: null, max: null, min: null, open: null, close: null, count: 0 })
      }
    }
    out.push(points[i])
  }
  return out
}

export function intradayOption(points, opts = {}) {
  const {
    unit = '', mode = 'RAW', bucketSeconds = null, showExtremes = false,
    showThresholds = true, showAnomalies = true, thresholds = null,
    anomalyPoints = [], compact = false, infiniteMode = false,
    detectIntervalSeconds = 10,
  } = opts

  const gapSeconds = bucketSeconds ?? (mode === 'RAW' ? detectIntervalSeconds : 60)
  const data = insertGaps(points, gapSeconds)
  const labels = data.map((p) => labelFor(p.t, 'HOUR'))
  const perf = perfSwitches(data.length)
  // D4: range + threshold visibility from the shared helper (gap markers in
  // `data` carry null min/max and are skipped there, so use the raw points).
  const range = valueRange(points, thresholds, showThresholds)
  const pad = range.lo != null && range.hi != null
    ? (range.hi - range.lo) * 0.05 || Math.abs(range.hi || 1) * 0.05
    : 0

  // RAW points carry the actual measured value (open=close=avg=max=min,
  // count=1) — calling it 均值 misrepresents it (user requirement 2026-10-25:
  // 缺省显示最细粒度实测值，不是均值). Only BUCKETED buckets are averages.
  const valueName = mode === 'RAW' ? '实测值' : '均值'
  const series = [{
    name: valueName, type: 'line', data: data.map((p) => p.avg),
    // Sparse data (fresh dev DB, long comms outage) draws no line segment —
    // symbols keep every collected sample visible (same threshold as the
    // candlestick line series).
    showSymbol: data.length <= 300, connectNulls: false, sampling: perf.sampling,
    lineStyle: { width: 1.5, color: CYAN }, areaStyle: { color: cyanGradient() },
    ...(thresholds && showThresholds ? { markLine: thresholdMarkLines(thresholds, range) } : {}),
    ...(anomalyPoints?.length && showAnomalies
      ? { markPoint: { symbol: 'circle', symbolSize: 24,
          itemStyle: { borderColor: '#1a1a2e', borderWidth: 1 },
          data: anomalyMarkerData(anomalyPoints, points, 'INTRADAY', (t) => labelFor(t, 'HOUR')) } }
      : {}),
  }]
  if (showExtremes) {
    series.push(
      { name: '最低值', type: 'line', stack: 'band', data: data.map((p) => p.min),
        showSymbol: data.length <= 300, connectNulls: false, lineStyle: { opacity: 0 },
        areaStyle: { opacity: 0 }, z: 1 },
      { name: '极值带', type: 'line', stack: 'band',
        data: data.map((p) => (p.max != null && p.min != null ? p.max - p.min : null)),
        showSymbol: false, connectNulls: false, lineStyle: { opacity: 0 },
        areaStyle: { color: 'rgba(0, 212, 255, 0.12)' }, z: 1 },
      { name: '最高值', type: 'line', data: data.map((p) => p.max),
        showSymbol: data.length <= 300, connectNulls: false,
        lineStyle: { width: 1, color: 'rgba(0, 212, 255, 0.5)', type: 'dashed' }, z: 2 },
    )
  }

  return {
    animation: perf.animation,
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'cross' }, ...darkTooltip(),
      formatter: (params) => intradayTooltip(params, data, unit, valueName),
    },
    // D3: legend only earns space with more than one visible series (the
    // 极值带 trio); the lone avg line is already named by the panel title.
    legend: { show: !compact && series.length > 1, top: 8,
      textStyle: { color: '#8b9dc3', fontSize: 11 } },
    grid: { ...gridStyle(), bottom: compact ? 36 : 56 },
    xAxis: [{ type: 'category', data: labels, boundaryGap: false, ...axisStyle(),
      axisLabel: { ...axisStyle().axisLabel, hideOverlap: true } }],
    yAxis: [{ type: 'value', scale: true,
      min: range.lo != null ? range.lo - pad : undefined,
      max: range.hi != null ? range.hi + pad : undefined, ...axisStyle(),
      axisLabel: { ...axisStyle().axisLabel, formatter: (v) => fmt(v) } }],
    dataZoom: [
      { type: 'inside', throttle: 100, zoomOnMouseWheel: true, moveOnMouseMove: true },
      ...(compact ? [] : [{ type: 'slider', height: 18, bottom: 8,
        borderColor: 'rgba(0,212,255,0.25)', fillerColor: 'rgba(0,212,255,0.12)',
        handleStyle: { color: '#00d4ff' }, textStyle: { color: '#8b9dc3', fontSize: 10 } }]),
    ],
    series,
  }
}

function intradayTooltip(params, data, unit, valueName) {
  const p = Array.isArray(params) ? params.find((x) => x.seriesName === valueName) ?? params[0] : params
  const point = data[p.dataIndex] || {}
  const lines = [`<b>${p.name}</b>`, `${valueName} ${fmt(p.value)}${unit}`]
  if (point.max != null || point.min != null) {
    lines.push(`最高值 ${fmt(point.max)}${unit}`, `最低值 ${fmt(point.min)}${unit}`)
  }
  if (point.count != null && point.count > 1) lines.push(`采样数 ${point.count.toLocaleString()}`)
  return lines.join('<br/>')
}

function thresholdMarkLines(thresholds, range) {
  const data = []
  // D4: only valueRange-visible thresholds draw a line (see yRange.js).
  if (thresholds.max != null && range.showMax) data.push({ yAxis: thresholds.max, name: '上限' })
  if (thresholds.min != null && range.showMin) data.push({ yAxis: thresholds.min, name: '下限' })
  return { silent: true, symbol: 'none',
    lineStyle: { color: ALARM, type: 'dashed', width: 1 },
    label: { color: ALARM, fontSize: 10 }, data }
}
