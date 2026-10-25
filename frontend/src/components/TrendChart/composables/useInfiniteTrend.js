import { ref, onUnmounted } from 'vue'
import { getTrendData, getTrendDefault } from '@/api/iot/trend'
import { formatMonitorDateTime } from '@/config/monitor'

const EDGE_THRESHOLD = 10
const DEBOUNCE_MS = 300
const MAX_BUFFER_POINTS = 2000 // client perf budget: pan/zoom stays smooth at <=2000 points
const GRANULARITY_STABLE_THRESHOLD = 2

/** Granularity thresholds in seconds — matches TrendService.determineGranularity() */
const GRAN_SECONDS = {
  INTRADAY: 86_400,
  HOUR    : 2_678_400,
  DAY     : 15_552_000,
  WEEK    : 63_072_000,
}

function granularityForSeconds(secs) {
  if (secs <= GRAN_SECONDS.INTRADAY) return 'INTRADAY'
  if (secs <= GRAN_SECONDS.HOUR) return 'HOUR'
  if (secs <= GRAN_SECONDS.DAY) return 'DAY'
  if (secs <= GRAN_SECONDS.WEEK) return 'WEEK'
  return 'MONTH'
}

/**
 * Composable managing infinite-pan trend data with auto-granularity switching.
 *
 * Holds the unified backend payload (Task 4 contract):
 * `{ granularity, mode, points, bucketSeconds, summary }` where each point is
 * `{ t, avg, max, min, open, close, count }`.
 *
 * @param {Object} opts
 * @param {import('vue').Ref<number>} opts.monitorId
 * @param {import('vue').Ref<string>|import('vue').ComputedRef<string>} opts.monitorKind
 * @param {import('vue').Ref<string>} opts.unit
 * @param {import('vue').Ref<number>} opts.detectIntervalSeconds
 * @param {Function} [opts.onGranularityChange] - called when auto-switch occurs
 * @param {Function} [opts.onDateRangeChange] - called to sync toolbar date picker
 */
