import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'

// Mock API clients before importing the composable
vi.mock('@/api/iot/trend', () => ({
  getTrendData    : vi.fn(),
  getTrendMetadata: vi.fn(),
}))
vi.mock('@/api/iot/analysis', () => ({
  detectAnomalies: vi.fn(),
}))

import { getTrendData, getTrendMetadata } from '@/api/iot/trend'
import { detectAnomalies } from '@/api/iot/analysis'
import { useTrendSeriesLoader } from '../useTrendSeriesLoader'

describe('useTrendSeriesLoader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('exposes series/loading/failedCount refs with empty defaults', () => {
    const { series, loading, failedCount } = useTrendSeriesLoader()
    expect(series.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(failedCount.value).toBe(0)
  })

  it('loads all monitors in parallel via Promise.allSettled', async () => {
    getTrendData.mockResolvedValueOnce({
      granularity: 'HOUR',
      dataPoints: [{ time: '2026-06-01 00:00:00', avg: 20, max: 25, min: 15, sampleCount: 1 }],
      avg5: [], avg10: [], avg20: [],
      summary: { currentValue: 20, periodMax: 25, periodMin: 15, totalSamples: 1 },
    })
    getTrendData.mockResolvedValueOnce({
      granularity: 'HOUR',
      dataPoints: [{ time: '2026-06-01 00:00:00', avg: 30, max: 35, min: 25, sampleCount: 1 }],
      avg5: [], avg10: [], avg20: [],
      summary: { currentValue: 30, periodMax: 35, periodMin: 25, totalSamples: 1 },
    })
    getTrendMetadata.mockResolvedValue({ unit: '°C', caption: 't1' })
    detectAnomalies.mockResolvedValue([])

    const { load, series, loading, failedCount } = useTrendSeriesLoader()

    await load([1, 2], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')

    expect(loading.value).toBe(false)
    expect(series.value).toHaveLength(2)
    expect(failedCount.value).toBe(0)
    expect(getTrendData).toHaveBeenCalledTimes(2)
    expect(getTrendMetadata).toHaveBeenCalledTimes(2)
  })

  it('partial failure: successful monitors render, failedCount reflects failures', async () => {
    getTrendData
      .mockResolvedValueOnce({ granularity: 'HOUR', dataPoints: [], summary: null, avg5: [], avg10: [], avg20: [] })
      .mockRejectedValueOnce(new Error('404'))  // monitor 2 fails
    getTrendMetadata.mockResolvedValue({ unit: '°C' })
    detectAnomalies.mockResolvedValue([])

    const { load, series, failedCount } = useTrendSeriesLoader()
    await load([1, 2], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')

    expect(series.value).toHaveLength(1)        // only successful one
    expect(series.value[0].id).toBe(1)
    expect(failedCount.value).toBe(1)
  })

  it('all-failure: empty series, failedCount = N', async () => {
    getTrendData.mockRejectedValue(new Error('500'))
    getTrendMetadata.mockResolvedValue({ unit: '°C' })
    detectAnomalies.mockResolvedValue([])

    const { load, series, failedCount } = useTrendSeriesLoader()
    await load([1, 2, 3], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')

    expect(series.value).toEqual([])
    expect(failedCount.value).toBe(3)
  })

  it('detectAnomalies failure does not block trend series (best-effort)', async () => {
    getTrendData.mockResolvedValue({ granularity: 'HOUR', dataPoints: [], summary: null, avg5: [], avg10: [], avg20: [] })
    getTrendMetadata.mockResolvedValue({ unit: '°C' })
    detectAnomalies.mockRejectedValue(new Error('anomaly service down'))
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    try {
      const { load, series, failedCount } = useTrendSeriesLoader()
      await load([1], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')

      expect(series.value).toHaveLength(1)        // trend data still loaded
      expect(series.value[0].anomalies).toEqual([])  // anomalies default to []
      expect(failedCount.value).toBe(0)           // anomaly failure doesn't count
      expect(warnSpy).toHaveBeenCalledWith('Failed to load anomalies:', expect.any(Error))
    } finally {
      warnSpy.mockRestore()
    }
  })

  it('skips detectAnomalies when granularity is not HOUR/DAY', async () => {
    getTrendData.mockResolvedValue({ granularity: 'WEEK', dataPoints: [], summary: null, avg5: [], avg10: [], avg20: [] })
    getTrendMetadata.mockResolvedValue({ unit: '°C' })

    const { load } = useTrendSeriesLoader()
    await load([1], 'PROBE', ['2026-06-01 00:00:00', '2026-08-01 00:00:00'], 'WEEK')

    expect(detectAnomalies).not.toHaveBeenCalled()
  })

  it('clear() resets state to defaults', async () => {
    getTrendData.mockResolvedValue({ granularity: 'HOUR', dataPoints: [{ time: '2026-06-01 00:00:00', avg: 1, max: 2, min: 0, sampleCount: 1 }], summary: null, avg5: [], avg10: [], avg20: [] })
    getTrendMetadata.mockResolvedValue({ unit: '°C' })
    detectAnomalies.mockResolvedValue([])

    const { load, clear, series, failedCount } = useTrendSeriesLoader()
    await load([1], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')
    expect(series.value).toHaveLength(1)

    clear()
    expect(series.value).toEqual([])
    expect(failedCount.value).toBe(0)
  })

  /**
   * If clear() is called while a load is in flight, the pending result must
   * be discarded — otherwise it would resolve after clear() and re-populate
   * series.value, undoing the clear.
   */
  it('clear() discards in-flight load() result', async () => {
    let resolveLoad
    getTrendData.mockImplementation(() => new Promise(resolve => {
      resolveLoad = () => resolve({ granularity: 'HOUR', dataPoints: [{ time: '2026-06-01 00:00:00', avg: 1, max: 2, min: 0, sampleCount: 1 }] })
    }))
    getTrendMetadata.mockResolvedValue({ unit: '°C' })
    detectAnomalies.mockResolvedValue([])

    const { load, clear, series } = useTrendSeriesLoader()
    const p = load([1], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')
    // clear() before the load resolves
    clear()
    expect(series.value).toEqual([])
    // Now let the load resolve — its result must be discarded
    resolveLoad()
    await p
    expect(series.value).toEqual([])
  })

  it('assigns colors from palette to each loaded series', async () => {
    getTrendData.mockResolvedValue({ granularity: 'HOUR', dataPoints: [], summary: null, avg5: [], avg10: [], avg20: [] })
    getTrendMetadata.mockResolvedValue({ unit: '°C' })
    detectAnomalies.mockResolvedValue([])

    const { load, series } = useTrendSeriesLoader()
    await load([10, 20, 30], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')

    const colors = series.value.map(s => s.color)
    expect(new Set(colors).size).toBe(3)        // all distinct
    expect(colors[0]).toMatch(/^#[0-9a-fA-F]{6}$/)
  })

  /**
   * Race protection: when user adds monitors one-by-one in el-select multiple,
   * @change fires for each click — multiple load() calls overlap. Without a
   * token guard, the slowest load resolves last and overwrites the latest
   * result. With the guard, only the most-recent load() can apply its result.
   */
  it('race protection: stale load() result is discarded', async () => {
    // First load (1 monitor) resolves slowly; second load (3 monitors) resolves fast.
    // Without the token guard, the slow first load would overwrite the second's result.
    getTrendData.mockImplementation(({ monitorId }) =>
      new Promise(resolve => {
        const delay = monitorId === 1 ? 100 : 5
        setTimeout(() => resolve({
          granularity: 'HOUR',
          dataPoints: [{ time: '2026-06-01 00:00:00', avg: monitorId * 10, max: 0, min: 0, sampleCount: 1 }],
          summary: null, avg5: [], avg10: [], avg20: [],
        }), delay)
      }),
    )
    getTrendMetadata.mockImplementation(({ monitorId }) =>
      Promise.resolve({ unit: '°C', caption: `m-${monitorId}` }),
    )
    detectAnomalies.mockResolvedValue([])

    const { load, series } = useTrendSeriesLoader()

    // Fire two loads concurrently — first one is slower
    const p1 = load([1], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')
    const p2 = load([2, 3, 4], 'PROBE', ['2026-06-01 00:00:00', '2026-06-02 00:00:00'], 'HOUR')
    await Promise.all([p1, p2])

    // Latest load wins — series has 3 monitors, not 1
    expect(series.value).toHaveLength(3)
    expect(series.value.map(s => s.id)).toEqual([2, 3, 4])
  })
})
