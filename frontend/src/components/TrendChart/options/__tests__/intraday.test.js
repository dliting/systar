import { describe, it, expect } from 'vitest'
import { intradayOption, insertGaps } from '../intraday'
import { emptyFrameOption } from '../empty'

const pts = (...mins) => mins.map((m) => ({
  t: `2026-10-25 ${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}:00`,
  avg: 20 + m, max: 21 + m, min: 19 + m, open: 20 + m, close: 20 + m, count: 1,
}))

describe('insertGaps', () => {
  it('inserts a null marker across gaps wider than 1.5 buckets', () => {
    const out = insertGaps(pts(0, 1, 10), 60) // 9-minute gap at 60s buckets
    expect(out).toHaveLength(4)
    expect(out[2].avg).toBeNull()
  })
  it('leaves contiguous series untouched', () => {
    expect(insertGaps(pts(0, 1, 2), 60)).toHaveLength(3)
  })
})

describe('intradayOption', () => {
  it('draws threshold markLines from the consumers\' {min,max} contract', () => {
    // TrendStandalone/MonitorTrend build thresholds as {min,max,warnCond}
    // (from metadata minValue/maxValue) — the builder must read the same keys.
    // Values sit inside the data range (19–23) so D4 keeps both lines drawn.
    const opt = intradayOption(pts(0, 1, 2), { thresholds: { min: 20, max: 22 }, showThresholds: true })
    const lines = opt.series.find((s) => s.markLine)?.markLine?.data ?? []
    expect(lines).toHaveLength(2)
    expect(lines.some((l) => l.yAxis === 22)).toBe(true)
    expect(lines.some((l) => l.yAxis === 20)).toBe(true)
  })
  it('omits thresholds farther than 2x the span and keeps the y-range on data (D4)', () => {
    const opt = intradayOption(pts(0, 1, 2), { thresholds: { min: 5, max: 35 } })
    expect(opt.series.find((s) => s.markLine)?.markLine?.data ?? []).toHaveLength(0)
    expect(opt.yAxis[0].max).toBeLessThan(30)
  })
  it('extends the y-range for a near-out threshold and draws it (D4)', () => {
    const opt = intradayOption(pts(0, 1, 2), { thresholds: { max: 26 } })
    expect(opt.series.find((s) => s.name === '实测值').markLine.data.some((l) => l.yAxis === 26)).toBe(true)
    expect(opt.yAxis[0].max).toBeGreaterThanOrEqual(26)
  })
  it('hides the legend for the single avg series (D3)', () => {
    expect(intradayOption(pts(0, 1, 2), {}).legend.show).toBe(false)
  })
  it('shows the legend when the 极值带 overlay is on (D3)', () => {
    expect(intradayOption(pts(0, 1, 2), { showExtremes: true }).legend.show).toBe(true)
  })
  it('renders the avg line with area fill and no extremes band by default (D5)', () => {
    const opt = intradayOption(pts(0, 1, 2), {})
    const line = opt.series.find((s) => s.type === 'line' && s.name === '实测值')
    expect(line).toBeTruthy()
    expect(opt.series.some((s) => s.stack === 'band')).toBe(false)
  })
  it('adds the stacked 极值带 pair when showExtremes is on', () => {
    const opt = intradayOption(pts(0, 1, 2), { showExtremes: true })
    const band = opt.series.filter((s) => s.stack === 'band')
    expect(band).toHaveLength(2)
    expect(band.some((s) => s.name === '极值带')).toBe(true)
  })
  it('connects nulls off so gaps break the line', () => {
    const opt = intradayOption(pts(0, 1, 10), { bucketSeconds: 60 })
    expect(opt.series.find((s) => s.name === '实测值').connectNulls).toBe(false)
  })
  // A lone sample draws no line segment — with symbols off the operator sees
  // an empty chart (reported on the 分时 view with a fresh dev DB).
  it('shows point symbols on sparse data so every collected sample stays visible', () => {
    const opt = intradayOption(pts(0), {})
    expect(opt.series.find((s) => s.name === '实测值').showSymbol).toBe(true)
    // The 极值带 boundary lines share the same root cause — with one sample
    // their dashed/solid segments draw nothing either. The stacked 极值带
    // series itself stays symbol-less (its symbol would sit on the band top).
    const ex = intradayOption(pts(0), { showExtremes: true })
    expect(ex.series.find((s) => s.name === '最高值').showSymbol).toBe(true)
    expect(ex.series.find((s) => s.name === '最低值').showSymbol).toBe(true)
    expect(ex.series.find((s) => s.name === '极值带').showSymbol).toBe(false)
  })
  it('hides point symbols on dense data to keep the trend line readable', () => {
    const many = pts(...Array.from({ length: 301 }, (_, i) => i))
    const opt = intradayOption(many, { detectIntervalSeconds: 60, showExtremes: true })
    expect(opt.series.find((s) => s.name === '实测值').showSymbol).toBe(false)
    expect(opt.series.find((s) => s.name === '最高值').showSymbol).toBe(false)
  })
  // The default state shows the finest-granularity actual samples — labeling
  // them 均值 misrepresents the data (user requirement 2026-10-25: 不是均值).
  // Only BUCKETED buckets are averages.
  it('names the series 实测值 in RAW mode and 均值 in BUCKETED mode', () => {
    expect(intradayOption(pts(0, 1, 2), { mode: 'RAW', detectIntervalSeconds: 60 }).series[0].name).toBe('实测值')
    expect(intradayOption(pts(0, 1, 2), { mode: 'BUCKETED', bucketSeconds: 60 }).series[0].name).toBe('均值')
  })
  it('tooltips the value under the mode-appropriate name', () => {
    const raw = intradayOption(pts(0, 1, 2), { mode: 'RAW', detectIntervalSeconds: 60, unit: '℃' })
    expect(raw.tooltip.formatter([{ seriesName: '实测值', name: '10-25 00:00', value: 20, dataIndex: 0 }]))
      .toContain('实测值 20℃')
    const buck = intradayOption(pts(0, 1, 2), { mode: 'BUCKETED', bucketSeconds: 60, unit: '℃' })
    expect(buck.tooltip.formatter([{ seriesName: '均值', name: '10-25 00:00', value: 20, dataIndex: 0 }]))
      .toContain('均值 20℃')
  })
  it('empty frame still renders axes without data', () => {
    const opt = emptyFrameOption('INTRADAY', '℃', 10)
    expect(opt.xAxis).toBeTruthy()
    expect(opt.series.every((s) => (s.data ?? []).length === 0)).toBe(true)
  })

  // Analysis API shape: { time, actual, expected, deviation, severity }
  // (normalized in src/api/iot/analysis.js) — the builder must read those
  // keys, and an intraday anomaly belongs to the most recent sample at or
  // before its time.
  it('renders anomaly markers from the analysis API shape', () => {
    const anomalies = [{ time: '2026-10-25 00:01:30', actual: 21, expected: 20, deviation: 2.5, severity: 'low' }]
    const opt = intradayOption(pts(0, 1, 2), { anomalyPoints: anomalies, showAnomalies: true })
    const data = opt.series.find((s) => s.name === '实测值').markPoint?.data ?? []
    expect(data).toHaveLength(1)
    expect(data[0].coord).toEqual(['10-25 00:01', 21])
    expect(data[0].itemStyle.color).toBe('#ffab00')
  })
})
