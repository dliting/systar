import { ref, onBeforeUnmount } from 'vue'

const DEFAULT_INTERVAL_MS  = 30000
const DEFAULT_HIGHLIGHT_MS = 3000

export function useAutoRefresh(fetchFn, options = {}) {
  const intervalMs        = options.interval ?? DEFAULT_INTERVAL_MS
  const idField           = options.idField ?? 'id'
  const compareField      = options.compareField ?? null
  const highlightDuration = options.highlightDuration ?? DEFAULT_HIGHLIGHT_MS

  const enabled        = ref(true)
  const highlightedIds = ref(new Set())

  let timer           = null
  let refreshing      = false
  let previousData    = new Map()
  let highlightTimers = new Map()

  async function refresh() {
    if (!enabled.value || refreshing) return
    refreshing = true
    try {
      const data = await fetchFn()
      if (!compareField || !Array.isArray(data)) return

      const currentIds    = new Set()
      const newHighlighted = new Set()
      for (const item of data) {
        const id    = item[idField]
        const value = item[compareField]
        if (id == null) continue
        currentIds.add(id)
        if (previousData.has(id) && previousData.get(id) !== value) {
          newHighlighted.add(id)
        }
        previousData.set(id, value)
      }

      for (const id of previousData.keys()) {
        if (!currentIds.has(id)) previousData.delete(id)
      }

      for (const id of newHighlighted) {
        highlightedIds.value = new Set([...highlightedIds.value, id])
        if (highlightTimers.has(id)) clearTimeout(highlightTimers.get(id))
        const t = setTimeout(() => {
          highlightedIds.value = new Set([...highlightedIds.value].filter(x => x !== id))
          highlightTimers.delete(id)
        }, highlightDuration)
        highlightTimers.set(id, t)
      }
    } catch (e) {
      console.warn('[useAutoRefresh] fetch failed:', e?.message || e)
    } finally {
      refreshing = false
    }
  }

  function start() {
    stop()
    if (!enabled.value) return
    refresh()
    timer = setInterval(() => { if (enabled.value) refresh() }, intervalMs)
  }

  function stop() {
    if (timer) { clearInterval(timer); timer = null }
    for (const t of highlightTimers.values()) clearTimeout(t)
    highlightTimers.clear()
    previousData.clear()
    highlightedIds.value = new Set()
  }

  try { onBeforeUnmount(() => stop()) } catch { /* not in component context */ }

  return { enabled, highlightedIds, start, stop }
}
