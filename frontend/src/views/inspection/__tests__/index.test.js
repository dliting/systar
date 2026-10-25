import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/inspection', () => ({
  listPlans: vi.fn().mockResolvedValue({
    records: [{ id: 1, name: '周检计划', cronExpression: '0 0 9 ? * MON', enabled: 1, autoCreateWorkorder: 0 }], total: 1
  }),
  getPlan: vi.fn().mockResolvedValue({ data: {} }),
  createPlan: vi.fn().mockResolvedValue({ data: 1 }),
  updatePlan: vi.fn().mockResolvedValue({}),
  deletePlan: vi.fn().mockResolvedValue({}),
  listTasks: vi.fn().mockResolvedValue({ data: [] }),
  startTask: vi.fn().mockResolvedValue({}),
  getTask: vi.fn().mockResolvedValue({ data: {} }),
  submitResults: vi.fn().mockResolvedValue({}),
  completeTask: vi.fn().mockResolvedValue({}),
  cancelTask: vi.fn().mockResolvedValue({}),
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  // Renders each column header plus the first data row through the view's
  // scoped column slots, so slot-based rendering is observable.
  EnhancedTable: {
    name: 'EnhancedTable',
    props: ['columns', 'data', 'loading', 'theme', 'exportable', 'exportFileName', 'contextMenu'],
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

import Inspection from '../index.vue'

function mountInspection() {
  return mount(Inspection, {
    global: { plugins: [ElementPlus], stubs }
  })
}

describe('Inspection', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders inspection page container', () => {
    const wrapper = mountInspection()
    expect(wrapper.find('.ops-container').exists()).toBe(true)
  })

  it('renders page title', () => {
    const wrapper = mountInspection()
    expect(wrapper.text()).toContain('巡检管理')
    expect(wrapper.text()).toContain('巡检计划与执行')
  })

  it('renders tabs for plans and tasks', () => {
    const wrapper = mountInspection()
    expect(wrapper.text()).toContain('巡检计划')
    expect(wrapper.text()).toContain('巡检任务')
  })

  it('calls listPlans on mount', async () => {
    mountInspection()
    await flushPromises()
    const { listPlans } = await import('@/api/iot/inspection')
    expect(listPlans).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountInspection()).not.toThrow()
  })

  // Operators read "每周一 09:00" in the plan table, not the raw cron string.
  it('humanizes the cron expression in the plan table', async () => {
    const wrapper = mountInspection()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('每周一 09:00')
    expect(text).not.toContain('0 0 9 ? * MON')
  })
})
