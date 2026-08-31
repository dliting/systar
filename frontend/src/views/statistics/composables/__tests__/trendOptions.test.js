import { describe, it, expect } from 'vitest'
import { lineOption, intradayOption, emptyFrameOption, multiSeriesOption } from '../trendOptions'

/**
 * Build n synthetic dataPoints starting at start, stepping stepHours apart.
 * Uses local-time formatting (matching backend LocalDateTime serialization
 * consumed via Date parse which also uses local time) so formatAxisTimeAdaptive
 * produces predictable labels regardless of test runner timezone.
 */
const makePoints = (n, start = '2026-06-01 00:00:00', stepHours = 1) => {
  const out = []
  const t0  = new Date(start.replace(' ', 'T'))
  for (let i = 0; i < n; i++) {
    const ts = new Date(t0.getTime() + i * stepHours * 3_600_000)
    const pad = n2 => String(n2).padStart(2, '0')
    const local = `${ts.getFullYear()}-${pad(ts.getMonth() + 1)}-${pad(ts.getDate())} ${pad(ts.getHours())}:${pad(ts.getMinutes())}:${pad(ts.getSeconds())}`
    out.push({
      time  : local,
      avg   : 20 + i * 0.1,
      max   : 25 + i * 0.2,
      min   : 15 + i * 0.05,
      sampleCount: 60,
    })
  }
  return out
}

