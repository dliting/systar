import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { setActivePinia, createPinia } from 'pinia'
import { useWebSocketStore } from '@/stores/websocket'

// jsdom's localStorage is non-functional in this setup (getItem is not a
// function); the auth store reads the token at setup, so stub it in-memory.
vi.stubGlobal('localStorage', (() => {
  const store = new Map()
  return {
    getItem: k => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => { store.set(k, String(v)) },
    removeItem: k => { store.delete(k) },
    clear: () => { store.clear() }
  }
})())

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ path: '/dashboard', query: {}, fullPath: '/dashboard' })
}))

const stubs = {
  NotificationBell: { template: '<div class="stub-bell" />' },
  RouterView: { template: '<div />' }
}

import Layout from '../index.vue'

function mountLayout() {
  return mount(Layout, { global: { plugins: [ElementPlus], stubs } })
}

describe('layout disconnect banner', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  // Operators must know live data went stale — the header banner is the only
  // global surface for a dropped websocket connection.
  it('shows the reconnect banner once a live connection drops', () => {
    const ws = useWebSocketStore()
    ws.hasConnected = true
    ws.connected = false
    const wrapper = mountLayout()
    expect(wrapper.text()).toContain('实时连接已断开')
  })

  it('hides the banner while connected', () => {
    const ws = useWebSocketStore()
    ws.hasConnected = true
    ws.connected = true
    const wrapper = mountLayout()
    expect(wrapper.text()).not.toContain('实时连接已断开')
  })

  it('hides the banner before the first successful connection', () => {
    const wrapper = mountLayout()
    expect(wrapper.text()).not.toContain('实时连接已断开')
  })
})
