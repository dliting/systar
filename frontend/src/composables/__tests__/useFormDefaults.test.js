import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.stubGlobal('localStorage', {
  store: {},
  getItem(key) { return this.store[key] || null },
  setItem(key, val) { this.store[key] = String(val) },
  removeItem(key) { delete this.store[key] },
  clear() { this.store = {} }
})

import { useFormDefaults } from '@/composables/useFormDefaults'

describe('useFormDefaults', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns empty object when no saved data', () => {
    const { defaults } = useFormDefaults('probe')
    const result = defaults()
    expect(result).toEqual({})
  })

  it('saves and retrieves defaults', () => {
    const { saveDefaults, defaults } = useFormDefaults('probe')
    saveDefaults({ name: 'sensor-1', interval: 30 })
    expect(defaults()).toEqual({ name: 'sensor-1', interval: 30 })
  })

  it('stores data under key form-{storageKey}', () => {
    const { saveDefaults } = useFormDefaults('probe')
    saveDefaults({ name: 'sensor-1' })
    expect(localStorage.getItem('form-probe')).toBe(JSON.stringify({ name: 'sensor-1' }))
  })

  it('clears saved defaults', () => {
    const { saveDefaults, clearDefaults, defaults } = useFormDefaults('probe')
    saveDefaults({ name: 'sensor-1' })
    clearDefaults()
    expect(defaults()).toEqual({})
    expect(localStorage.getItem('form-probe')).toBeNull()
  })

  it('merges parent defaults with memory defaults (memory wins)', () => {
    const { saveDefaults, defaults } = useFormDefaults('probe')
    saveDefaults({ name: 'memory-name', unit: 'C' })

    const result = defaults({ name: 'parent-name', detectInterval: 10 })
    expect(result).toEqual({
      name: 'memory-name',
      unit: 'C',
      detectInterval: 10
    })
  })

  it('priority: memory > parent > typeDefaults', () => {
    const { saveDefaults, defaults } = useFormDefaults('probe')
    saveDefaults({ name: 'memory-name' })

    const result = defaults(
      { name: 'parent-name', unit: 'C' },
      { name: 'type-name', unit: 'F', interval: 60 }
    )
    expect(result).toEqual({
      name: 'memory-name',
      unit: 'C',
      interval: 60
    })
  })

  it('returns parent defaults when no memory', () => {
    const { defaults } = useFormDefaults('probe')
    const result = defaults({ name: 'parent-name', unit: 'C' })
    expect(result).toEqual({ name: 'parent-name', unit: 'C' })
  })

  it('handles corrupted localStorage gracefully', () => {
    localStorage.setItem('form-probe', 'not-valid-json{{{')
    const { defaults } = useFormDefaults('probe')
    const result = defaults()
    expect(result).toEqual({})
  })

  it('only persists specified fields (whitelist)', () => {
    const { saveDefaults, defaults } = useFormDefaults('probe')
    saveDefaults(
      { name: 'sensor-1', interval: 30, secret: 'hidden' },
      ['name', 'interval']
    )
    expect(defaults()).toEqual({ name: 'sensor-1', interval: 30 })
    expect(defaults()).not.toHaveProperty('secret')
  })

  it('different storageKeys are independent', () => {
    const probe = useFormDefaults('probe')
    const control = useFormDefaults('control')

    probe.saveDefaults({ name: 'probe-1' })
    control.saveDefaults({ name: 'control-1' })

    expect(probe.defaults()).toEqual({ name: 'probe-1' })
    expect(control.defaults()).toEqual({ name: 'control-1' })

    probe.clearDefaults()
    expect(probe.defaults()).toEqual({})
    expect(control.defaults()).toEqual({ name: 'control-1' })
  })
})
