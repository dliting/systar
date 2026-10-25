import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('@/api/iot/alarm', () => ({
  getAlarmRules: vi.fn().mockResolvedValue({
    data: [{ id: 1, monitorId: 11, strategy: 'ONLY_ONCE', way: 1, eventRankId: 2, start: 1, messageTemplate: 't', enabled: 1 }]
  }),
  getAlarmMessages: vi.fn().mockResolvedValue({
    rows: [{ id: 5, monitorId: 77, caption: 'c', state: 1, escalationLevel: 0, recovered: 0, alarmTime: '2026-01-01T00:00:00' }], total: 1
  }),
  listCorrelationRules: vi.fn().mockResolvedValue({
    data: { records: [{ id: 1, name: 'corr', deviceId: 10, windowSeconds: 300, enabled: 1 }], total: 1 }
  }),
  addCorrelationRule: vi.fn().mockResolvedValue({}),
  updateCorrelationRule: vi.fn().mockResolvedValue({}),
  deleteCorrelationRule: vi.fn().mockResolvedValue({}),
  listEscalationPolicies: vi.fn().mockResolvedValue({
    data: { records: [{ id: 1, name: 'esc', fromLevel: 2, toLevel: 3, timeoutMinutes: 30, notifyType: 'site_notice', enabled: 1 }], total: 1 }
  }),
  addEscalationPolicy: vi.fn().mockResolvedValue({}),
  updateEscalationPolicy: vi.fn().mockResolvedValue({}),
  deleteEscalationPolicy: vi.fn().mockResolvedValue({}),
  listSilenceWindows: vi.fn().mockResolvedValue({
    data: { records: [{ id: 1, name: 'sil', deviceId: 10, monitorId: 11, startTime: '2026-01-01 00:00:00', endTime: '2026-01-02 00:00:00', reason: 'r', enabled: 1 }], total: 1 }
  }),
  addSilenceWindow: vi.fn().mockResolvedValue({}),
  updateSilenceWindow: vi.fn().mockResolvedValue({}),
  deleteSilenceWindow: vi.fn().mockResolvedValue({}),
}))

// Asset name resolution for the raw device/monitor id columns.
vi.mock('@/api/iot/asset', () => ({
  getAssetTree: vi.fn().mockResolvedValue({
    data: [
      { key: 'KIND:DEVICE', nodeKind: 'KIND', children: [
        { key: 'ASSET:10', nodeKind: 'ASSET', id: 10, caption: '空调主机', name: 'hvac_1', children: [
          { key: 'ASSET:11', nodeKind: 'ASSET', id: 11, caption: null, name: 'temp_101', children: [] }
        ] }
      ] }
    ]
  }),
  listGroupTrees: vi.fn().mockResolvedValue({ data: [] })
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
  // Renders each column header plus the first data row through the view's
  // scoped column slots, so slot-based label mapping is observable.
  EnhancedTable: {
    name: 'EnhancedTable',
    props: ['columns', 'data', 'loading', 'exportable', 'exportFileName', 'rowClassName', 'contextMenu'],
    template: `
      <div class="stub-enhanced-table">
        <slot />
        <div v-for="col in (columns || [])" :key="col.prop" class="stub-col">
          <span class="stub-col-label">{{ col.label }}</span>
          <template v-if="firstRow && col.slot"><slot :name="'column-' + col.prop" :row="firstRow" /></template>
          <span v-else-if="firstRow" class="stub-col-raw">{{ firstRow[col.prop] }}</span>
        </div>
      </div>`,
    computed: {
      firstRow() { return (this.data || [])[0] }
    }
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

// Operators read names and labels, not raw enum codes or numeric ids.
// Element Plus renders a tab pane only after first activation, so tests
// switch to the tab whose table they assert on.
async function mountOnTab(tabLabel) {
  const wrapper = mountAlarm()
  await flushPromises()
  const item = wrapper.findAll('.el-tabs__item').find(t => t.text() === tabLabel)
  await item.trigger('click')
  await flushPromises()
  return wrapper
}

describe('operator-facing labels', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('names the alarm rule rank and resolves its monitor id to the asset name', async () => {
    const wrapper = await mountOnTab('告警规则')
    const text = wrapper.text()
    expect(text).toContain('警告')
    expect(text).toContain('temp_101')
  })

  it('labels the message log id column and the escalation count', async () => {
    const wrapper = await mountOnTab('告警消息')
    const text = wrapper.text()
    expect(text).toContain('日志ID')
    expect(text).toContain('未升级')
  })

  it('names correlation and silence device ids', async () => {
    const wrapper = await mountOnTab('关联规则')
    expect(wrapper.text()).toContain('空调主机')
  })

  it('names escalation ranks and the notification channel', async () => {
    const wrapper = await mountOnTab('升级策略')
    const text = wrapper.text()
    expect(text).toContain('重要')
    expect(text).toContain('站内通知')
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
