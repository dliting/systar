import { describe, it, expect } from 'vitest'
import {
  useInfiniteTrend,
} from '../useInfiniteTrend'

// We test the exported helper functions via the composable's _exposed methods.
// The composable requires Vue reactivity, so we test pure logic here.

describe('useInfiniteTrend helpers', () => {
  describe('granularityForSeconds', () => {
    const { _granularityForSeconds: g } = useInfiniteTrend({
      monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
      unit: { value: '' }, detectIntervalSeconds: { value: 10 },
    })

    it('≤1 day → INTRADAY', () => {
      expect(g(0)).toBe('INTRADAY')
      expect(g(86_400)).toBe('INTRADAY')
    })

    it('≤31 days → HOUR', () => {
      expect(g(86_401)).toBe('HOUR')
      expect(g(2_678_400)).toBe('HOUR')
    })

    it('≤180 days → DAY', () => {
      expect(g(2_678_401)).toBe('DAY')
      expect(g(15_552_000)).toBe('DAY')
    })

    it('≤2 years → WEEK', () => {
      expect(g(15_552_001)).toBe('WEEK')
      expect(g(63_072_000)).toBe('WEEK')
    })

    it('>2 years → MONTH', () => {
      expect(g(63_072_001)).toBe('MONTH')
    })
  })

  describe('getVisibleTimeRange', () => {
    const { _getVisibleTimeRange: getRange } = useInfiniteTrend({
      monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
      unit: { value: '' }, detectIntervalSeconds: { value: 10 },
    })

    it('returns null for empty data', () => {
      expect(getRange(0, 100)).toBeNull()
    })

    it('computes range from percentage', () => {
      // Manually set dataPoints for this test
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      // Simulate data
      inst.dataPoints.value = [
        { time: '2026-06-01 00:00:00', avg: 20, max: 25, min: 15, sampleCount: 60 },
        { time: '2026-06-02 00:00:00', avg: 21, max: 26, min: 16, sampleCount: 60 },
        { time: '2026-06-03 00:00:00', avg: 22, max: 27, min: 17, sampleCount: 60 },
        { time: '2026-06-04 00:00:00', avg: 23, max: 28, min: 18, sampleCount: 60 },
        { time: '2026-06-05 00:00:00', avg: 24, max: 29, min: 19, sampleCount: 60 },
      ]
      const range = inst._getVisibleTimeRange(0, 60)
      expect(range).not.toBeNull()
      expect(range.startTime).toBe('2026-06-01 00:00:00')
      expect(range.endTime).toBe('2026-06-03 00:00:00')
    })
  })

  describe('trimBuffer', () => {
    it('trims aggregated data exceeding max (default: head)', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      // Fill with 2010 points (max is 2000)
      const points = Array.from({ length: 2010 }, (_, i) => ({
        time: `2026-06-${String(i % 30 + 1).padStart(2, '0')} 00:00:00`,
        avg: 20 + i * 0.1, max: 25 + i * 0.1, min: 15 + i * 0.1, sampleCount: 60,
      }))
      inst.dataPoints.value = points
      inst.avg5.value = points.map(() => null)
      inst.avg10.value = points.map(() => null)
      inst.avg20.value = points.map(() => null)
      inst._trimBuffer()
      // Default: trim HEAD (drop oldest 10, keep newest 2000)
      expect(inst.dataPoints.value.length).toBe(2000)
      expect(inst.avg5.value.length).toBe(2000)
      // Points 10..2019 are kept; point 10 has avg = 20 + 10*0.1 = 21
      expect(inst.dataPoints.value[0].avg).toBeCloseTo(21, 5)
    })

    it('does not trim when under limit', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.dataPoints.value = Array.from({ length: 100 }, (_, i) => ({
        time: `2026-06-${String(i % 30 + 1).padStart(2, '0')} 00:00:00`,
        avg: 20, max: 25, min: 15, sampleCount: 60,
      }))
      inst._trimBuffer()
      expect(inst.dataPoints.value.length).toBe(100)
    })

    it('trims TAIL when fetchDirection=older (preserve just-fetched older data)', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      // 2010 points with monotonic avg so we can tell head from tail
      const points = Array.from({ length: 2010 }, (_, i) => ({
        time: `2026-06-${String((i % 30) + 1).padStart(2, '0')} ${String(Math.floor(i / 30)).padStart(2, '0')}:00:00`,
        avg: i, max: i, min: i, sampleCount: 60,
      }))
      inst.dataPoints.value = points
      inst.avg5.value  = points.map(p => p.avg)
      inst.avg10.value = points.map(p => p.avg)
      inst.avg20.value = points.map(p => p.avg)
      inst._trimBuffer('older')
      expect(inst.dataPoints.value.length).toBe(2000)
      // 'older' keeps HEAD (smallest avg values), drops TAIL (largest)
      expect(inst.dataPoints.value[0].avg).toBe(0)
      expect(inst.dataPoints.value[1999].avg).toBe(1999)
      // avg arrays must be sliced in parallel
      expect(inst.avg5.value[0]).toBe(0)
      expect(inst.avg5.value[1999]).toBe(1999)
    })

    it('trims HEAD when fetchDirection=newer (preserve just-fetched newer data)', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      const points = Array.from({ length: 2010 }, (_, i) => ({
        time: `2026-06-${String((i % 30) + 1).padStart(2, '0')} ${String(Math.floor(i / 30)).padStart(2, '0')}:00:00`,
        avg: i, max: i, min: i, sampleCount: 60,
      }))
      inst.dataPoints.value = points
      inst.avg5.value  = points.map(p => p.avg)
      inst.avg10.value = points.map(p => p.avg)
      inst.avg20.value = points.map(p => p.avg)
      inst._trimBuffer('newer')
      expect(inst.dataPoints.value.length).toBe(2000)
      // 'newer' keeps TAIL (largest avg), drops HEAD (smallest)
      expect(inst.dataPoints.value[0].avg).toBe(10)
      expect(inst.dataPoints.value[1999].avg).toBe(2009)
    })
  })

  describe('appendRealtimePoint', () => {
    it('appends point in INTRADAY mode', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.granularity.value = 'INTRADAY'
      inst.intradayPoints.value = [
        { time: '2026-06-01 10:00:00', value: 23.5 },
      ]
      inst.appendRealtimePoint({ time: '2026-06-01 10:00:10', value: 23.7 })
      expect(inst.intradayPoints.value.length).toBe(2)
      expect(inst.intradayPoints.value[1].value).toBe(23.7)
    })

    it('ignores append in non-INTRADAY mode', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.granularity.value = 'HOUR'
      inst.intradayPoints.value = []
      inst.appendRealtimePoint({ time: '2026-06-01 10:00:10', value: 23.7 })
      expect(inst.intradayPoints.value.length).toBe(0)
    })

    it('trims INTRADAY buffer when exceeding max', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.granularity.value = 'INTRADAY'
      // Fill to max (10000)
      const pts = Array.from({ length: 10000 }, (_, i) => ({
        time: `2026-06-01 ${String(Math.floor(i / 60)).padStart(2, '0')}:${String(i % 60).padStart(2, '0')}:00`,
        value: i,
      }))
      inst.intradayPoints.value = pts
      // Append one more → trim should kick in
      inst.appendRealtimePoint({ time: '2026-06-02 00:00:00', value: 10000 })
      expect(inst.intradayPoints.value.length).toBe(10000)
      // Oldest point should have been trimmed
      expect(inst.intradayPoints.value[0].value).toBe(1)
    })
  })

  describe('clearBuffer', () => {
    it('resets all refs to initial state', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.dataPoints.value = [{ time: '2026-06-01', avg: 1, max: 2, min: 0, sampleCount: 1 }]
      inst.intradayPoints.value = [{ time: '2026-06-01', value: 1 }]
      inst.avg5.value = [1, 2]
      inst.avg10.value = [1, 2]
      inst.avg20.value = [1, 2]
      inst.summary.value = { currentValue: 20 }
      inst.clearBuffer()
      expect(inst.dataPoints.value).toEqual([])
      expect(inst.intradayPoints.value).toEqual([])
      expect(inst.avg5.value).toEqual([])
      expect(inst.avg10.value).toEqual([])
      expect(inst.avg20.value).toEqual([])
      expect(inst.summary.value).toBeNull()
    })
  })

  describe('INTRADAY trimBuffer trims avg arrays', () => {
    it('trims avg5/avg10/avg20 alongside intradayPoints when they exceed max', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.granularity.value = 'INTRADAY'
      const len = 10010
      inst.intradayPoints.value = Array.from({ length: len }, (_, i) => ({
        time: `2026-06-01 ${String(Math.floor(i / 3600)).padStart(2, '0')}:${String(Math.floor((i % 3600) / 60)).padStart(2, '0')}:00`,
        value: i,
      }))
      inst.avg5.value = Array.from({ length: len }, (_, i) => i)
      inst.avg10.value = Array.from({ length: len }, (_, i) => i)
      inst.avg20.value = Array.from({ length: len }, (_, i) => i)
      inst._trimBuffer()
      expect(inst.intradayPoints.value.length).toBe(10000)
      expect(inst.avg5.value.length).toBe(10000)
      expect(inst.avg10.value.length).toBe(10000)
      expect(inst.avg20.value.length).toBe(10000)
      // Oldest trimmed: values should start from index 10
      expect(inst.avg5.value[0]).toBe(10)
    })
  })

  describe('updateBufferBounds', () => {
    it('sets bounds to first/last point time for non-empty data', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.applyResponse({
        granularity: 'HOUR',
        dataPoints: [
          { time: '2026-06-01 00:00:00', avg: 1, max: 2, min: 0, sampleCount: 1 },
          { time: '2026-06-02 00:00:00', avg: 2, max: 3, min: 1, sampleCount: 1 },
        ],
        avg5: [], avg10: [], avg20: [],
        summary: null,
      })
      expect(inst.bufferStartTime.value).toBe('2026-06-01 00:00:00')
      expect(inst.bufferEndTime.value).toBe('2026-06-02 00:00:00')
    })

    it('sets bounds to null for empty data', () => {
      const inst = useInfiniteTrend({
        monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
        unit: { value: '' }, detectIntervalSeconds: { value: 10 },
      })
      inst.clearBuffer()
      expect(inst.bufferStartTime.value).toBeNull()
      expect(inst.bufferEndTime.value).toBeNull()
    })
  })
})
