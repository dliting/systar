import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/workorder', () => ({
  listWorkOrders: vi.fn().mockResolvedValue({ data: [] }),
  getWorkOrderStats: vi.fn().mockResolvedValue({ data: { total: 0, created: 0, processing: 0, closed: 0 } }),
  createWorkOrder: vi.fn().mockResolvedValue({ data: 1 }),
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  EnhancedTable: {
    template: '<div class="stub-enhanced-table"><slot /></div>',
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
})
