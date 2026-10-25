import { describe, it, expect } from 'vitest'
import { smallMultiplesOption } from '../smallMultiples'
import { MA_COLORS } from '@/components/TrendChart/theme'

const makePoints = (n, start, stepHours = 1, offset = 0) => {
  const out = []
  const t0 = new Date(start.replace(' ', 'T')).getTime()
  for (let i = 0; i < n; i++) {
    const ts = new Date(t0 + i * stepHours * 3_600_000)
    const pad = n2 => String(n2).padStart(2, '0')
    const iso = `${ts.getFullYear()}-${pad(ts.getMonth() + 1)}-${pad(ts.getDate())} ${pad(ts.getHours())}:${pad(ts.getMinutes())}:${pad(ts.getSeconds())}`
    const avg = 20 + i * 0.1 + offset
    out.push({ t: iso, avg, max: 25 + i + offset, min: 15 + offset, open: avg, close: avg, count: 60 })
  }
  return out
}

const makeSeries = (monitorId, unit, name, count = 5) => ({
  monitorId,
  name,
  unit,
  color: null,
  points: makePoints(count, '2026-06-01 00:00:00', 1, monitorId),
  ma5: [],
  ma10: [],
  ma20: [],
  thresholds: null,
  anomalies: [],
})

describe('smallMultiplesOption', () => {
  it('produces one grid + one xAxis + one yAxis per series', () => {
    const series = [
      makeSeries(1, '°C', 'temp'),
      makeSeries(2, '%', 'humidity'),
      makeSeries(3, 'V', 'voltage'),
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)

    expect(opt.grid).toHaveLength(3)
    expect(opt.xAxis).toHaveLength(3)
    expect(opt.yAxis).toHaveLength(3)
    expect(opt.series).toHaveLength(3)
  })

  it('each yAxis is labeled with the series unit', () => {
    const series = [
      makeSeries(1, '°C', 'temp'),
      makeSeries(2, '%', 'humidity'),
      makeSeries(3, 'V', 'voltage'),
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    const names = opt.yAxis.map(y => y.name)
    expect(names).toEqual(['°C', '%', 'V'])
  })

  it('each series uses its own yAxisIndex', () => {
    const series = [
      makeSeries(1, '°C', 't1'),
      makeSeries(2, '%', 't2'),
      makeSeries(3, 'V', 't3'),
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    expect(opt.series[0].yAxisIndex).toBe(0)
    expect(opt.series[1].yAxisIndex).toBe(1)
    expect(opt.series[2].yAxisIndex).toBe(2)
  })

  it('each series has its own xAxisIndex', () => {
    const series = [
      makeSeries(1, '°C', 't1'),
      makeSeries(2, '%', 't2'),
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    expect(opt.series[0].xAxisIndex).toBe(0)
    expect(opt.series[1].xAxisIndex).toBe(1)
  })

  it('uses each series color in lineStyle', () => {
    const series = [
      { ...makeSeries(1, '°C', 't1'), color: '#00d4ff' },
      { ...makeSeries(2, '%', 't2'), color: '#ff5252' },
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    expect(opt.series[0].lineStyle.color).toBe('#00d4ff')
    expect(opt.series[1].lineStyle.color).toBe('#ff5252')
  })

  it('grids are stacked vertically (different top/bottom positions)', () => {
    const series = [
      makeSeries(1, '°C', 't1'),
      makeSeries(2, '%', 't2'),
      makeSeries(3, 'V', 't3'),
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    // Each grid should have different top position (stacked vertically)
    const tops = opt.grid.map(g => g.top)
    expect(new Set(tops).size).toBe(3)  // all different
  })

  it('each grid has its own label (series name)', () => {
    const series = [
      makeSeries(1, '°C', 'temp-probe'),
      makeSeries(2, '%', 'humidity-sensor'),
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    // ECharts title array — one per grid
    expect(opt.title).toBeDefined()
    const titles = Array.isArray(opt.title) ? opt.title : [opt.title]
    const texts = titles.map(t => t.text)
    expect(texts).toContain('temp-probe')
    expect(texts).toContain('humidity-sensor')
  })

  it('uses labelFor x-axis formatting for HOUR', () => {
    const series = [{ ...makeSeries(1, '°C', 't1'), color: '#00d4ff' }]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    expect(opt.xAxis[0].data[0]).toBe('06-01 00:00')
  })

  it('renders 5期平均 overlay in its own grid when the series carries ma5', () => {
    const series = [{
      ...makeSeries(1, '°C', 't1'),
      color: '#00d4ff',
      ma5: [null, null, null, null, 20.2],
    }]
    const opt = smallMultiplesOption(series, 'HOUR', 7)
    const ma = opt.series.find(s => s.name === '5期平均')
    expect(ma).toBeDefined()
    expect(ma.lineStyle.color).toBe(MA_COLORS[0])
    expect(ma.xAxisIndex).toBe(0)
    expect(ma.yAxisIndex).toBe(0)
  })

  it('handles empty series array', () => {
    const opt = smallMultiplesOption([], 'HOUR', 7)
    expect(opt.series).toEqual([])
    expect(opt.grid).toEqual([])
  })

  it('handles single series (one monitor)', () => {
    const opt = smallMultiplesOption(
      [{ ...makeSeries(1, '°C', 't1'), color: '#00d4ff' }],
      'HOUR', 7,
    )
    expect(opt.grid).toHaveLength(1)
    expect(opt.series).toHaveLength(1)
  })

  it('thresholds markLine attaches per-series', () => {
    const series = [
      {
        ...makeSeries(1, '°C', 't1'),
        color: '#00d4ff',
        thresholds: { min: 10, max: 30, warnCond: null },
      },
    ]
    const opt = smallMultiplesOption(series, 'HOUR', 7, true, false)
    expect(opt.series[0].markLine).toBeDefined()
    expect(opt.series[0].markLine.data.length).toBe(2)
  })

  it('anomaly markPoint matches buckets via the unified t field', () => {
    const series = [{
      ...makeSeries(1, '°C', 't1', 24),
      color: '#00d4ff',
      anomalies: [
        { time: '2026-06-01 03:15:00', actual: 50, expected: 20, deviation: 2.5, severity: 'medium' },
      ],
    }]
    const opt = smallMultiplesOption(series, 'HOUR', 7, false, true)
    expect(opt.series[0].markPoint.data).toHaveLength(1)
    expect(opt.series[0].markPoint.data[0].coord[0]).toBe(opt.xAxis[0].data[3])
  })
})
