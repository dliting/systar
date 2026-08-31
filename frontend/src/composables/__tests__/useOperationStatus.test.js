import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useOperationStatus } from '../useOperationStatus'

describe('useOperationStatus', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('initializes with idle status', () => {
    const { getStatus } = useOperationStatus()
    expect(getStatus(1)).toBe('idle')
  })

  it('transitions to loading when execute is called', async () => {
    const apiFn = vi.fn().mockResolvedValue({})
    const { execute, getStatus } = useOperationStatus()
    const p = execute(1, apiFn)
    expect(getStatus(1)).toBe('loading')
    await p
    expect(getStatus(1)).toBe('success')
  })

  it('transitions to failed when API throws', async () => {
    const apiFn = vi.fn().mockRejectedValue(new Error('fail'))
    const { execute, getStatus } = useOperationStatus()
    await execute(1, apiFn)
    expect(getStatus(1)).toBe('failed')
  })

  it('transitions to timeout when watchForCompletion times out', async () => {
    const { watchForCompletion, getStatus } = useOperationStatus({ timeoutMs: 5000 })
    const wsGetter = vi.fn().mockReturnValue(undefined)
    watchForCompletion(1, wsGetter)
    expect(getStatus(1)).toBe('loading')
    vi.advanceTimersByTime(6000)
    expect(getStatus(1)).toBe('timeout')
  })

  it('transitions to success when WS value appears', async () => {
    let wsValue = undefined
    const wsGetter = () => wsValue
    const { watchForCompletion, getStatus } = useOperationStatus({ timeoutMs: 5000 })
    watchForCompletion(1, wsGetter)
    expect(getStatus(1)).toBe('loading')
    wsValue = { value: 42 }
    vi.advanceTimersByTime(1000) // trigger poll
    expect(getStatus(1)).toBe('success')
  })

  it('clearStatus resets to idle', async () => {
    const apiFn = vi.fn().mockResolvedValue({})
    const { execute, getStatus, clearStatus } = useOperationStatus()
    await execute(1, apiFn)
    expect(getStatus(1)).toBe('success')
    clearStatus(1)
    expect(getStatus(1)).toBe('idle')
  })

  it('isLoading returns true during loading phase', async () => {
    const apiFn = vi.fn().mockResolvedValue({})
    const { execute, isLoading } = useOperationStatus()
    const p = execute(1, apiFn)
    expect(isLoading(1)).toBe(true)
    await p
    expect(isLoading(1)).toBe(false)
  })

  it('handles multiple assets independently', async () => {
    const apiFn = vi.fn().mockResolvedValue({})
    const { execute, getStatus } = useOperationStatus()
    const p1 = execute(1, apiFn)
    const p2 = execute(2, apiFn)
    expect(getStatus(1)).toBe('loading')
    expect(getStatus(2)).toBe('loading')
    await Promise.all([p1, p2])
    expect(getStatus(1)).toBe('success')
    expect(getStatus(2)).toBe('success')
  })

  it('clearAll resets all statuses', async () => {
    const apiFn = vi.fn().mockResolvedValue({})
    const { execute, getStatus, clearAll } = useOperationStatus()
    await execute(1, apiFn)
    await execute(2, apiFn)
    clearAll()
    expect(getStatus(1)).toBe('idle')
    expect(getStatus(2)).toBe('idle')
  })
})
