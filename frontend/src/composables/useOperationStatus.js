import { ref, onBeforeUnmount } from 'vue'

const STATUS_IDLE    = 'idle'
const STATUS_LOADING = 'loading'
const STATUS_SUCCESS = 'success'
const STATUS_FAILED  = 'failed'
const STATUS_TIMEOUT = 'timeout'

const DEFAULT_TIMEOUT_MS = 30000

/**
 * Tracks per-asset operation status with optional WS completion detection.
 *
 * @param {Object} [options]
 * @param {number} [options.timeoutMs=30000] - default timeout for WS watching
 * @returns {{ execute, watchForCompletion, getStatus, isLoading, clearStatus, clearAll }}
 */
export function useOperationStatus(options = {}) {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS

  const statuses  = ref({})
  const watchers  = new Map() // assetId -> { timer, pollTimer }

  function getStatus(id) {
    return statuses.value[id] ?? STATUS_IDLE
  }

  function isLoading(id) {
    return getStatus(id) === STATUS_LOADING
  }

  function setStatus(id, status) {
    statuses.value = { ...statuses.value, [id]: status }
  }

  /**
   * Execute an API call and track its status.
   * @param {number} id - asset ID
   * @param {Function} apiFn - () => Promise
   * @returns {Promise<*>} result of apiFn
   */
  async function execute(id, apiFn) {
    setStatus(id, STATUS_LOADING)
    try {
      const result = await apiFn()
      setStatus(id, STATUS_SUCCESS)
      return result
    } catch (e) {
      setStatus(id, STATUS_FAILED)
    }
  }

  /**
   * Watch for WS-driven completion (e.g., detect refresh).
   * Polls wsGetter at regular intervals; resolves to 'success' when wsGetter returns truthy.
   * Falls back to 'timeout' after timeoutMs.
   *
   * @param {number} id - asset ID
   * @param {Function} wsGetter - () => value from WS store (truthy = completed)
   * @param {number} [customTimeoutMs] - override timeout
   */
  function watchForCompletion(id, wsGetter, customTimeoutMs) {
    stopWatching(id)
    setStatus(id, STATUS_LOADING)

    let resolved = false

    const timer = setTimeout(() => {
      if (!resolved) {
        resolved = true
        setStatus(id, STATUS_TIMEOUT)
        cleanup(id)
      }
    }, customTimeoutMs ?? timeoutMs)

    // Check immediately in case the WS value is already truthy
    const initialValue = wsGetter()
    if (initialValue) {
      resolved = true
      setStatus(id, STATUS_SUCCESS)
      clearTimeout(timer)
      return
    }

    // Poll the WS getter every 500ms; check result synchronously
    const pollTimer = setInterval(() => {
      if (resolved) return
      const val = wsGetter()
      if (val) {
        resolved = true
        setStatus(id, STATUS_SUCCESS)
        cleanup(id)
      }
    }, 500)

    watchers.set(id, { timer, pollTimer })
  }

  function cleanup(id) {
    const w = watchers.get(id)
    if (!w) return
    clearTimeout(w.timer)
    clearInterval(w.pollTimer)
    watchers.delete(id)
  }

  function stopWatching(id) {
    cleanup(id)
  }

  function clearStatus(id) {
    stopWatching(id)
    statuses.value = { ...statuses.value, [id]: STATUS_IDLE }
  }

  function clearAll() {
    for (const id of watchers.keys()) {
      stopWatching(id)
    }
    statuses.value = {}
  }

  onBeforeUnmount(() => {
    for (const id of watchers.keys()) {
      stopWatching(id)
    }
  })

  return { execute, watchForCompletion, getStatus, isLoading, clearStatus, clearAll }
}
