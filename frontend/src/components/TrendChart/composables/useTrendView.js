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

// Mirror of backend TrendService.bucketSecondsFor: an explicit granularity is
// honored only while the span holds more than one bucket of it; below that the
// backend clamps it down the ladder (DAY over 24h silently becomes HOUR) and
// the toolbar selection would contradict the rendered data.
const BUCKET_SECONDS = { HOUR: 3_600, DAY: 86_400, WEEK: 604_800, MONTH: 2_592_000 }

export function windowTooShortFor(choice, win) {
  const bucket = BUCKET_SECONDS[choice]
  if (bucket == null) return false
  const span = (new Date(win.end.replace(' ', 'T')) - new Date(win.start.replace(' ', 'T'))) / 1000
  return span <= bucket
}

// Reverse-direction guard: when a window change (preset/custom/refresh) drops
// the span below the explicit choice's bucket floor, the backend clamps the
// data one rung down the ladder while the select would still read the stale
// pick — fall back to AUTO so the server-resolved granularity drives the
// toolbar too.
export function resolveChoiceForWindow(choice, win) {
  return windowTooShortFor(choice, win) ? 'AUTO' : choice
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
  // 1h keeps the first view in RAW mode (actual measured samples) for any
  // probe interval ≥ 7s: the backend switches to BUCKETED bucket averages
  // above 5000 raw samples, and 24h at a 10s probe is 8640.
  const rangeChoice = ref('1h')
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
    // A window too short for the chosen granularity would be clamped by the
    // backend into a coarser-looking finer view while the select still reads
    // the user's pick — fall back to that granularity's default range instead.
    const win = customRange.value
      ? { start: customRange.value[0], end: customRange.value[1] }
      : windowForChoice(choice, rangeChoice.value)
    if (windowTooShortFor(choice, win)) {
      customRange.value = null
      rangeChoice.value = 'default' // no preset matches → windowForChoice uses the granularity default
    }
    load()
  }
  function setPreset(key) {
    rangeChoice.value = key
    customRange.value = null
    granularityChoice.value = resolveChoiceForWindow(
      granularityChoice.value, windowForChoice(granularityChoice.value, key))
    load()
  }
  function setCustomRange(start, end) {
    customRange.value = [start, end]
    rangeChoice.value = 'custom'
    granularityChoice.value = resolveChoiceForWindow(granularityChoice.value, { start, end })
    load()
  }

  return {
    state, error, points, mode, bucketSeconds, summary, granularity,
    granularityChoice, rangeChoice, customRange,
    setGranularityChoice, setPreset, setCustomRange,
    refresh: load, retry: load, load,
  }
}
