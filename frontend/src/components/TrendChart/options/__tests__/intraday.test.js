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
    const opt = intradayOption(pts(0, 1, 2), { thresholds: { min: 5, max: 35 }, showThresholds: true })
    const lines = opt.series.find((s) => s.markLine)?.markLine?.data ?? []
    expect(lines).toHaveLength(2)
    expect(lines.some((l) => l.yAxis === 35)).toBe(true)
    expect(lines.some((l) => l.yAxis === 5)).toBe(true)
  })
  it('renders the avg line with area fill and no extremes band by default (D5)', () => {
    const opt = intradayOption(pts(0, 1, 2), {})
    const line = opt.series.find((s) => s.type === 'line' && s.name === '均值')
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
    expect(opt.series.find((s) => s.name === '均值').connectNulls).toBe(false)
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
    const data = opt.series.find((s) => s.name === '均值').markPoint?.data ?? []
    expect(data).toHaveLength(1)
    expect(data[0].coord).toEqual(['10-25 00:01', 21])
    expect(data[0].itemStyle.color).toBe('#ffab00')
  })
})
