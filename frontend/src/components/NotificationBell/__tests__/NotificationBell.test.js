import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import NotificationBell from '@/components/NotificationBell.vue'

// Stub Element Plus components for testing
const globalStubs = {
  ElBadge: {
    name: 'ElBadge',
    template: '<div class="el-badge"><slot /><span v-if="value > 0" class="badge-value">{{ value }}</span></div>',
    props: ['value', 'hidden', 'max']
  },
  ElIcon: { name: 'ElIcon', template: '<span class="el-icon"><slot /></span>', props: ['size', 'color'] },
  ElPopover: { name: 'ElPopover', template: '<div class="el-popover"><slot name="reference" /><slot /></div>', props: ['placement', 'width', 'trigger'] },
  ElButton: { name: 'ElButton', template: '<button @click="$emit(\'click\')"><slot /></button>', props: ['link', 'type', 'size'] },
  ElEmpty: { name: 'ElEmpty', template: '<div class="el-empty"><slot /></div>', props: ['description', 'imageSize'] }
}

describe('NotificationBell', () => {
  let pinia
  let router

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/alarm', component: { template: '<div/>' } }]
    })
  })

  it('renders with no unread alarms', () => {
    const wrapper = mount(NotificationBell, {
      global: { plugins: [pinia, router], stubs: globalStubs }
    })
    expect(wrapper.find('.bell-icon').exists()).toBe(true)
  })

  it('shows badge value from store unreadAlarmCount', async () => {
    const { useWebSocketStore } = await import('@/stores/websocket')
    const store = useWebSocketStore()
    store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 3, assetId: 42 }))

    const wrapper = mount(NotificationBell, {
      global: { plugins: [pinia, router], stubs: globalStubs }
    })
    const badge = wrapper.findComponent({ name: 'ElBadge' })
    expect(badge.props('value')).toBe(1)
  })

  it('clears unread on markAllRead', async () => {
    const { useWebSocketStore } = await import('@/stores/websocket')
    const store = useWebSocketStore()
    store._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: 1, eventRankId: 3, assetId: 42 }))

    const wrapper = mount(NotificationBell, {
      global: { plugins: [pinia, router], stubs: globalStubs }
    })
    expect(store.unreadAlarmCount).toBe(1)
    store.clearUnreadAlarms()
    expect(store.unreadAlarmCount).toBe(0)
  })
})
