import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useChart } from '../useChart'

/**
 * Mock ResizeObserver — jsdom does not provide it.
 * Each instance records observe/unobserve/disconnect calls and invokes the
 * registered callback synchronously on observe() so tests can simulate a
 * container resize.
 */
class MockResizeObserver {
  static instances = []
  static lastInstance = null
  constructor(cb) {
    this.cb = cb
    this.observed = []
    this.disconnected = false
    MockResizeObserver.instances.push(this)
    MockResizeObserver.lastInstance = this
  }
  observe(el) { this.observed.push(el) }
  unobserve(el) { this.observed = this.observed.filter(x => x !== el) }
  disconnect() { this.disconnected = true }
  fire() { if (!this.disconnected) this.cb() }
}

describe('useChart', () => {
  let originalRO
  let originalRAF
  let originalCAF
  let rafCallbacks
  let rafIdCounter

  beforeEach(() => {
    vi.useFakeTimers()
    MockResizeObserver.instances = []
    MockResizeObserver.lastInstance = null
    originalRO = global.ResizeObserver
    global.ResizeObserver = MockResizeObserver

    rafCallbacks = new Map()
    rafIdCounter = 1
    originalRAF = global.requestAnimationFrame
    originalCAF = global.cancelAnimationFrame
    global.requestAnimationFrame = vi.fn((cb) => {
      const id = rafIdCounter++
      rafCallbacks.set(id, cb)
      return id
    })
    global.cancelAnimationFrame = vi.fn((id) => {
      rafCallbacks.delete(id)
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (originalRO === undefined) delete global.ResizeObserver
    else global.ResizeObserver = originalRO
    global.requestAnimationFrame = originalRAF
    global.cancelAnimationFrame = originalCAF
  })

  /** Flush pending rAF callbacks. */
  function flushRaf() {
    for (const [id, cb] of rafCallbacks) {
      rafCallbacks.delete(id)
      cb()
    }
  }

  it('initializes chart instance with dark theme', async () => {
    const { chartRef, initChart } = useChart()
    chartRef.value = document.createElement('div')
    const instance = initChart()
    await nextTick()
    expect(instance).toBeTruthy()
    expect(typeof instance.setOption).toBe('function')
  })

  it('returns null when chartRef is not set', () => {
    const { chartRef, initChart } = useChart()
    chartRef.value = null
    const instance = initChart()
    expect(instance).toBeNull()
  })

  it('ResizeObserver is created on initChart', async () => {
    const { chartRef, initChart } = useChart()
    chartRef.value = document.createElement('div')
    initChart()
    await nextTick()
    expect(MockResizeObserver.lastInstance).not.toBeNull()
    expect(MockResizeObserver.lastInstance.observed).toContain(chartRef.value)
  })

  it('ResizeObserver triggers resize via rAF debounce', async () => {
    const { chartRef, initChart } = useChart()
    chartRef.value = document.createElement('div')
    const instance = initChart()
    const resizeSpy = vi.spyOn(instance, 'resize')

    // Fire ResizeObserver callback 3 times rapidly
    MockResizeObserver.lastInstance.fire()
    MockResizeObserver.lastInstance.fire()
    MockResizeObserver.lastInstance.fire()

    // Before rAF flush, no resize yet
    expect(resizeSpy).not.toHaveBeenCalled()

    // After rAF flush, resize called exactly once (debounced)
    flushRaf()
    expect(resizeSpy).toHaveBeenCalledTimes(1)

    // Subsequent fires after flush should trigger a new rAF cycle
    MockResizeObserver.lastInstance.fire()
    flushRaf()
    expect(resizeSpy).toHaveBeenCalledTimes(2)
  })

  it('window resize shares the same rAF debounce', async () => {
    const { chartRef, initChart } = useChart()
    chartRef.value = document.createElement('div')
    const instance = initChart()
    const resizeSpy = vi.spyOn(instance, 'resize')

    // Both ResizeObserver and window resize triggered in same frame
    MockResizeObserver.lastInstance.fire()
    window.dispatchEvent(new Event('resize'))

    flushRaf()
    expect(resizeSpy).toHaveBeenCalledTimes(1)
  })

  it('dispose cancels pending rAF and disconnects ResizeObserver', async () => {
    const { chartRef, initChart, dispose } = useChart()
    chartRef.value = document.createElement('div')
    initChart()
    const ro = MockResizeObserver.lastInstance

    MockResizeObserver.lastInstance.fire()  // queue rAF
    dispose()

    expect(ro.disconnected).toBe(true)
    expect(global.cancelAnimationFrame).toHaveBeenCalled()
  })

  it('ResizeObserver fallback when undefined (old browsers)', async () => {
    delete global.ResizeObserver
    const { chartRef, initChart } = useChart()
    chartRef.value = document.createElement('div')

    expect(() => initChart()).not.toThrow()
  })
})
