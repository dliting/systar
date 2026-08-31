import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.stubGlobal('localStorage', {
  store: {},
  getItem(key) { return this.store[key] || null },
  setItem(key, val) { this.store[key] = String(val) },
  removeItem(key) { delete this.store[key] },
  clear() { this.store = {} }
})

import { getToken, setToken, removeToken } from '../../utils/auth'

describe('auth utilities', () => {
  beforeEach(() => { localStorage.clear() })

  it('getToken returns null when no token', () => {
    expect(getToken()).toBeNull()
  })

  it('setToken stores to localStorage', () => {
    setToken('test-jwt-123')
    expect(localStorage.getItem('Systar-Token')).toBe('test-jwt-123')
  })

  it('getToken retrieves stored token', () => {
    setToken('my-token')
    expect(getToken()).toBe('my-token')
  })

  it('removeToken clears localStorage', () => {
    setToken('to-remove')
    removeToken()
    expect(getToken()).toBeNull()
  })
})
