import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useAutoRefresh } from '../useAutoRefresh'

describe('useAutoRefresh', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('does not call fetchFn before start', () => {
    const fetchFn = vi.fn()
    useAutoRefresh(fetchFn)
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it('calls fetchFn at interval after start', async () => {
    const fetchFn = vi.fn().mockResolvedValue([])
    const { start, stop } = useAutoRefresh(fetchFn, { interval: 10000 })
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(fetchFn).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchFn).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchFn).toHaveBeenCalledTimes(3)
    stop()
  })

  it('stops calling fetchFn after stop', async () => {
    const fetchFn = vi.fn().mockResolvedValue([])
    const { start, stop } = useAutoRefresh(fetchFn, { interval: 10000 })
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(fetchFn).toHaveBeenCalledTimes(1)
    stop()
    await vi.advanceTimersByTimeAsync(30000)
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('detects changed IDs by comparing id field', async () => {
    let callCount = 0
    const fetchFn = vi.fn().mockImplementation(() => {
      callCount++
      if (callCount === 1) {
        return Promise.resolve([{ id: 1, value: 10 }, { id: 2, value: 20 }])
      }
      return Promise.resolve([{ id: 1, value: 15 }, { id: 2, value: 20 }])
    })
    const { start, stop, highlightedIds } = useAutoRefresh(fetchFn, { interval: 10000, idField: 'id', compareField: 'value' })
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(highlightedIds.value).toEqual(new Set())
    await vi.advanceTimersByTimeAsync(10000)
    expect(highlightedIds.value).toEqual(new Set([1]))
    stop()
  })

  it('clears highlighted IDs after duration', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce([{ id: 1, value: 10 }])
      .mockResolvedValueOnce([{ id: 1, value: 20 }])
      .mockResolvedValueOnce([{ id: 1, value: 20 }])

    const { start, stop, highlightedIds } = useAutoRefresh(fetchFn, {
      interval: 10000, idField: 'id', compareField: 'value', highlightDuration: 3000
    })
    start()
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(10000)
    expect(highlightedIds.value).toEqual(new Set([1]))
    await vi.advanceTimersByTimeAsync(3000)
    expect(highlightedIds.value).toEqual(new Set())
    stop()
  })

  it('enabled ref controls auto-refresh', () => {
    const fetchFn = vi.fn().mockResolvedValue([])
    const { enabled, start } = useAutoRefresh(fetchFn, { interval: 10000 })
    enabled.value = false
    start()
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it('cleans up timers on stop', async () => {
    const fetchFn = vi.fn().mockResolvedValue([])
    const { start, stop } = useAutoRefresh(fetchFn, { interval: 10000 })
    start()
    await vi.advanceTimersByTimeAsync(0)
    stop()
    await vi.advanceTimersByTimeAsync(60000)
    expect(fetchFn).toHaveBeenCalledTimes(1) // only the initial call
  })

  // --- New edge-case tests ---

  it('handles fetchFn returning null without error', async () => {
    const fetchFn = vi.fn().mockResolvedValue(null)
    const { start, stop, highlightedIds } = useAutoRefresh(fetchFn, {
      interval: 10000, compareField: 'value'
    })
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(highlightedIds.value).toEqual(new Set())
    stop()
  })

  it('continues polling after fetchFn rejection', async () => {
    const fetchFn = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([{ id: 1, value: 10 }])
    const { start, stop } = useAutoRefresh(fetchFn, { interval: 10000, compareField: 'value' })
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(fetchFn).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchFn).toHaveBeenCalledTimes(2)
    stop()
  })

  it('prunes removed items from previousData', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce([{ id: 1, value: 10 }, { id: 2, value: 20 }])
      .mockResolvedValueOnce([{ id: 1, value: 15 }])  // id 2 removed
      .mockResolvedValueOnce([{ id: 1, value: 15 }, { id: 2, value: 30 }])  // id 2 reappears
    const { start, stop, highlightedIds } = useAutoRefresh(fetchFn, {
      interval: 10000, compareField: 'value'
    })
    start()
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(10000)
    // id 1 changed → highlighted
    expect(highlightedIds.value).toEqual(new Set([1]))

    await vi.advanceTimersByTimeAsync(10000)
    // id 2 reappears with new value (30) — since it was pruned, it should NOT be highlighted
    expect(highlightedIds.value).toEqual(new Set())
    stop()
  })

  it('skips items missing the idField', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce([{ id: 1, value: 10 }, { value: 20 }])  // second item has no id
      .mockResolvedValueOnce([{ id: 1, value: 15 }, { value: 25 }])
    const { start, stop, highlightedIds } = useAutoRefresh(fetchFn, {
      interval: 10000, compareField: 'value'
    })
    start()
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(10000)
    // Only id 1 is tracked; the item without id is ignored
    expect(highlightedIds.value).toEqual(new Set([1]))
    stop()
  })

  it('does not create duplicate intervals on double start', async () => {
    const fetchFn = vi.fn().mockResolvedValue([])
    const { start, stop } = useAutoRefresh(fetchFn, { interval: 10000 })
    start()
    await vi.advanceTimersByTimeAsync(0)
    start()  // second start should stop and restart
    await vi.advanceTimersByTimeAsync(0)
    // Each start calls fetchFn once immediately, so 2 calls total
    expect(fetchFn).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(10000)
    // Only one more call from the single interval
    expect(fetchFn).toHaveBeenCalledTimes(3)
    stop()
  })

  it('does not highlight when compareField is not set', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce([{ id: 1, value: 10 }])
      .mockResolvedValueOnce([{ id: 1, value: 20 }])
    const { start, stop, highlightedIds } = useAutoRefresh(fetchFn, { interval: 10000 })
    start()
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(10000)
    expect(highlightedIds.value).toEqual(new Set())
    stop()
  })
})