export function useInfiniteTrend(opts) {
  const {
    monitorId, monitorKind, unit, detectIntervalSeconds,
    onGranularityChange, onDateRangeChange,
  } = opts

  const points        = ref([])
  const mode          = ref('RAW')
  const bucketSeconds = ref(null)
  const summary       = ref(null)
  const granularity   = ref('HOUR')
  const loading       = ref(false)
  const bufferStartTime = ref(null)
  const bufferEndTime   = ref(null)

  let loadToken       = 0
  let abortController = null
  let debounceTimer   = null
  let lastComputedGran = null
  let granStableCount  = 0

  const MIN_FETCH_SPAN_SECONDS = 3600 // 1 hour minimum to prevent zero-span loops

  onUnmounted(() => {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = null
    if (abortController) abortController.abort()
    abortController = null
  })

  // ---- Default view load ----

  async function loadDefault(monitorKindVal) {
    if (!monitorId.value) return
    const myToken = ++loadToken
    loading.value = true
    if (abortController) abortController.abort()
    abortController = new AbortController()
    const signal = abortController.signal
    try {
      const res = await getTrendDefault({
        monitorId: monitorId.value,
        monitorKind: monitorKindVal,
      }, { signal })
      if (myToken !== loadToken) return
      const payload = res.data || res
      applyResponse(payload)
      if (payload.granularity && payload.granularity !== granularity.value) {
        granularity.value = payload.granularity
        onGranularityChange?.(payload.granularity)
      }
    } catch (e) {
      if (myToken !== loadToken) return
      if (e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
      console.error('useInfiniteTrend loadDefault failed:', e)
      clearBuffer()
    } finally {
      if (myToken === loadToken) loading.value = false
      if (abortController?.signal === signal) abortController = null
    }
  }

  // ---- Initial load ----

  async function loadInitial(startTime, endTime, gran) {
    if (!monitorId.value) return
    const myToken = ++loadToken
    loading.value = true
    try {
      const res = await fetchRange(startTime, endTime, gran || granularity.value)
      if (myToken !== loadToken) return
      applyResponse(res)
      if (gran && gran !== granularity.value) {
        granularity.value = gran
        onGranularityChange?.(gran)
      }
    } catch (e) {
      if (myToken !== loadToken) return
      if (e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
      console.error('useInfiniteTrend loadInitial failed:', e)
      clearBuffer()
    } finally {
      if (myToken === loadToken) loading.value = false
    }
  }

  // ---- dataZoom event handler ----

  function handleDataZoomEvent(event) {
    const { start, end } = event
    // Debounce edge check + granularity switch
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      checkEdgeAndFetch(start, end)
      checkGranularitySwitch(start, end)
    }, DEBOUNCE_MS)
  }

  // ---- Edge detection & fetch ----

  function checkEdgeAndFetch(startPct, endPct) {
    if (loading.value) return
    if (startPct < EDGE_THRESHOLD && bufferStartTime.value) {
      fetchOlder()
    } else if (endPct > 100 - EDGE_THRESHOLD && bufferEndTime.value) {
      fetchNewer()
    }
  }

  async function fetchOlder() {
    if (!bufferStartTime.value || !bufferEndTime.value) return
    const myToken = ++loadToken
    loading.value = true
    try {
      const span = dateDiffSeconds(bufferStartTime.value, bufferEndTime.value)
      if (span < MIN_FETCH_SPAN_SECONDS) return
      const newStart = new Date(new Date(bufferStartTime.value.replace(' ', 'T')).getTime() - span * 1000)
      const newStartStr = formatMonitorDateTime(newStart)
      const res = await fetchRange(newStartStr, bufferEndTime.value, granularity.value)
      if (myToken !== loadToken) return
      applyResponse(res, 'older')
    } catch (e) {
      if (myToken !== loadToken) return
      if (e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
      console.error('fetchOlder failed:', e)
    } finally {
      if (myToken === loadToken) loading.value = false
    }
  }

  async function fetchNewer() {
    if (!bufferStartTime.value || !bufferEndTime.value) return
    const myToken = ++loadToken
    loading.value = true
    try {
      const span = dateDiffSeconds(bufferStartTime.value, bufferEndTime.value)
      if (span < MIN_FETCH_SPAN_SECONDS) return
      const newEnd = new Date(new Date(bufferEndTime.value.replace(' ', 'T')).getTime() + span * 1000)
      const now = new Date()
      const newEndStr = formatMonitorDateTime(newEnd > now ? now : newEnd)
      const res = await fetchRange(bufferStartTime.value, newEndStr, granularity.value)
      if (myToken !== loadToken) return
      applyResponse(res, 'newer')
    } catch (e) {
      if (myToken !== loadToken) return
      if (e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
      console.error('fetchNewer failed:', e)
    } finally {
      if (myToken === loadToken) loading.value = false
    }
  }

  // ---- Granularity auto-switch ----

  function checkGranularitySwitch(startPct, endPct) {
    if (points.value.length < 2) return

    const visible = getVisibleTimeRange(startPct, endPct)
    if (!visible) return

    onDateRangeChange?.([formatMonitorDateTime(new Date(visible.startTime.replace(' ', 'T'))),
                         formatMonitorDateTime(new Date(visible.endTime.replace(' ', 'T')))])

    const visibleSecs = dateDiffSeconds(visible.startTime, visible.endTime)
    const computed = granularityForSeconds(visibleSecs)

    if (computed === granularity.value) {
      granStableCount = 0
      lastComputedGran = null
      return
    }

    if (computed === lastComputedGran) {
      granStableCount++
    } else {
      lastComputedGran = computed
      granStableCount = 1
    }

    if (granStableCount >= GRANULARITY_STABLE_THRESHOLD) {
      switchGranularity(computed, visible)
      granStableCount = 0
      lastComputedGran = null
    }
  }

  async function switchGranularity(newGran, visibleRange) {
    const start = visibleRange.startTime || bufferStartTime.value
    const end = visibleRange.endTime || bufferEndTime.value
    if (!start || !end) return

    await loadInitial(start, end, newGran)
  }

  // ---- Visible time range calculation ----

  function getVisibleTimeRange(startPct, endPct) {
    const pts = points.value
    if (pts.length === 0) return null

    const startIdx = Math.max(0, Math.floor(startPct / 100 * pts.length))
    const endIdx = Math.min(pts.length - 1, Math.ceil(endPct / 100 * pts.length) - 1)

    const startTime = pts[startIdx]?.t
    const endTime = pts[endIdx]?.t
    if (!startTime || !endTime) return null

    return { startTime, endTime }
  }

  // ---- Data fetch helper ----

  async function fetchRange(startTime, endTime, gran) {
    if (abortController) abortController.abort()
    abortController = new AbortController()
    try {
      const res = await getTrendData({
        monitorId: monitorId.value,
        monitorKind: monitorKind.value,
        startTime,
        endTime,
        granularity: gran,
      }, { signal: abortController.signal })
      return res.data || res
    } finally {
      abortController = null
    }
  }

  // ---- Apply response ----

  /**
   * Apply a unified backend payload wholesale: the server returns the full
   * requested window, so replacing `points` is how older/newer fetches
   * concatenate — the fetched window is a superset of the old buffer.
   *
   * @param {string} [direction] - 'older' | 'newer'; tells trimBuffer which
   *   edge the fetch just extended so the cap retains the newly loaded side.
   */
  function applyResponse(payload, direction) {
    if (!payload) { clearBuffer(); return }
    if (payload.granularity) granularity.value = payload.granularity
    points.value = payload.points ?? []
    mode.value = payload.mode ?? 'RAW'
    bucketSeconds.value = payload.bucketSeconds ?? null
    summary.value = payload.summary ?? null
    updateBufferBounds()
    trimBuffer(direction)
  }

  function updateBufferBounds() {
    const pts = points.value
    if (pts.length === 0) {
      bufferStartTime.value = null
      bufferEndTime.value = null
      return
    }
    bufferStartTime.value = pts[0].t
    bufferEndTime.value = pts[pts.length - 1].t
  }

  // ---- Buffer trimming ----

  /**
   * Bound the client buffer to MAX_BUFFER_POINTS, retaining the edge the
   * fetch just extended: an 'older' fetch keeps the oldest window (dropping
   * the off-screen newest tail), anything else keeps the newest window
   * (dropping the off-screen oldest head). A head-trim on an older fetch
   * would discard exactly the data just loaded, pinning bufferStartTime and
   * re-fetching the identical window on every edge pan past the cap.
   */
  function trimBuffer(direction) {
    if (points.value.length <= MAX_BUFFER_POINTS) return
    if (direction === 'older') {
      points.value = points.value.slice(0, MAX_BUFFER_POINTS)
    } else {
      const excess = points.value.length - MAX_BUFFER_POINTS
      points.value = points.value.slice(excess)
    }
    updateBufferBounds()
  }

  // ---- Real-time point append (INTRADAY WebSocket) ----

  function appendRealtimePoint(point) {
    // Raw WebSocket samples only belong to the INTRADAY view; aggregated
    // granularities (HOUR/DAY/...) are server-bucketed and must not be
    // polluted by native-interval points.
    if (granularity.value !== 'INTRADAY') return
    const p = point
    points.value.push({
      t: p.time ?? p.t,
      avg: p.value ?? p.avg,
      max: p.value ?? p.avg,
      min: p.value ?? p.avg,
      open: p.value ?? p.avg,
      close: p.value ?? p.avg,
      count: 1,
    })
    trimBuffer()
  }

  // ---- Clear ----

  function clearBuffer() {
    points.value = []
    mode.value = 'RAW'
    bucketSeconds.value = null
    summary.value = null
    bufferStartTime.value = null
    bufferEndTime.value = null
  }

  // ---- Utility ----

  function dateDiffSeconds(startStr, endStr) {
    const s = new Date(String(startStr).replace(' ', 'T'))
    const e = new Date(String(endStr).replace(' ', 'T'))
    if (isNaN(s.getTime()) || isNaN(e.getTime())) return 0
    return Math.max(0, (e.getTime() - s.getTime()) / 1000)
  }

  return {
    points, mode, bucketSeconds,
    summary, granularity, loading,
    bufferStartTime, bufferEndTime,
    loadDefault, loadInitial, handleDataZoomEvent, checkEdgeAndFetch,
    fetchOlder, fetchNewer, checkGranularitySwitch, switchGranularity,
    getVisibleTimeRange, fetchRange, appendRealtimePoint, clearBuffer,
    applyResponse,
    /** Exposed for unit testing */
    _granularityForSeconds: granularityForSeconds,
    _trimBuffer: trimBuffer,
  }
}
