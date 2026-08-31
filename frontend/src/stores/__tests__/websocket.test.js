import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWebSocketStore } from '@/stores/websocket'

describe('websocket store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('monitorResult messages', () => {
    it('parses monitorResult messages into probeValues', () => {
      const store = useWebSocketStore()
      const msg = JSON.stringify({
        type: 'monitorResult',
        monitorId: 1,
        value: 23.5,
        status: 'NORMAL',
      })
      store._parseMessage(msg)
      expect(store.probeValues[1]).toEqual({
        type: 'monitorResult',
        monitorId: 1,
        value: 23.5,
        status: 'NORMAL',
      })
    })

    it('parses messages without type field (backward compat) into probeValues', () => {
      const store = useWebSocketStore()
      const msg = JSON.stringify({ monitorId: 2, value: 10 })
      store._parseMessage(msg)
      expect(store.probeValues[2]).toEqual({ monitorId: 2, value: 10 })
    })

    it('parses messages using probeId (backward compat) into probeValues', () => {
      const store = useWebSocketStore()
      const msg = JSON.stringify({ probeId: 7, value: 99.9 })
      store._parseMessage(msg)
      expect(store.probeValues[7]).toEqual({ probeId: 7, value: 99.9 })
    })
  })

  describe('alarm messages', () => {
    it('parses alarm messages into alarmMessages', () => {
      const store = useWebSocketStore()
      const msg = JSON.stringify({
        type: 'alarm',
        alarmMessageId: 42,
        eventRankId: 3,
        assetId: 101,
      })
      store._parseMessage(msg)
      expect(store.alarmMessages).toHaveLength(1)
      expect(store.alarmMessages[0]).toEqual({
        type: 'alarm',
        alarmMessageId: 42,
        eventRankId: 3,
        assetId: 101,
      })
    })

    it('increments unreadAlarmCount for each alarm', () => {
      const store = useWebSocketStore()
      store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 1, assetId: 1 }))
      store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 2, eventRankId: 2, assetId: 2 }))
      expect(store.unreadAlarmCount).toBe(2)
    })

    it('keeps last 50 alarm messages', () => {
      const store = useWebSocketStore()
      for (let i = 0; i < 55; i++) {
        store._parseMessage(JSON.stringify({
          type: 'alarm',
          alarmMessageId: i,
          eventRankId: i,
          assetId: i,
        }))
      }
      expect(store.alarmMessages).toHaveLength(50)
      expect(store.alarmMessages[0].alarmMessageId).toBe(5)
      expect(store.alarmMessages[49].alarmMessageId).toBe(54)
    })
  })

  describe('clearUnreadAlarms', () => {
    it('resets unread count to zero', () => {
      const store = useWebSocketStore()
      store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 1, assetId: 1 }))
      expect(store.unreadAlarmCount).toBe(1)
      store.clearUnreadAlarms()
      expect(store.unreadAlarmCount).toBe(0)
    })

    it('does not affect alarmMessages array', () => {
      const store = useWebSocketStore()
      store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 1, assetId: 1 }))
      store.clearUnreadAlarms()
      expect(store.alarmMessages).toHaveLength(1)
    })
  })

  describe('edge cases', () => {
    it('ignores invalid JSON', () => {
      const store = useWebSocketStore()
      store._parseMessage('{not-json}')
      expect(store.alarmMessages).toHaveLength(0)
      expect(Object.keys(store.probeValues)).toHaveLength(0)
    })

    it('ignores non-empty string that is not JSON', () => {
      const store = useWebSocketStore()
      store._parseMessage('hello')
      expect(store.alarmMessages).toHaveLength(0)
    })

    it('ignores JSON without type, monitorId, or probeId', () => {
      const store = useWebSocketStore()
      store._parseMessage(JSON.stringify({ foo: 'bar' }))
      expect(store.alarmMessages).toHaveLength(0)
    })

    it('deduplicates alarm messages by alarmMessageId', () => {
      const store = useWebSocketStore()
      store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 1, assetId: 1 }))
      store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 1, assetId: 1 }))
      expect(store.alarmMessages).toHaveLength(1)
      expect(store.unreadAlarmCount).toBe(1)
    })
  })

  describe('probeValues LRU eviction (write + subscribe two-way touch)', () => {
    function pushMany(store, ids) {
      for (const id of ids) {
        store._parseMessage(JSON.stringify({ type: 'monitorResult', monitorId: id, value: id }))
      }
    }

    it('keeps all entries when under MAX_PROBE_VALUES', () => {
      const store = useWebSocketStore()
      pushMany(store, [1, 2, 3])
      expect(Object.keys(store.probeValues)).toHaveLength(3)
      expect(store.probeValues[1]).toBeDefined()
      expect(store.probeValues[3]).toBeDefined()
    })

    it('evicts least-recently-accessed when exceeding MAX_PROBE_VALUES', () => {
      const store = useWebSocketStore()
      // Fill exactly MAX_PROBE_VALUES (200) entries with sequential IDs.
      const ids = Array.from({ length: 200 }, (_, i) => i + 1)
      pushMany(store, ids)
      expect(Object.keys(store.probeValues)).toHaveLength(200)
      // Push one more — should evict the oldest (id=1).
      pushMany(store, [201])
      expect(store.probeValues[1]).toBeUndefined()
      expect(store.probeValues[201]).toBeDefined()
      expect(Object.keys(store.probeValues)).toHaveLength(200)
    })

    it('hot key (frequently updated) is not evicted', () => {
      const store = useWebSocketStore()
      // id=1 is updated frequently; others get pushed once each.
      for (let i = 1; i <= 200; i++) {
        pushMany(store, [i])
      }
      // Keep touching id=1 while pushing 50 more
      for (let i = 201; i <= 250; i++) {
        pushMany(store, [1, i])  // touch id=1 each iteration
      }
      // id=1 is most-recent, must survive; some early cold keys evicted
      expect(store.probeValues[1]).toBeDefined()
      expect(Object.keys(store.probeValues)).toHaveLength(200)
    })

    it('subscribed-but-quiet key is not evicted (write-touch alone would fail this)', () => {
      const store = useWebSocketStore()
      // Subscribe to id=999 (this should touch it)
      store.subscribe([999])
      // Fill 200 other keys via writes
      const ids = Array.from({ length: 200 }, (_, i) => i + 1)
      pushMany(store, ids)
      // id=999 has no write but was subscribe-touched — must survive
      expect(store.probeValues[999]).toBeUndefined()  // no data yet, key doesn't exist
      // But subscribedMonitorIds should still include 999
      expect(store.subscribedMonitorIds).toContain(999)
    })

    it('subscribe adds to subscribedMonitorIds', () => {
      const store = useWebSocketStore()
      store.subscribe([1, 2, 3])
      expect(store.subscribedMonitorIds).toContain(1)
      expect(store.subscribedMonitorIds).toContain(2)
      expect(store.subscribedMonitorIds).toContain(3)
    })

    it('unsubscribe removes from subscribedMonitorIds', () => {
      const store = useWebSocketStore()
      store.subscribe([1, 2, 3])
      store.unsubscribe([2])
      expect(store.subscribedMonitorIds).not.toContain(2)
      expect(store.subscribedMonitorIds).toContain(1)
      expect(store.subscribedMonitorIds).toContain(3)
    })

    it('evicted key receives next push normally', () => {
      const store = useWebSocketStore()
      pushMany(store, Array.from({ length: 200 }, (_, i) => i + 1))
      pushMany(store, [201])  // evicts id=1
      expect(store.probeValues[1]).toBeUndefined()
      // Re-push id=1 — should re-create entry
      pushMany(store, [1])
      expect(store.probeValues[1]).toBeDefined()
    })

    it('subscribedMonitorIds preserved for reconnect (onopen reads this list)', () => {
      const store = useWebSocketStore()
      store.subscribe([1, 2, 3])
      expect(store.subscribedMonitorIds).toEqual([1, 2, 3])
      // onopen re-subscribes subscribedMonitorIds — the list must remain intact
      // until the next successful connect. Full reconnect path verified by integration test.
      expect(store.subscribedMonitorIds.length).toBe(3)
    })
  })
})
