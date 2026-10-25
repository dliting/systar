import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { windowForChoice, PRESETS, GRANULARITY_CHOICES, useTrendView } from '../useTrendView'

vi.mock('@/api/iot/trend', () => ({ getTrendData: vi.fn() }))
import { getTrendData } from '@/api/iot/trend'

const ok = (points, extra = {}) => ({
  granularity: 'HOUR', mode: 'OHLC', points, bucketSeconds: 3600,
  summary: { currentValue: 1, periodMax: 2, periodMin: 0, totalSamples: 3 }, ...extra,
})
const P = [{ t: '2026-10-25 00:00:00', avg: 1, max: 2, min: 0, open: 0, close: 2, count: 3 }]

describe('windowForChoice', () => {
  it('maps presets to relative windows', () => {
    const { start, end } = windowForChoice('AUTO', '7d')
    const secs = (new Date(end.replace(' ', 'T')) - new Date(start.replace(' ', 'T'))) / 1000
    expect(secs).toBe(604800)
  })
  it('falls back to the granularity default when no preset', () => {
    const { start, end } = windowForChoice('MONTH', null)
    const secs = (new Date(end.replace(' ', 'T')) - new Date(start.replace(' ', 'T'))) / 1000
    expect(secs).toBe(63072000)
  })
  it('exposes SCADA labels only', () => {
    for (const p of PRESETS) expect(p.label).toMatch(/^近|全部/)
    expect(GRANULARITY_CHOICES.map((c) => c.value)).toEqual(['AUTO', 'INTRADAY', 'HOUR', 'DAY', 'WEEK', 'MONTH'])
  })
})

describe('useTrendView', () => {
  beforeEach(() => vi.clearAllMocks())

  it('idle → loading → ready on success', async () => {
    vi.mocked(getTrendData).mockResolvedValueOnce(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    expect(v.state.value).toBe('idle')
    const p = v.load()
    expect(v.state.value).toBe('loading')
    await p
    expect(v.state.value).toBe('ready')
    expect(v.points.value).toHaveLength(1)
    expect(v.mode.value).toBe('OHLC')
  })

  it('empty window → empty state, not error (Review Focus 2)', async () => {
    vi.mocked(getTrendData).mockResolvedValueOnce(ok([]))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    await v.load()
    expect(v.state.value).toBe('empty')
    expect(v.error.value).toBe('')
  })

  it('failure → error state with logged cause and retry recovers', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      vi.mocked(getTrendData).mockRejectedValueOnce(new Error('boom'))
      const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
      await v.load()
      expect(v.state.value).toBe('error')
      expect(v.error.value).toContain('加载失败')
      expect(spy).toHaveBeenCalledWith('Failed to load trend', expect.any(Error))
      vi.mocked(getTrendData).mockResolvedValueOnce(ok(P))
      await v.retry()
      expect(v.state.value).toBe('ready')
    } finally { spy.mockRestore() }
  })

  it('discards a stale response when a newer load started (Review Focus 4)', async () => {
    let resolveFirst
    vi.mocked(getTrendData)
      .mockImplementationOnce(() => new Promise((r) => { resolveFirst = r }))
      .mockImplementationOnce(async () => ok(P, { granularity: 'DAY' }))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    const first = v.load()
    v.setGranularityChoice('DAY') // starts load #2 while #1 is in flight
    await new Promise((r) => setTimeout(r, 0)) // let load #2's request fire before #1 resolves
    resolveFirst(ok(P, { granularity: 'INTRADAY' })) // #1 lands late with the wrong granularity
    await first
    expect(v.granularity.value).toBe('DAY') // #2 wins; #1 was discarded
  })

  it('AUTO sends granularity null; explicit choices pass through', async () => {
    vi.mocked(getTrendData).mockResolvedValue(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    await v.load()
    expect(vi.mocked(getTrendData).mock.lastCall[0].granularity).toBeNull()
    await v.setGranularityChoice('DAY')
    expect(vi.mocked(getTrendData).mock.lastCall[0].granularity).toBe('DAY')
  })

  // The backend clamps an explicit granularity down the ladder when the
  // window cannot hold even one bucket of it (TrendService.resolveGranularity:
  // DAY over 24h silently becomes HOUR) — the toolbar would keep reading 日
  // while the chart shows hourly data. Extend to the granularity default.
  it('extends a too-short window when switching to a coarser granularity', async () => {
    vi.mocked(getTrendData).mockResolvedValue(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    await v.setGranularityChoice('DAY') // default preset is 24h — one DAY bucket at most
    const call = vi.mocked(getTrendData).mock.lastCall[0]
    const secs = (new Date(call.endTime.replace(' ', 'T')) - new Date(call.startTime.replace(' ', 'T'))) / 1000
    expect(secs).toBe(2592000) // DAY default (30d)
  })

  it('keeps a window that already holds buckets of the explicit granularity', async () => {
    vi.mocked(getTrendData).mockResolvedValue(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    v.rangeChoice.value = '7d'
    await v.setGranularityChoice('DAY')
    const call = vi.mocked(getTrendData).mock.lastCall[0]
    const secs = (new Date(call.endTime.replace(' ', 'T')) - new Date(call.startTime.replace(' ', 'T'))) / 1000
    expect(secs).toBe(604800) // 7d holds 7 DAY buckets — user's window stands
  })

  // Reverse direction of the guard above: shrinking the window below the
  // explicit choice's bucket floor (小时 + 近1小时, 日 + 近1小时) would leave
  // the backend clamping the data one rung down while the select still reads
  // the pick — demote the choice to AUTO so the toolbar tracks what renders.
  it('demotes an explicit granularity to AUTO when a preset window cannot hold it', async () => {
    vi.mocked(getTrendData).mockResolvedValue(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    await v.setGranularityChoice('HOUR') // 24h default preset holds HOUR buckets — choice stands
    expect(vi.mocked(getTrendData).mock.lastCall[0].granularity).toBe('HOUR')
    v.setPreset('1h') // 3600s span == one HOUR bucket — backend would clamp to INTRADAY
    await vi.waitFor(() => expect(v.state.value).toBe('ready'))
    expect(vi.mocked(getTrendData).mock.lastCall[0].granularity).toBeNull()
    expect(v.granularityChoice.value).toBe('AUTO')
  })

  it('demotes an explicit granularity to AUTO for a too-short custom range', async () => {
    vi.mocked(getTrendData).mockResolvedValue(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    await v.setGranularityChoice('DAY') // window extended to the 30d DAY default
    v.setCustomRange('2026-10-24 00:00:00', '2026-10-24 12:00:00') // 12h < one DAY bucket
    await vi.waitFor(() => expect(v.state.value).toBe('ready'))
    expect(vi.mocked(getTrendData).mock.lastCall[0].granularity).toBeNull()
    expect(v.granularityChoice.value).toBe('AUTO')
  })

  it('custom range overrides presets', async () => {
    vi.mocked(getTrendData).mockResolvedValue(ok(P))
    const v = useTrendView({ monitorId: ref(1), monitorKind: ref('PROBE') })
    v.setCustomRange('2026-01-01 00:00:00', '2026-01-02 00:00:00')
    await vi.waitFor(() => expect(v.state.value).toBe('ready'))
    const call = vi.mocked(getTrendData).mock.lastCall[0]
    expect(call.startTime).toBe('2026-01-01 00:00:00')
    expect(call.endTime).toBe('2026-01-02 00:00:00')
  })
})