describe('lineOption (阶段 1: tooltip / axis / dataZoom)', () => {
  describe('tooltip unit handling', () => {
    it('shows unit in tooltip header, not in each series row', () => {
      const points = makePoints(10)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7)
      // formatter is a function — invoke with synthetic params
      const html = opt.tooltip.formatter([
        { axisValue: '06-01 00', seriesName: '最大值', value: 25, color: '#ff5252' },
        { axisValue: '06-01 00', seriesName: '均值', value: 20, color: '#00d4ff' },
      ])
      // Unit should appear once (in header), not twice (in each row)
      const matches = (html.match(/°C/g) || []).length
      expect(matches).toBe(1)
    })

    it('header contains time label', () => {
      const points = makePoints(10)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7)
      const html = opt.tooltip.formatter([
        { axisValue: '06-01 00', value: 25, seriesName: '最大值', color: '#ff5252' },
      ])
      expect(html).toContain('06-01 00')
    })

    it('series row shows value without repeated unit suffix', () => {
      const opt = lineOption(makePoints(10), [], [], [], 'V', 'HOUR', false, 7)
      const html = opt.tooltip.formatter([
        { axisValue: '06-01 00', value: 220.123, seriesName: '均值', color: '#00d4ff' },
      ])
      // Value should use unit precision (2 decimals for V)
      expect(html).toContain('220.12')
      // Should NOT contain (V) at row end
      expect(html).not.toMatch(/220\.12<\/span>\(V\)/)
    })
  })

  describe('adaptive x-axis formatting (replaces formatLineAxisTime)', () => {
    it('HOUR granularity with rangeDays > 1.5 uses "MM-DD HH" format', () => {
      const points = makePoints(10)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7)
      // First label should be "06-01 00"
      expect(opt.xAxis.data[0]).toBe('06-01 00')
    })

    it('HOUR granularity with rangeDays ≤ 1.5 uses "HH:mm" format', () => {
      const points = makePoints(10)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 1)
      expect(opt.xAxis.data[0]).toBe('00:00')
    })

    it('DAY granularity with rangeDays ≤ 365 uses "MM-DD" format', () => {
      const points = makePoints(10, '2026-06-01 00:00:00', 24)
      const opt = lineOption(points, [], [], [], '°C', 'DAY', false, 30)
      expect(opt.xAxis.data[0]).toBe('06-01')
    })

    it('DAY granularity with rangeDays > 365 uses "YY-MM-DD" format', () => {
      const points = makePoints(10, '2026-06-01 00:00:00', 24)
      const opt = lineOption(points, [], [], [], '°C', 'DAY', false, 400)
      expect(opt.xAxis.data[0]).toBe('26-06-01')
    })

    it('MONTH granularity always uses "YYYY-MM"', () => {
      const points = makePoints(10, '2026-06-01 00:00:00', 24 * 30)
      const opt = lineOption(points, [], [], [], '°C', 'MONTH', false, 500)
      expect(opt.xAxis.data[0]).toBe('2026-06')
    })
  })

  describe('dataZoom hint when windowed', () => {
    it('includes graphic hint when periodCount > DEFAULT_PERIOD_WINDOW', () => {
      const points = makePoints(100)  // > 60
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 100)
      expect(opt.graphic).toBeDefined()
      // Graphic should be an array with at least one element containing the hint text
      const graphics = Array.isArray(opt.graphic) ? opt.graphic : [opt.graphic]
      const hintText = graphics.map(g => g?.style?.text || '').join(' ')
      expect(hintText).toContain('更早')
    })

    it('no graphic hint when periodCount ≤ DEFAULT_PERIOD_WINDOW', () => {
      const points = makePoints(30)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 30)
      expect(opt.graphic).toBeUndefined()
    })

    it('graphic hint z-index above dataZoom', () => {
      const points = makePoints(100)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 100)
      const graphics = Array.isArray(opt.graphic) ? opt.graphic : [opt.graphic]
      for (const g of graphics) {
        if (g?.style?.text?.includes('更早')) {
          expect(g.z).toBeGreaterThanOrEqual(100)
        }
      }
    })
  })

  describe('moving-average insufficient-data title hint', () => {
    it('renders title hint when all MAs are null', () => {
      const points = makePoints(3)  // too few for MA5
      const opt = lineOption(points, [null, null, null], [null, null, null], [null, null, null], '°C', 'HOUR', false, 7)
      expect(opt.title).toBeDefined()
      const titles = Array.isArray(opt.title) ? opt.title : [opt.title]
      const text = titles.map(t => t?.text || '').join(' ')
      expect(text).toContain('均线数据不足')
    })

    it('no title hint when MAs have at least one non-null value', () => {
      const points = makePoints(10)
      const opt = lineOption(points, [null, null, 20, 21], [null, null, null, null], [null, null, null, null], '°C', 'HOUR', false, 7)
      // avg5 has a non-null at index 2 — should suppress the title hint
      const titles = Array.isArray(opt.title) ? opt.title : (opt.title ? [opt.title] : [])
      if (titles.length > 0) {
        const text = titles.map(t => t?.text || '').join(' ')
        expect(text).not.toContain('均线数据不足')
      }
    })

    it('no title hint in compact mode (text would not fit)', () => {
      const points = makePoints(3)
      const opt = lineOption(points, [null, null, null], [null, null, null], [null, null, null], '°C', 'HOUR', true, 7)
      expect(opt.title).toBeUndefined()
    })
  })

  describe('legend in compact vs full', () => {
    it('compact mode hides legend', () => {
      const opt = lineOption(makePoints(10), [], [], [], '°C', 'HOUR', true, 7)
      expect(opt.legend).toBeUndefined()
    })

    it('full mode shows legend', () => {
      const opt = lineOption(makePoints(10), [], [], [], '°C', 'HOUR', false, 7)
      expect(opt.legend).toBeDefined()
      expect(opt.legend.data).toContain('最大值')
      expect(opt.legend.data).toContain('均值')
      expect(opt.legend.data).toContain('最小值')
    })
  })

  describe('dataZoom in compact vs full', () => {
    it('compact mode hides dataZoom', () => {
      const opt = lineOption(makePoints(100), [], [], [], '°C', 'HOUR', true, 100)
      expect(opt.dataZoom).toBeUndefined()
    })

    it('full mode shows dataZoom with slider + inside', () => {
      const opt = lineOption(makePoints(100), [], [], [], '°C', 'HOUR', false, 100)
      expect(opt.dataZoom).toBeDefined()
      expect(opt.dataZoom).toHaveLength(2)
      expect(opt.dataZoom.map(d => d.type).sort()).toEqual(['inside', 'slider'])
    })
  })
})

describe('intradayOption (阶段 1 — unchanged shape)', () => {
  it('produces single series for intraday data', () => {
    const points = [{ time: '2026-06-15 14:30:00', value: 23.5 }]
    const opt = intradayOption(points, '°C', 60, 10, false)
    expect(opt.series).toHaveLength(1)
    expect(opt.series[0].data).toContain(23.5)
  })

  it('tooltip unit shown in header only', () => {
    const points = [{ time: '2026-06-15 14:30:00', value: 23.5 }]
    const opt = intradayOption(points, '°C', 60, 10, false)
    const html = opt.tooltip.formatter([{ dataIndex: 0, value: 23.5, axisValue: '14:30' }])
    // °C should appear exactly once (in the header unitText)
    expect((html.match(/°C/g) || []).length).toBe(1)
    // Value row should not duplicate unit suffix on the value
    expect(html).not.toMatch(/监测值.*°C/)
  })
})

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

