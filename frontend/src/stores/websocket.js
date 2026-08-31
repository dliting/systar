import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken } from '@/utils/auth'

const MAX_ALARM_MESSAGES = 50

/**
 * Max probe-value keys kept in memory; further entries evict least-recently-accessed.
 * Based on: single-page monitors usually ≤ 50, operations list pagination ≤ 100,
 * leaving headroom of 200. If raised to 1000+, evictIfNeeded should switch to a
 * linked-list to avoid O(n log n) sort per write.
 */
const MAX_PROBE_VALUES = 200

export const useWebSocketStore = defineStore('websocket', () => {
  const connected        = ref(false)
  const probeValues      = ref({})
  const alarmMessages    = ref([])
  const unreadAlarmCount = ref(0)
  let ws = null
  let reconnectTimer = null
  let reconnectDelay = 2000  // exponential backoff: 2s → 4s → 8s → ... cap 30s

  /**
   * Internal last-access timestamp map for LRU.
   * Intentionally NOT reactive — it's pure bookkeeping, no consumer needs to
   * re-render when access times update.
   *
   * Touch happens from two paths:
   *   - writeProbeValue: backend just pushed → data flow is active
   *   - subscribe:       user explicitly subscribed → user is viewing (strong signal)
   * Reading probeValues.value[id] does NOT touch — Vue watch getter would
   * otherwise trigger spurious touches every re-evaluation.
   */
  const _lastAccess = new Map()  // monitorId → last-access timestamp (ms)

  function touchProbeAccess(id) {
    _lastAccess.set(id, Date.now())
  }

  function evictIfNeeded() {
    // Single-pass O(n) min-scan instead of O(n log n) sort — eviction typically
    // removes 1-2 entries at a time, so finding the minimum repeatedly is cheaper
    // than sorting the entire map.
    while (_lastAccess.size > MAX_PROBE_VALUES) {
      let minId    = null
      let minTime  = Infinity
      for (const [id, ts] of _lastAccess) {
        if (ts < minTime) { minTime = ts; minId = id }
      }
      delete probeValues.value[minId]
      _lastAccess.delete(minId)
    }
  }

  function writeProbeValue(id, msg) {
    probeValues.value[id] = msg
    touchProbeAccess(id)
    evictIfNeeded()
  }

  function parseMessage(raw) {
    let data
    try {
      data = JSON.parse(raw)
    } catch (e) {
      return
    }

    if (data.type === 'alarm') {
      if (alarmMessages.value.some(m => m.alarmMessageId === data.alarmMessageId)) {
        return
      }
      alarmMessages.value.push(data)
      unreadAlarmCount.value++
      if (alarmMessages.value.length > MAX_ALARM_MESSAGES) {
        alarmMessages.value.splice(0, alarmMessages.value.length - MAX_ALARM_MESSAGES)
      }
      return
    }

    const id = data.monitorId ?? data.probeId
    if (id !== undefined && data.value !== undefined) {
      writeProbeValue(id, data)
    }
  }

  function connect() {
    const t = getToken()
    if (!t) return
    const url = `${import.meta.env.VITE_WS_URL || '/ws'}?token=${encodeURIComponent(t)}`
    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value  = true
      reconnectDelay   = 2000
      if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
      // Re-subscribe all previously tracked monitor IDs after reconnect.
      // (Network may have been down — gaps in pushed data are expected; UI
      // watchers will pick up the next live push.)
      if (subscribedMonitorIds.value.length > 0) {
        ws.send(JSON.stringify({ action: 'subscribe', monitorIds: subscribedMonitorIds.value }))
        for (const id of subscribedMonitorIds.value) touchProbeAccess(id)
      }
    }

    ws.onmessage = (event) => {
      parseMessage(event.data)
    }

    ws.onclose = () => {
      connected.value = false
      reconnectTimer  = setTimeout(connect, reconnectDelay)
      reconnectDelay  = Math.min(reconnectDelay * 2, 30000)
    }

    // Let onclose handle reconnection; browser fires onclose after onerror naturally
    ws.onerror = () => {}
  }

  const subscribedMonitorIds = ref([])

  function subscribe(monitorIds) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ action: 'subscribe', monitorIds: monitorIds }))
    }
    // Touch on subscribe intent — even when WS not yet open (network down),
    // user has signalled "I am viewing this monitor" so the key is hot.
    // When network recovers, onopen re-subscribes subscribedMonitorIds.
    for (const id of monitorIds) {
      touchProbeAccess(id)
      if (!subscribedMonitorIds.value.includes(id)) {
        subscribedMonitorIds.value.push(id)
      }
    }
  }

  function unsubscribe(monitorIds) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ action: 'unsubscribe', monitorIds: monitorIds }))
    }
    // Do NOT delete _lastAccess entries here — let LRU naturally age them out.
    // Frequent subscribe/unsubscribe/subscribe churn would otherwise repeatedly
    // delete and re-create access entries.
    subscribedMonitorIds.value = subscribedMonitorIds.value.filter(
      id => !monitorIds.includes(id)
    )
  }

  function disconnect() {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
    ws?.close()
    ws = null
    connected.value = false
    subscribedMonitorIds.value = []
    _lastAccess.clear()
    probeValues.value = {}
  }

  function clearUnreadAlarms() {
    unreadAlarmCount.value = 0
  }

  return {
    connected,
    probeValues,
    alarmMessages,
    unreadAlarmCount,
    subscribedMonitorIds,
    connect,
    subscribe,
    unsubscribe,
    disconnect,
    clearUnreadAlarms,
    _parseMessage: parseMessage,
  }
})
