import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/inspection', () => ({
  listPlans: vi.fn().mockResolvedValue({ data: [] }),
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
  EnhancedTable: {
    template: '<div class="stub-enhanced-table"><slot /></div>',
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
})