describe('lineOption (阶段 2: thresholds + anomaly markLine/markPoint)', () => {
  describe('threshold markLine rendering', () => {
    it('renders markLine for both min and max when thresholds present and showThresholds=true', () => {
      const points = makePoints(10)
      const thresholds = { min: 10, max: 30 }
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, thresholds, [], true, false)

      // 均值 (series[1]) should have markLine with 2 entries
      const meanSeries = opt.series[1]
      expect(meanSeries.markLine).toBeDefined()
      expect(meanSeries.markLine.data).toHaveLength(2)
      const yAxisValues = meanSeries.markLine.data.map(d => d.yAxis).sort((a, b) => a - b)
      expect(yAxisValues).toEqual([10, 30])
    })

    it('renders markLine only for max when min is null', () => {
      const points = makePoints(10)
      const thresholds = { min: null, max: 30 }
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, thresholds, [], true, false)

      const meanSeries = opt.series[1]
      expect(meanSeries.markLine.data).toHaveLength(1)
      expect(meanSeries.markLine.data[0].yAxis).toBe(30)
    })

    it('omits markLine when showThresholds=false', () => {
      const points = makePoints(10)
      const thresholds = { min: 10, max: 30 }
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, thresholds, [], false, false)

      const meanSeries = opt.series[1]
      // markLine data should be empty (not undefined — we always attach the field; just no entries)
      expect((meanSeries.markLine?.data || [])).toHaveLength(0)
    })

    it('omits markLine when thresholds is null', () => {
      const points = makePoints(10)
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, [], true, false)

      const meanSeries = opt.series[1]
      expect((meanSeries.markLine?.data || [])).toHaveLength(0)
    })

    it('markLine label uses formatMonitorValue for precision', () => {
      const points = makePoints(10)
      const thresholds = { min: 0, max: 79.99999 }  // tricky float
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, thresholds, [], true, false)

      const meanSeries = opt.series[1]
      const maxLine = meanSeries.markLine.data.find(d => d.yAxis === 79.99999)
      // Formatter should apply °C precision (1 decimal) — produces "上限 80.0"
      expect(maxLine.label.formatter).toContain('80.0')
    })

    it('markLine label does not use ECharts {c} placeholder', () => {
      const points = makePoints(10)
      const thresholds = { min: 10, max: 30 }
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, thresholds, [], true, false)

      const meanSeries = opt.series[1]
      for (const line of meanSeries.markLine.data) {
        expect(line.label.formatter).not.toMatch(/\{c\}/)
      }
    })
  })

  describe('anomaly markPoint bucket matching', () => {
    it('renders markPoint for anomaly that falls inside a data bucket (HOUR granularity)', () => {
      // dataPoints have bucket_start at hour boundary (e.g. 2026-06-01 00:00:00).
      // anomaly.time is mid-hour (e.g. 14:23:45) — should match bucket at 14:00.
      const points = makePoints(24, '2026-06-01 00:00:00', 1)  // 24 hourly buckets starting at 00:00
      const anomalies = [
        { time: '2026-06-01 14:23:45', actual: 95, expected: 22, deviation: 3.5, severity: 'high' },
      ]
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, anomalies, false, true)

      const meanSeries = opt.series[1]
      expect(meanSeries.markPoint).toBeDefined()
      expect(meanSeries.markPoint.data).toHaveLength(1)
      const mp = meanSeries.markPoint.data[0]
      // coord[0] should match xAxis.data[14] (the bucket at hour 14)
      expect(mp.coord[0]).toBe(opt.xAxis.data[14])
      // value label should include σ
      expect(mp.value).toContain('3.5')
    })

    it('renders markPoint for anomaly that matches a DAY bucket by day', () => {
      const points = makePoints(10, '2026-06-01 00:00:00', 24)  // 10 daily buckets
      const anomalies = [
        { time: '2026-06-05 12:30:00', actual: 50, expected: 20, deviation: 2.5, severity: 'medium' },
      ]
      const opt = lineOption(points, [], [], [], '°C', 'DAY', false, 30, null, anomalies, false, true)

      const meanSeries = opt.series[1]
      expect(meanSeries.markPoint.data).toHaveLength(1)
      // coord[0] should match xAxis.data[4] (index 4 is 06-05)
      expect(meanSeries.markPoint.data[0].coord[0]).toBe(opt.xAxis.data[4])
    })

    it('omits markPoint when showAnomalies=false', () => {
      const points = makePoints(10)
      const anomalies = [{ time: points[0].time, actual: 50, expected: 20, deviation: 3, severity: 'high' }]
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, anomalies, false, false)

      const meanSeries = opt.series[1]
      expect((meanSeries.markPoint?.data || [])).toHaveLength(0)
    })

    it('omits markPoint when anomalyPoints is null or empty', () => {
      const points = makePoints(10)
      const opt1 = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, null, false, true)
      const opt2 = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, [], false, true)

      expect((opt1.series[1].markPoint?.data || [])).toHaveLength(0)
      expect((opt2.series[1].markPoint?.data || [])).toHaveLength(0)
    })

    it('skips anomalies that fall outside the dataPoints range', () => {
      const points = makePoints(10, '2026-06-01 00:00:00', 1)  // hours 0..9 of 2026-06-01
      const anomalies = [
        // Outside range — should be skipped without error
        { time: '2026-05-30 12:00:00', actual: 50, expected: 20, deviation: 3, severity: 'high' },
        { time: '2026-06-15 00:00:00', actual: 99, expected: 20, deviation: 4, severity: 'high' },
      ]
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, anomalies, false, true)

      const meanSeries = opt.series[1]
      expect(meanSeries.markPoint.data).toHaveLength(0)
    })

    it('coord[0] always matches an xAxis.data entry (strict literal)', () => {
      // Critical NC1 property: ECharts category axis requires coord[0] === xAxis.data[i]
      const points = makePoints(24, '2026-06-01 00:00:00', 1)
      const anomalies = [
        { time: '2026-06-01 03:15:00', actual: 50, expected: 20, deviation: 2.5, severity: 'medium' },
        { time: '2026-06-01 17:45:00', actual: 80, expected: 20, deviation: 5, severity: 'high' },
      ]
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, anomalies, false, true)

      const meanSeries = opt.series[1]
      for (const mp of meanSeries.markPoint.data) {
        expect(opt.xAxis.data).toContain(mp.coord[0])
      }
    })

    it('markPoint color reflects severity', () => {
      const points = makePoints(24, '2026-06-01 00:00:00', 1)
      const anomalies = [
        { time: '2026-06-01 03:15:00', actual: 50, expected: 20, deviation: 2.5, severity: 'medium' },
        { time: '2026-06-01 17:45:00', actual: 80, expected: 20, deviation: 5, severity: 'high' },
      ]
      const opt = lineOption(points, [], [], [], '°C', 'HOUR', false, 7, null, anomalies, false, true)

      const meanSeries = opt.series[1]
      const colors = meanSeries.markPoint.data.map(d => d.itemStyle.color)
      expect(colors).toContain('#ff5252')   // high severity red
      expect(colors).toContain('#ffab00')   // medium severity amber
    })
  })
})

