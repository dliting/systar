import { describe, it, expect } from 'vitest'
import { emptyFrameOption, multiSeriesOption } from '../trendOptions'
import { MA_COLORS } from '@/components/TrendChart/theme'

/**
 * Build n synthetic trend points in the unified TrendPointVO shape
 * ({ t, avg, max, min, open, close, count }) starting at start, stepping
 * stepHours apart. Local-time strings match backend LocalDateTime
 * serialization (consumed via Date parse, also local) so labelFor produces
 * predictable labels regardless of test runner timezone.
 */
const makePoints = (n, start = '2026-06-01 00:00:00', stepHours = 1) => {
  const out = []
  const t0  = new Date(start.replace(' ', 'T'))
  for (let i = 0; i < n; i++) {
    const ts = new Date(t0.getTime() + i * stepHours * 3_600_000)
    const pad = n2 => String(n2).padStart(2, '0')
    const local = `${ts.getFullYear()}-${pad(ts.getMonth() + 1)}-${pad(ts.getDate())} ${pad(ts.getHours())}:${pad(ts.getMinutes())}:${pad(ts.getSeconds())}`
    const avg = 20 + i * 0.1
    out.push({
      t    : local,
      avg,
      max  : 25 + i * 0.2,
      min  : 15 + i * 0.05,
      open : avg,
      close: avg,
      count: 60,
    })
  }
  return out
}

describe('emptyFrameOption', () => {
  it('renders empty grid with placeholder times', () => {
    const opt = emptyFrameOption('HOUR', '°C', 10)
    expect(opt.xAxis.data.length).toBeGreaterThan(0)
    expect(opt.series).toEqual([])
  })

  it('handles MONTH granularity without throwing', () => {
    expect(() => emptyFrameOption('MONTH', '', 10)).not.toThrow()
  })
})

