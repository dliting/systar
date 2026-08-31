import { ref, onUnmounted } from 'vue'
import { getTrendData, getTrendDefault } from '@/api/iot/trend'
import { formatMonitorDateTime } from '@/config/monitor'

const EDGE_THRESHOLD = 10
const DEBOUNCE_MS = 300
const MAX_BUFFER_AGGREGATED = 2000
const MAX_BUFFER_INTRADAY = 10000
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

  const dataPoints      = ref([])
  const intradayPoints  = ref([])
  const avg5            = ref([])
  const avg10           = ref([])
  const avg20           = ref([])
  const summary         = ref(null)
  const granularity     = ref('HOUR')
  const loading         = ref(false)
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
    const points = granularity.value === 'INTRADAY' ? intradayPoints.value : dataPoints.value
    if (points.length < 2) return

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
    const points = granularity.value === 'INTRADAY' ? intradayPoints.value : dataPoints.value
    if (points.length === 0) return null

    const startIdx = Math.max(0, Math.floor(startPct / 100 * points.length))
    const endIdx = Math.min(points.length - 1, Math.ceil(endPct / 100 * points.length) - 1)

    const startTime = points[startIdx]?.time
    const endTime = points[endIdx]?.time
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
   * Apply a fetch response and optionally trim the buffer.
   *
   * @param {object} payload - backend response
   * @param {'older'|'newer'|null} fetchDirection
   *   - 'older': user panned to older end; trim NEWER (tail) to bound memory
   *     while keeping the just-fetched older data visible.
   *   - 'newer': user panned to newer end; trim OLDER (head).
   *   - null: initial load or granularity switch; trim head (default).
   *     For INTRADAY real-time append, always trim head.
   */
  function applyResponse(payload, fetchDirection = null) {
    if (!payload) { clearBuffer(); return }
    if (payload.granularity) granularity.value = payload.granularity
    dataPoints.value = payload.dataPoints || []
    intradayPoints.value = payload.intradayPoints || []
    avg5.value = payload.avg5 || []
    avg10.value = payload.avg10 || []
    avg20.value = payload.avg20 || []
    summary.value = payload.summary || null
    updateBufferBounds()
    trimBuffer(fetchDirection)
  }

  function updateBufferBounds() {
    const points = granularity.value === 'INTRADAY' ? intradayPoints.value : dataPoints.value
    if (points.length === 0) {
      bufferStartTime.value = null
      bufferEndTime.value = null
      return
    }
    bufferStartTime.value = points[0].time
    bufferEndTime.value = points[points.length - 1].time
  }

  // ---- Buffer trimming ----

  /**
   * Trim buffer to bound memory.
   *
   * Direction semantics:
   *   - 'older' (user panned to older end): keep HEAD (older, just-fetched),
   *     trim TAIL (newer, far from viewport).
   *   - 'newer' (user panned to newer end): keep TAIL (newer, just-fetched),
   *     trim HEAD (older, far from viewport).
   *   - null: initial load or granularity switch; trim HEAD (default).
   *   - INTRADAY real-time append always trims HEAD (newest at tail, oldest at head).
   *
   * Before this fix, trimBuffer always sliced HEAD, which on fetchOlder would
   * immediately discard the just-fetched older data — defeating the purpose
   * of the fetch.
   */
  function trimBuffer(fetchDirection = null) {
    const maxPoints = granularity.value === 'INTRADAY'
      ? MAX_BUFFER_INTRADAY : MAX_BUFFER_AGGREGATED

    if (granularity.value === 'INTRADAY') {
      if (intradayPoints.value.length <= maxPoints) return
      // INTRADAY appends at tail; always trim head (oldest)
      const excess = intradayPoints.value.length - maxPoints
      intradayPoints.value = intradayPoints.value.slice(excess)
      // avg arrays are normally empty for INTRADAY, but trim if non-empty
      if (avg5.value.length > maxPoints) avg5.value = avg5.value.slice(excess)
      if (avg10.value.length > maxPoints) avg10.value = avg10.value.slice(excess)
      if (avg20.value.length > maxPoints) avg20.value = avg20.value.slice(excess)
      updateBufferBounds()
      return
    }

    if (dataPoints.value.length <= maxPoints) return
    const excess = dataPoints.value.length - maxPoints

    if (fetchDirection === 'older') {
      // Keep first maxPoints (oldest); drop last `excess` (newest)
      dataPoints.value = dataPoints.value.slice(0, maxPoints)
      avg5.value  = avg5.value.slice(0, maxPoints)
      avg10.value = avg10.value.slice(0, maxPoints)
      avg20.value = avg20.value.slice(0, maxPoints)
    } else {
      // 'newer' or null: keep last maxPoints (newest); drop first `excess` (oldest)
      dataPoints.value = dataPoints.value.slice(excess)
      avg5.value  = avg5.value.slice(excess)
      avg10.value = avg10.value.slice(excess)
      avg20.value = avg20.value.slice(excess)
    }
    updateBufferBounds()
  }

  // ---- Real-time point append (INTRADAY WebSocket) ----

  function appendRealtimePoint(point) {
    if (granularity.value !== 'INTRADAY') return
    intradayPoints.value.push(point)
    if (intradayPoints.value.length > MAX_BUFFER_INTRADAY) {
      const excess = intradayPoints.value.length - MAX_BUFFER_INTRADAY
      intradayPoints.value = intradayPoints.value.slice(excess)
    }
    updateBufferBounds()
  }

  // ---- Clear ----

  function clearBuffer() {
    dataPoints.value = []
    intradayPoints.value = []
    avg5.value = []
    avg10.value = []
    avg20.value = []
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
    dataPoints, intradayPoints, avg5, avg10, avg20,
    summary, granularity, loading,
    bufferStartTime, bufferEndTime,
    loadInitial, loadDefault, handleDataZoomEvent, appendRealtimePoint, applyResponse, clearBuffer,
    /** Exposed for unit testing */
    _granularityForSeconds: granularityForSeconds,
    _getVisibleTimeRange: getVisibleTimeRange,
    _trimBuffer: trimBuffer,
  }
}