describe('multiSeriesOption (阶段 3: comparison chart)', () => {
  const makeSeries = (id, unit, caption, count = 5) => ({
    id,
    caption,
    unit,
    color   : null,  // assigned by caller
    dataPoints: makePoints(count, `2026-06-01 00:00:00`, 1).map(p => ({
      ...p,
      avg: p.avg + id,  // offset each series so curves don't overlap
      max: p.max + id,
      min: p.min + id,
    })),
    avg5: [],
    avg10: [],
    avg20: [],
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

      // Three series per monitor (avg/max/min) → 2 monitors × 3 = 6 series
      // But multi-series typically shows only avg for clarity — verify at least 2 series
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

    it('tooltip groups values by monitor caption', () => {
      const series = [
        { ...makeSeries(1, '°C', 'temp-1'), color: '#00d4ff' },
        { ...makeSeries(2, '°C', 'temp-2'), color: '#ff5252' },
      ]
      const opt = multiSeriesOption(series, 'HOUR', 7)

      const html = opt.tooltip.formatter([
        { axisValue: '06-01 00', seriesName: 'temp-1', value: 25, color: '#00d4ff' },
        { axisValue: '06-01 00', seriesName: 'temp-2', value: 30, color: '#ff5252' },
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

  describe('dataZoom present in non-compact', () => {
    it('includes inside + slider dataZoom', () => {
      const series = [{ ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' }]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      expect(opt.dataZoom).toBeDefined()
      expect(opt.dataZoom.map(d => d.type).sort()).toEqual(['inside', 'slider'])
    })
  })

  describe('xAxis adaptive formatting', () => {
    it('uses formatAxisTimeAdaptive for HOUR granularity with 7-day range', () => {
      const series = [{ ...makeSeries(1, '°C', 'temp'), color: '#00d4ff' }]
      const opt = multiSeriesOption(series, 'HOUR', 7)
      expect(opt.xAxis.data[0]).toBe('06-01 00')
    })

    it('uses MM-DD format for DAY granularity', () => {
      const series = [{
        ...makeSeries(1, '°C', 'temp'),
        dataPoints: makePoints(5, '2026-06-01 00:00:00', 24),
        color: '#00d4ff',
      }]
      const opt = multiSeriesOption(series, 'DAY', 30)
      expect(opt.xAxis.data[0]).toBe('06-01')
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