describe('multiSeriesOption (comparison chart, unified point contract)', () => {
  const makeSeries = (monitorId, unit, name, count = 5) => ({
    monitorId,
    name,
    unit,
    color   : null,  // assigned by caller
    points: makePoints(count, '2026-06-01 00:00:00', 1).map(p => ({
      ...p,
      avg  : p.avg + monitorId,   // offset each series so curves don't overlap
      max  : p.max + monitorId,
      min  : p.min + monitorId,
      open : p.open + monitorId,
      close: p.close + monitorId,
    })),
    ma5: [],
    ma10: [],
    ma20: [],
    thresholds: null,
    anomalies: [],
  })

  describe('single unit (single Y axis)', () => {
    it('renders all series on a single Y axis', () => {
      const series = [
        { ...makeSeries(1, '°C', 'temp-1'), color: '#00d4ff' },
        { ...makeSeries(2, '°C', 'temp-2'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      // Single Y axis
      expect(opt.yAxis).toHaveLength(1)
      expect(opt.yAxis[0].name).toBe('°C')

      // One avg line per monitor (MA overlays only when carried)
      expect(opt.series.length).toBeGreaterThanOrEqual(2)

      // All series reference the same yAxisIndex
      for (const s of opt.series) {
        expect(s.yAxisIndex).toBe(0)
      }
    })

    it('uses each series color', () => {
      const series = [
        { ...makeSeries(1, '°C', 'temp-1'), color: '#00d4ff' },
        { ...makeSeries(2, '°C', 'temp-2'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      const colors = opt.series.map(s => s.lineStyle.color)
      expect(colors).toContain('#00d4ff')
      expect(colors).toContain('#ff5252')
    })

    it('tooltip groups values by monitor name', () => {
      const series = [
        { ...makeSeries(1, '°C', 'temp-1'), color: '#00d4ff' },
        { ...makeSeries(2, '°C', 'temp-2'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      const html = opt.tooltip.formatter([
        { axisValue: '06-01 00:00', seriesName: 'temp-1', value: 25, color: '#00d4ff' },
        { axisValue: '06-01 00:00', seriesName: 'temp-2', value: 30, color: '#ff5252' },
      ])
      expect(html).toContain('temp-1')
      expect(html).toContain('temp-2')
      expect(html).toContain('25')
      expect(html).toContain('30')
    })
  })

  describe('two units (dual Y axis)', () => {
    it('renders dual Y axis with both unit names', () => {
      const series = [
        { ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' },
        { ...makeSeries(2, '%', 'humidity'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      expect(opt.yAxis).toHaveLength(2)
      const names = opt.yAxis.map(y => y.name)
      expect(names).toContain('°C')
      expect(names).toContain('%')
    })

    it('assigns series to correct yAxisIndex by unit group', () => {
      const series = [
        { ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' },
        { ...makeSeries(2, '%', 'humidity'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      // °C series on yAxis[0], % series on yAxis[1]
      const tempSeries = opt.series.find(s => s.name.includes('temp'))
      const humiditySeries = opt.series.find(s => s.name.includes('humidity'))
      expect(tempSeries.yAxisIndex).toBe(0)
      expect(humiditySeries.yAxisIndex).toBe(1)
    })
  })

  describe('moving-average overlays (client-computed ma5/ma10/ma20)', () => {
    it('renders 5/10/20期平均 lines with MA_COLORS when a single series carries them', () => {
      const base = makePoints(20)
      const avgs = base.map(p => p.avg)
      const ma = (period) => avgs.map((_, i) => (i >= period - 1 ? avgs.slice(i - period + 1, i + 1).reduce((a, b) => a + b, 0) / period : null))
      const series = [{
        ...makeSeries(1, '°C', 'temp', 20),
        color: '#00d4ff',
        ma5 : ma(5),
        ma10: ma(10),
        ma20: ma(20),
      }]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      const names = opt.series.map(s => s.name)
      expect(names).toContain('5期平均')
      expect(names).toContain('10期平均')
      expect(names).toContain('20期平均')

      const ma5 = opt.series.find(s => s.name === '5期平均')
      expect(ma5.lineStyle.color).toBe(MA_COLORS[0])
      expect(ma5.data).toHaveLength(20)
    })

    it('skips an all-null MA array (too few points for that period)', () => {
      const series = [{
        ...makeSeries(1, '°C', 'temp', 5),
        color: '#00d4ff',
        ma5 : [null, null, null, null, 20.2],
        ma10: [null, null, null, null, null],
      }]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      const names = opt.series.map(s => s.name)
      expect(names).toContain('5期平均')
      expect(names).not.toContain('10期平均')
    })

    it('prefixes MA legend names with the monitor name when multiple series carry MAs', () => {
      const ma5 = [null, null, null, null, 20.2]
      const series = [
        { ...makeSeries(1, '°C', 'temp-1'), color: '#00d4ff', ma5 },
        { ...makeSeries(2, '°C', 'temp-2'), color: '#ff5252', ma5 },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      const names = opt.series.map(s => s.name)
      expect(names).toContain('temp-1 5期平均')
      expect(names).toContain('temp-2 5期平均')
      expect(names).not.toContain('5期平均')
    })
  })

  describe('thresholds + anomalies on the avg series', () => {
    it('renders markLine for both min and max when thresholds present and showThresholds=true', () => {
      const series = [{ ...makeSeries(1, '°C', 'temp'), color: '#00d4ff', thresholds: { min: 10, max: 30 } }]
      const opt = multiSeriesOption(series, 'HOUR', 7, true, false)

      const avgSeries = opt.series.find(s => s.name === 'temp')
      expect(avgSeries.markLine.data).toHaveLength(2)
      const yAxisValues = avgSeries.markLine.data.map(d => d.yAxis).sort((a, b) => a - b)
      expect(yAxisValues).toEqual([10, 30])
    })

    it('renders markPoint for an anomaly that falls inside a data bucket (HOUR)', () => {
      // points have bucket starts at hour boundaries; anomaly is mid-hour and
      // must match the bucket at 03:00 via the unified `t` field
      const series = [{
        ...makeSeries(1, '°C', 'temp', 24),
        color: '#00d4ff',
        anomalies: [
          { time: '2026-06-01 03:15:00', actual: 50, expected: 20, deviation: 2.5, severity: 'medium' },
        ],
      }]
      const opt = multiSeriesOption(series, 'HOUR', 7, false, true)

      const avgSeries = opt.series.find(s => s.name === 'temp')
      expect(avgSeries.markPoint.data).toHaveLength(1)
      const mp = avgSeries.markPoint.data[0]
      // coord[0] must match an xAxis.data entry literally (ECharts category axis)
      expect(opt.xAxis.data).toContain(mp.coord[0])
      expect(mp.coord[0]).toBe(opt.xAxis.data[3])
      expect(mp.value).toContain('2.5')
    })

    it('omits markPoint when showAnomalies=false', () => {
      const series = [{
        ...makeSeries(1, '°C', 'temp'),
        color: '#00d4ff',
        anomalies: [{ time: '2026-06-01 03:15:00', actual: 50, expected: 20, deviation: 3, severity: 'high' }],
      }]
      const opt = multiSeriesOption(series, 'HOUR', 7, true, false)
      const avgSeries = opt.series.find(s => s.name === 'temp')
      expect((avgSeries.markPoint?.data || [])).toHaveLength(0)
    })
  })

  describe('dataZoom present in non-compact', () => {
    it('includes inside + slider dataZoom', () => {
      const series = [{ ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' }]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      expect(opt.dataZoom).toBeDefined()
      expect(opt.dataZoom.map(d => d.type).sort()).toEqual(['inside', 'slider'])
    })
  })

  describe('xAxis labelFor formatting', () => {
    it('uses "MM-DD HH:mm" for HOUR granularity', () => {
      const series = [{ ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' }]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      expect(opt.xAxis.data[0]).toBe('06-01 00:00')
    })

    it('uses MM-DD format for DAY granularity', () => {
      const series = [{
        ...makeSeries(1, '°C', 'temp'),
        points: makePoints(5, '2026-06-01 00:00:00', 24),
        color: '#00d4ff',
      }]
      const opt = multiSeriesOption(series, 'DAY', 30)
      expect(opt.xAxis.data[0]).toBe('06-01')
    })

    it('uses YYYY-MM format for MONTH granularity', () => {
      const series = [{
        ...makeSeries(1, '°C', 'temp'),
        points: makePoints(5, '2026-06-01 00:00:00', 24 * 30),
        color: '#00d4ff',
      }]
      const opt = multiSeriesOption(series, 'MONTH', 500)
      expect(opt.xAxis.data[0]).toBe('2026-06')
    })
  })

  describe('empty / edge cases', () => {
    it('handles empty series array', () => {
      const opt = multiSeriesOption([], 'HOUR', 7)
      expect(opt.series).toEqual([])
    })

    it('handles single series (one unit, one monitor)', () => {
      const series = [{ ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' }]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      expect(opt.yAxis).toHaveLength(1)
      expect(opt.series.length).toBeGreaterThanOrEqual(1)
    })

    it('handles series with null unit (collapsed into 无单位 group)', () => {
      const series = [
        { ...makeSeries(1, null, 'probe-1'), color: '#00d4ff' },
        { ...makeSeries(2, null, 'probe-2'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      // Single Y axis (both in '(无单位)' group)
      expect(opt.yAxis).toHaveLength(1)
    })
  })
})
