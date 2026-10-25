import { describe, it, expect, vi } from 'vitest'
import { getTrendData } from '@/api/iot/trend'
import { useInfiniteTrend } from '../useInfiniteTrend'

// Unified backend payload (Task 4 contract): { granularity, mode, points, bucketSeconds, summary }
vi.mock('@/api/iot/trend', () => ({
  getTrendData: vi.fn(async (params) => {
    const start = new Date(String(params.startTime).replace(' ', 'T'))
    const pad = (n) => String(n).padStart(2, '0')
    const points = Array.from({ length: 2010 }, (_, i) => {
      const d = new Date(start.getTime() + i * 3_600_000)
      return {
        t: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`,
        avg: i, max: i, min: i, open: i, close: i, count: 1,
      }
    })
    return { granularity: 'HOUR', mode: 'OHLC', points, bucketSeconds: 3600, summary: null }
  }),
  getTrendDefault: vi.fn(async () => ({
    granularity: 'HOUR', mode: 'OHLC', points: [], bucketSeconds: 3600, summary: null,
  })),
}))

// We test the exported helper functions via the composable's exposed methods.
// The composable requires Vue reactivity, so we test pure logic here.

const mk = () => useInfiniteTrend({
  monitorId: { value: 1 }, monitorKind: { value: 'PROBE' },
  unit: { value: '' }, detectIntervalSeconds: { value: 10 },
})

const hourly = (startStr, n) => {
  const start = new Date(String(startStr).replace(' ', 'T'))
  const pad = (x) => String(x).padStart(2, '0')
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(start.getTime() + i * 3_600_000)
    return {
      t: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`,
      avg: i, max: i, min: i, open: i, close: i, count: 1,
    }
  })
}

describe('useInfiniteTrend helpers', () => {
  describe('granularityForSeconds', () => {
    const { _granularityForSeconds: g } = mk()

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
    it('returns null for empty data', () => {
      expect(mk().getVisibleTimeRange(0, 100)).toBeNull()
    })

    it('computes range from percentage', () => {
      const inst = mk()
      inst.points.value = [
        { t: '2026-06-01 00:00:00', avg: 20, max: 25, min: 15, open: 20, close: 20, count: 60 },
        { t: '2026-06-02 00:00:00', avg: 21, max: 26, min: 16, open: 21, close: 21, count: 60 },
        { t: '2026-06-03 00:00:00', avg: 22, max: 27, min: 17, open: 22, close: 22, count: 60 },
        { t: '2026-06-04 00:00:00', avg: 23, max: 28, min: 18, open: 23, close: 23, count: 60 },
        { t: '2026-06-05 00:00:00', avg: 24, max: 29, min: 19, open: 24, close: 24, count: 60 },
      ]
      const range = inst.getVisibleTimeRange(0, 60)
      expect(range).not.toBeNull()
      expect(range.startTime).toBe('2026-06-01 00:00:00')
      expect(range.endTime).toBe('2026-06-03 00:00:00')
    })
  })

  describe('trimBuffer', () => {
    it('trims the oldest points beyond the 2000-point cap', () => {
      const inst = mk()
      inst.points.value = hourly('2026-06-01 00:00:00', 2010)
      inst._trimBuffer()
      expect(inst.points.value.length).toBe(2000)
      // Oldest 10 dropped; point 10 has avg = 10
      expect(inst.points.value[0].avg).toBe(10)
      // bufferStartTime follows the new head
      expect(inst.bufferStartTime.value).toBe(inst.points.value[0].t)
    })

    it('does not trim when under the cap', () => {
      const inst = mk()
      inst.points.value = hourly('2026-06-01 00:00:00', 100)
      inst._trimBuffer()
      expect(inst.points.value.length).toBe(100)
    })

    it('fetchOlder past the cap retains the fetched older edge (perf budget)', async () => {
      const inst = mk()
      inst.applyResponse({
        granularity: 'HOUR', mode: 'OHLC', bucketSeconds: 3600, summary: null,
        points: hourly('2026-01-01 00:00:00', 2000),
      })
      expect(inst.points.value.length).toBe(2000)
      const fetchedHead = new Date(
        new Date('2026-01-01 00:00:00').getTime() - 1999 * 3_600_000)
      await inst.fetchOlder()
      expect(inst.points.value.length).toBeLessThanOrEqual(2000)
      // Direction-aware trim: an older-fetch retains the fetched (older) edge
      // instead of trimming it away, so the past keeps extending.
      const head = new Date(inst.bufferStartTime.value.replace(' ', 'T'))
      expect(head.getTime()).toBe(fetchedHead.getTime())
      expect(inst.bufferStartTime.value).toBe(inst.points.value[0].t)
    })

    it('fetchOlder past the cap keeps extending the past (no re-fetch stall)', async () => {
      const inst = mk()
      inst.applyResponse({
        granularity: 'HOUR', mode: 'OHLC', bucketSeconds: 3600, summary: null,
        points: hourly('2026-01-01 00:00:00', 2000),
      })
      // Real-server fidelity: return every bucket of the requested window, so
      // the doubled window comes back doubled — the fixed 2010-point module
      // mock hides the stall the cap causes.
      const windowFaithful = async (params) => {
        const s = new Date(String(params.startTime).replace(' ', 'T'))
        const e = new Date(String(params.endTime).replace(' ', 'T'))
        const n = Math.round((e.getTime() - s.getTime()) / 3_600_000) + 1
        return {
          granularity: 'HOUR', mode: 'OHLC', bucketSeconds: 3600, summary: null,
          points: hourly(params.startTime, n),
        }
      }
      vi.mocked(getTrendData).mockImplementationOnce(windowFaithful)
      vi.mocked(getTrendData).mockImplementationOnce(windowFaithful)

      const headBefore = new Date(inst.bufferStartTime.value.replace(' ', 'T')).getTime()
      await inst.fetchOlder()
      expect(inst.points.value.length).toBe(2000)
      const headAfter1 = new Date(inst.bufferStartTime.value.replace(' ', 'T')).getTime()
      expect(headAfter1).toBeLessThan(headBefore)

      // Second edge pan must extend the past again — the old head-trim left
      // bufferStartTime pinned and re-fetched the identical window forever.
      await inst.fetchOlder()
      const headAfter2 = new Date(inst.bufferStartTime.value.replace(' ', 'T')).getTime()
      expect(headAfter2).toBeLessThan(headAfter1)
    })
  })

  describe('appendRealtimePoint', () => {
    it('appends a unified point in INTRADAY mode', () => {
      const inst = mk()
      inst.granularity.value = 'INTRADAY'
      inst.points.value = [
        { t: '2026-06-01 10:00:00', avg: 23.5, max: 23.5, min: 23.5, open: 23.5, close: 23.5, count: 1 },
      ]
      inst.appendRealtimePoint({ time: '2026-06-01 10:00:10', value: 23.7 })
      expect(inst.points.value.length).toBe(2)
      expect(inst.points.value[1].t).toBe('2026-06-01 10:00:10')
      expect(inst.points.value[1].avg).toBe(23.7)
    })

    it('ignores append in non-INTRADAY mode', () => {
      const inst = mk()
      inst.granularity.value = 'HOUR'
      inst.points.value = []
      inst.appendRealtimePoint({ time: '2026-06-01 10:00:10', value: 23.7 })
      expect(inst.points.value.length).toBe(0)
    })

    it('trims the INTRADAY buffer to the cap on append', () => {
      const inst = mk()
      inst.granularity.value = 'INTRADAY'
      inst.points.value = hourly('2026-06-01 00:00:00', 2000)
      inst.appendRealtimePoint({ time: '2026-06-02 00:00:00', value: 2000 })
      expect(inst.points.value.length).toBe(2000)
      // Oldest point trimmed away
      expect(inst.points.value[0].avg).toBe(1)
    })
  })

  describe('clearBuffer', () => {
    it('resets all refs to initial state', () => {
      const inst = mk()
      inst.points.value = [{ t: '2026-06-01', avg: 1, max: 2, min: 0, open: 1, close: 1, count: 1 }]
      inst.mode.value = 'OHLC'
      inst.bucketSeconds.value = 3600
      inst.summary.value = { currentValue: 20 }
      inst.clearBuffer()
      expect(inst.points.value).toEqual([])
      expect(inst.mode.value).toBe('RAW')
      expect(inst.bucketSeconds.value).toBeNull()
      expect(inst.summary.value).toBeNull()
    })
  })

  describe('updateBufferBounds', () => {
    it('sets bounds to first/last point t for non-empty data', () => {
      const inst = mk()
      inst.applyResponse({
        granularity: 'HOUR', mode: 'OHLC', bucketSeconds: 3600, summary: null,
        points: [
          { t: '2026-06-01 00:00:00', avg: 1, max: 2, min: 0, open: 1, close: 1, count: 1 },
          { t: '2026-06-02 00:00:00', avg: 2, max: 3, min: 1, open: 2, close: 2, count: 1 },
        ],
      })
      expect(inst.bufferStartTime.value).toBe('2026-06-01 00:00:00')
      expect(inst.bufferEndTime.value).toBe('2026-06-02 00:00:00')
      expect(inst.mode.value).toBe('OHLC')
      expect(inst.bucketSeconds.value).toBe(3600)
    })

    it('sets bounds to null for empty data', () => {
      const inst = mk()
      inst.clearBuffer()
      expect(inst.bufferStartTime.value).toBeNull()
      expect(inst.bufferEndTime.value).toBeNull()
    })
  })
})
