import { describe, it, expect } from 'vitest'
import { candlestickOption } from '../candlestick'
import { RISING, FALLING } from '../../theme'

const POINTS = [
  { t: '2026-10-24 00:00:00', avg: 20, max: 30, min: 10, open: 10, close: 30, count: 5 },
  { t: '2026-10-25 00:00:00', avg: 25, max: 28, min: 22, open: 28, close: 22, count: 3 },
]
const csOf = opt => opt.series.find(s => s.type === 'candlestick')

describe('candlestickOption', () => {
  it('degrades legacy null open/close to a flat cross at avg (Review Focus 1)', () => {
    const legacy = [{ t: '2026-10-24 00:00:00', avg: 7, max: 9, min: 5, open: null, close: null, count: 2 }]
    expect(csOf(candlestickOption(legacy, {})).data[0]).toEqual([7, 7, 5, 9])
  })

  it('maps rising to amber and falling to cyan (D7)', () => {
    const cs = csOf(candlestickOption(POINTS, {}))
    expect(cs.itemStyle.color).toBe(RISING)
    expect(cs.itemStyle.color0).toBe(FALLING)
  })

  it('tooltip speaks SCADA, never finance', () => {
    const opt = candlestickOption(POINTS, { unit: '℃' })
    const html = opt.tooltip.formatter([{ name: '10-24', value: [10, 30, 10, 30], dataIndex: 0 }])
    for (const term of ['期初值', '期末值', '最高值', '最低值', '波动幅度', '采样数', '上升']) {
      expect(html).toContain(term)
    }
    for (const term of ['开盘', '收盘', '振幅', '成交量']) {
      expect(html).not.toContain(term)
    }
  })

  it('tooltip reads OHLC from the point, not params.value (ECharts prepends the category dim)', () => {
    // Real ECharts passes value = [categoryIndex, open, close, min, max] for
    // candlestick on a category axis — the E2E snapshot caught the formatter
    // misreading it (期初值 2℃ = the dataIndex).
    const opt = candlestickOption(POINTS, { unit: '℃' })
    const html = opt.tooltip.formatter([
      { name: '10-24', seriesType: 'candlestick', dataIndex: 0, value: [0, 10, 30, 10, 30] },
    ])
    expect(html).toContain('期初值 10℃')
    expect(html).toContain('期末值 30℃')
    expect(html).toContain('最高值 30℃')
    expect(html).toContain('最低值 10℃')
    expect(html).toContain('波动幅度 20℃')
  })

  it('draws threshold markLines from the consumers\' {min,max} contract', () => {
    // TrendStandalone/MonitorTrend build thresholds as {min,max,warnCond}
    // (from metadata minValue/maxValue) — the builder must read the same keys.
    const cs = csOf(candlestickOption(POINTS, { thresholds: { min: 5, max: 35 }, showThresholds: true }))
    const lines = cs.markLine?.data ?? []
    expect(lines).toHaveLength(2)
    expect(lines.some((l) => l.yAxis === 35)).toBe(true)
    expect(lines.some((l) => l.yAxis === 5)).toBe(true)
  })

  it('sample sub-chart is off by default (D4) and adds a second grid when on', () => {
    expect(candlestickOption(POINTS, {}).series.some(s => s.type === 'bar')).toBe(false)
    const opt = candlestickOption(POINTS, { showSamples: true })
    expect(opt.grid).toHaveLength(2)
    expect(opt.series.some(s => s.type === 'bar' && s.yAxisIndex === 1)).toBe(true)
  })

  it('adds named MA overlay lines only for provided arrays', () => {
    const opt = candlestickOption(POINTS, { ma: { ma5: [null, 22.5], ma10: null, ma20: null } })
    const lines = opt.series.filter(s => s.type === 'line')
    expect(lines).toHaveLength(1)
    expect(lines[0].name).toBe('5期平均')
  })

  it('omits MA series whose values are all null — no dead legend entries (D3)', () => {
    // Fewer points than the MA period → every value null. The series would
    // draw nothing yet still claim a (grayed) legend slot.
    const ma = { ma5: [null, null], ma10: [null, null], ma20: [null, null] }
    const opt = candlestickOption(POINTS, { style: 'line', ma })
    expect(opt.series).toHaveLength(1)
    expect(opt.legend.show).toBe(false)
  })

  it('pins inside+slider zoom with 100ms throttle for smooth pan/zoom', () => {
    const opt = candlestickOption(POINTS, {})
    expect(opt.dataZoom.some(z => z.type === 'inside' && z.throttle === 100)).toBe(true)
    expect(opt.dataZoom.some(z => z.type === 'slider')).toBe(true)
  })

  // The analysis API client (src/api/iot/analysis.js) normalizes backend
  // AnomalyPoint records to { time, actual, expected, deviation, severity } —
  // the builder must read those keys (final review Important #1: it read
  // a.t / a.value and silently dropped every marker).
  describe('anomaly markers (analysis API shape)', () => {
    it('renders a DAY marker at the matching candle with severity color and σ label', () => {
      const anomalies = [{ time: '2026-10-24 14:30:00', actual: 28, expected: 24, deviation: 3.2, severity: 'high' }]
      const cs = csOf(candlestickOption(POINTS, { granularity: 'DAY', anomalyPoints: anomalies, showAnomalies: true }))
      const data = cs.markPoint?.data ?? []
      expect(data).toHaveLength(1)
      expect(data[0].coord).toEqual(['10-24', 28])
      expect(data[0].itemStyle.color).toBe('#ff5252')
      expect(data[0].value).toBe('3.2σ')
    })

    it('matches an HOUR anomaly to its hour bucket', () => {
      const hourPts = [
        { t: '2026-10-24 13:00:00', avg: 20, max: 22, min: 18, open: 20, close: 20, count: 5 },
        { t: '2026-10-24 14:00:00', avg: 25, max: 27, min: 23, open: 25, close: 25, count: 5 },
      ]
      const anomalies = [{ time: '2026-10-24 14:20:00', actual: 27, expected: 24, deviation: 2.1, severity: 'low' }]
      const cs = csOf(candlestickOption(hourPts, { granularity: 'HOUR', anomalyPoints: anomalies, showAnomalies: true }))
      const data = cs.markPoint?.data ?? []
      expect(data).toHaveLength(1)
      expect(data[0].coord).toEqual(['10-24 14:00', 27])
      expect(data[0].itemStyle.color).toBe('#ffab00')
    })

    it('skips anomalies outside the rendered window', () => {
      const anomalies = [{ time: '2026-11-02 09:00:00', actual: 30, expected: 24, deviation: 4.0, severity: 'high' }]
      const cs = csOf(candlestickOption(POINTS, { granularity: 'DAY', anomalyPoints: anomalies, showAnomalies: true }))
      expect(cs.markPoint?.data ?? []).toHaveLength(0)
    })
  })

  // Spec D1: every OHLC granularity defaults to a line (avg) render; the
  // candlestick form becomes an opt-in layer. Same scaffolding either way.
  describe('candlestickOption style', () => {
    it("style:'line' renders the avg as a line series", () => {
      const opt = candlestickOption(POINTS, { style: 'line' })
      expect(opt.series[0].type).toBe('line')
      expect(opt.series[0].data).toEqual([20, 25])
    })
    it("style:'line' keeps threshold markLines and anomaly markPoints on the main series", () => {
      const anomalies = [{ time: '2026-10-24 12:00:00', actual: 30, expected: 20, deviation: 3, severity: 'high' }]
      const opt = candlestickOption(POINTS, { style: 'line', thresholds: { min: 5, max: 35 }, anomalyPoints: anomalies, showAnomalies: true })
      expect(opt.series[0].markLine.data).toHaveLength(2)
      expect(opt.series[0].markPoint.data).toHaveLength(1)
    })
    it("style:'line' keeps MA overlays", () => {
      const ma = { ma5: [null, 22], ma10: [null, 23], ma20: [null, 24] }
      const opt = candlestickOption(POINTS, { style: 'line', ma })
      expect(opt.series.map((s) => s.name)).toContain('5期平均')
    })
    it("style:'line' degrades null open/close to avg without NaN (Review Focus)", () => {
      const legacy = [{ t: '2026-10-24 00:00:00', avg: 20, max: 30, min: 10, open: null, close: null, count: 5 }]
      const opt = candlestickOption(legacy, { style: 'line' })
      expect(opt.series[0].data).toEqual([20])
    })
    it('hides the legend for a single-series chart (D3)', () => {
      expect(candlestickOption(POINTS, {}).legend.show).toBe(false)
    })
    it('shows the legend once overlays exist (D3)', () => {
      const ma = { ma5: [null, 22], ma10: [null, 23], ma20: [null, 24] }
      expect(candlestickOption(POINTS, { ma }).legend.show).toBe(true)
    })
    it('omits a far threshold line and keeps the y-range on data (D4)', () => {
      const opt = candlestickOption(POINTS, { thresholds: { min: -200, max: 200 } })
      expect(opt.series[0].markLine.data).toHaveLength(0)
      expect(opt.yAxis[0].max).toBeLessThan(40)
    })
    it('extends the y-range for a near threshold and draws it (D4)', () => {
      const opt = candlestickOption(POINTS, { thresholds: { max: 35 } })
      expect(opt.series[0].markLine.data.some((l) => l.yAxis === 35)).toBe(true)
      expect(opt.yAxis[0].max).toBeGreaterThanOrEqual(35)
    })
  })
})
