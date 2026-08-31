import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('@/api/iot/alarm', () => ({
  getAlarmRules: vi.fn().mockResolvedValue({ data: [] }),
  getAlarmMessages: vi.fn().mockResolvedValue({ rows: [], total: 0 }),
  listCorrelationRules: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
  addCorrelationRule: vi.fn().mockResolvedValue({}),
  updateCorrelationRule: vi.fn().mockResolvedValue({}),
  deleteCorrelationRule: vi.fn().mockResolvedValue({}),
  listEscalationPolicies: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
  addEscalationPolicy: vi.fn().mockResolvedValue({}),
  updateEscalationPolicy: vi.fn().mockResolvedValue({}),
  deleteEscalationPolicy: vi.fn().mockResolvedValue({}),
  listSilenceWindows: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
  addSilenceWindow: vi.fn().mockResolvedValue({}),
  updateSilenceWindow: vi.fn().mockResolvedValue({}),
  deleteSilenceWindow: vi.fn().mockResolvedValue({}),
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

vi.mock('@/composables/useAutoRefresh', () => ({
  useAutoRefresh: vi.fn(() => ({
    enabled: { value: true },
    highlightedIds: { value: new Set() },
    start: vi.fn(),
    stop: vi.fn(),
  }))
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  pagination: { template: '<div class="stub-pagination" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  EnhancedTable: {
    template: '<div class="stub-enhanced-table"><slot /></div>',
  },
}

import Alarm from '../index.vue'

function mountAlarm() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/alarm', component: { template: '<div/>' } }]
  })
  return mount(Alarm, {
    global: { plugins: [ElementPlus, router], stubs }
  })
}

describe('Alarm', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders alarm page container', () => {
    const wrapper = mountAlarm()
    expect(wrapper.find('.app-container').exists()).toBe(true)
  })

  it('renders tabs for rules and messages', () => {
    const wrapper = mountAlarm()
    expect(wrapper.text()).toContain('告警规则')
    expect(wrapper.text()).toContain('告警消息')
  })

  it('calls getAlarmRules on mount', async () => {
    mountAlarm()
    await flushPromises()
    const { getAlarmRules } = await import('@/api/iot/alarm')
    expect(getAlarmRules).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountAlarm()).not.toThrow()
  })
})

describe('useAutoRefresh integration', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('creates msgAutoRefresh with useAutoRefresh', async () => {
    const { useAutoRefresh } = await import('@/composables/useAutoRefresh')
    await mountAlarm()
    await flushPromises()
    expect(useAutoRefresh).toHaveBeenCalled()
  })

  it('does not start msgAutoRefresh on rules tab', async () => {
    const { useAutoRefresh } = await import('@/composables/useAutoRefresh')
    await mountAlarm()
    await flushPromises()
    const instance = useAutoRefresh.mock.results[0].value
    expect(instance.start).not.toHaveBeenCalled()
  })
})
