import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/scheduledTask', () => ({
  listTasks: vi.fn().mockResolvedValue({ data: [] }),
  createTask: vi.fn().mockResolvedValue({ data: 1 }),
  updateTask: vi.fn().mockResolvedValue({}),
  deleteTask: vi.fn().mockResolvedValue({}),
  enableTask: vi.fn().mockResolvedValue({}),
  disableTask: vi.fn().mockResolvedValue({}),
  getTaskLogs: vi.fn().mockResolvedValue({ data: [] }),
  previewCron: vi.fn().mockResolvedValue({ data: null })
}))

vi.mock('@/api/iot/asset', () => ({
  getAssetTree: vi.fn().mockResolvedValue({
    data: { id: 1, name: 'root', caption: 'Root', kind: 'SPACE', children: [] }
  })
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

import Schedule from '../index.vue'

function mountSchedule() {
  return shallowMount(Schedule, {
    global: {
      plugins: [ElementPlus],
      stubs: {
        EnhancedTable: { template: '<div class="stub-enhanced-table"><slot /></div>' },
      }
    }
  })
}

describe('Schedule', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders page with tree sidebar and content area', () => {
    const wrapper = mountSchedule()
    expect(wrapper.find('.tree-sidebar-manage-wrap').exists()).toBe(true)
    expect(wrapper.find('.tree-sidebar-content').exists()).toBe(true)
  })

  it('renders toolbar with add button', () => {
    const wrapper = mountSchedule()
    const html = wrapper.html()
    expect(html).toContain('新增任务')
  })

  it('renders table stub and dialog stubs', () => {
    const wrapper = mountSchedule()
    expect(wrapper.find('.stub-enhanced-table').exists()).toBe(true)
    expect(wrapper.findAll('el-dialog-stub').length).toBeGreaterThanOrEqual(2)
  })

  it('calls listTasks on mount', async () => {
    mountSchedule()
    await flushPromises()
    const { listTasks } = await import('@/api/iot/scheduledTask')
    expect(listTasks).toHaveBeenCalled()
  })

  it('calls getAssetTree on mount', async () => {
    mountSchedule()
    await flushPromises()
    const { getAssetTree } = await import('@/api/iot/asset')
    expect(getAssetTree).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountSchedule()).not.toThrow()
  })
})
