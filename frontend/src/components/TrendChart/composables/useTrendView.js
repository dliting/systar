/**
 * Page-level trend orchestration (spec F4): granularity direct-select (D6),
 * relative presets, custom window, retry, and a load-token guard so a slow
 * stale response can never overwrite a newer one.
 */
import { ref } from 'vue'
import { getTrendData } from '@/api/iot/trend'

export const GRANULARITY_CHOICES = [
  { value: 'AUTO', label: '自动' },
  { value: 'INTRADAY', label: '分时' },
  { value: 'HOUR', label: '小时' },
  { value: 'DAY', label: '日' },
  { value: 'WEEK', label: '周' },
  { value: 'MONTH', label: '月' },
]

export const PRESETS = [
  { key: '1h', label: '近1小时', seconds: 3600 },
  { key: '6h', label: '近6小时', seconds: 21600 },
  { key: '24h', label: '近24小时', seconds: 86400 },
  { key: '7d', label: '近7天', seconds: 604800 },
  { key: '30d', label: '近30天', seconds: 2592000 },
  { key: '1y', label: '近1年', seconds: 31536000 },
  { key: 'all', label: '全部', seconds: 315360000 },
]

export const DEFAULT_RANGE_SECONDS = {
  INTRADAY: 86400, HOUR: 604800, DAY: 2592000, WEEK: 15552000, MONTH: 63072000,
}

const pad = (n) => String(n).padStart(2, '0')
const fmtLocal = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
  `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

export function windowForChoice(granularityChoice, presetKey) {
  const preset = PRESETS.find((p) => p.key === presetKey)
  const seconds = preset?.seconds ?? DEFAULT_RANGE_SECONDS[granularityChoice] ?? 604800
  const end = new Date()
  const start = new Date(end.getTime() - seconds * 1000)
  return { start: fmtLocal(start), end: fmtLocal(end) }
}

export function useTrendView({ monitorId, monitorKind }) {
  const state = ref('idle')
  const error = ref('')
  const points = ref([])
  const mode = ref('RAW')
  const bucketSeconds = ref(null)
  const summary = ref(null)
  const granularity = ref('INTRADAY')
  const granularityChoice = ref('AUTO')
  const rangeChoice = ref('24h')
  const customRange = ref(null)
  let loadToken = 0

  async function load() {
    const token = ++loadToken
    state.value = 'loading'
    error.value = ''
    const win = customRange.value
      ? { start: customRange.value[0], end: customRange.value[1] }
      : windowForChoice(granularityChoice.value, rangeChoice.value)
    try {
      const resp = await getTrendData({
        monitorId: monitorId?.value ?? monitorId,
        monitorKind: monitorKind?.value ?? monitorKind,
        startTime: win.start,
        endTime: win.end,
        granularity: granularityChoice.value === 'AUTO' ? null : granularityChoice.value,
      })
      if (token !== loadToken) return
      // The controller wraps the VO in Result<T> — unwrap like useInfiniteTrend does.
      const payload = resp?.data ?? resp
      points.value = payload.points ?? []
      mode.value = payload.mode ?? 'RAW'
      bucketSeconds.value = payload.bucketSeconds ?? null
      summary.value = payload.summary ?? null
      granularity.value = payload.granularity ?? granularity.value
      state.value = points.value.length ? 'ready' : 'empty'
    } catch (e) {
      if (token !== loadToken) return
      console.error('Failed to load trend', e)
      error.value = '加载失败，请重试'
      state.value = 'error'
    }
  }

  function setGranularityChoice(choice) {
    granularityChoice.value = choice
    load()
  }
  function setPreset(key) {
    rangeChoice.value = key
    customRange.value = null
    load()
  }
  function setCustomRange(start, end) {
    customRange.value = [start, end]
    rangeChoice.value = 'custom'
    load()
  }

  return {
    state, error, points, mode, bucketSeconds, summary, granularity,
    granularityChoice, rangeChoice, customRange,
    setGranularityChoice, setPreset, setCustomRange,
    refresh: load, retry: load, load,
  }
}
