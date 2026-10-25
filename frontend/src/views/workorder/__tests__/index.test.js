import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/workorder', () => ({
  listWorkOrders: vi.fn().mockResolvedValue({
    records: [{ id: 1, orderNo: 'WO-1', type: 'REPAIR', priority: 4, status: 'CREATED', dueTime: '2026-01-02 00:00:00' }], total: 1
  }),
  getWorkOrderStats: vi.fn().mockResolvedValue({ data: { total: 0, created: 0, processing: 0, closed: 0 } }),
  createWorkOrder: vi.fn().mockResolvedValue({ data: 1 }),
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  // Renders each column header plus the first data row through the view's
  // scoped column slots, so slot-based label mapping is observable.
  EnhancedTable: {
    name: 'EnhancedTable',
    props: ['columns', 'data', 'loading', 'theme', 'exportable', 'exportFileName', 'contextMenu', 'rowClassName'],
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

import Workorder from '../index.vue'

function mountWorkorder() {
  return mount(Workorder, {
    global: { plugins: [ElementPlus], stubs }
  })
}

describe('Workorder', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders workorder page container', () => {
    const wrapper = mountWorkorder()
    expect(wrapper.find('.ops-container').exists()).toBe(true)
  })

  it('renders page title', () => {
    const wrapper = mountWorkorder()
    expect(wrapper.text()).toContain('工单管理')
  })

  it('renders stat cards row', () => {
    const wrapper = mountWorkorder()
    expect(wrapper.find('.stat-row').exists()).toBe(true)
  })

  it('renders tabs', () => {
    const wrapper = mountWorkorder()
    expect(wrapper.text()).toContain('工单列表')
  })

  it('calls API on mount', async () => {
    mountWorkorder()
    await flushPromises()
    const { listWorkOrders } = await import('@/api/iot/workorder')
    expect(listWorkOrders).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountWorkorder()).not.toThrow()
  })

  // Operators read Chinese type and named priority, not REPAIR / P1 codes.
  it('labels the work order type and priority', async () => {
    const wrapper = mountWorkorder()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('维修')
    expect(text).toContain('P4 紧急')
  })
})
