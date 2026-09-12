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
  getAssetTree: vi.fn().mockResolvedValue({ data: [
    { key: 'KIND:SERVICE', nodeKind: 'ASSET', assetKind: 'SERVICE', id: null, children: [] },
    { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null, children: [
      { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, name: 'ups_001', caption: 'UPS', state: 'NORMAL', enabled: true, children: [] }
    ] }
  ] }),
  listGroupTrees: vi.fn().mockResolvedValue({ data: [] }),
  listGroups: vi.fn().mockResolvedValue({ data: [] }),
  createGroup: vi.fn().mockResolvedValue({}),
  updateGroup: vi.fn().mockResolvedValue({}),
  deleteGroup: vi.fn().mockResolvedValue({}),
  replaceGroupAssets: vi.fn().mockResolvedValue({}),
  createGroupTree: vi.fn().mockResolvedValue({}),
  updateGroupTree: vi.fn().mockResolvedValue({}),
  deleteGroupTree: vi.fn().mockResolvedValue({})
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

  it('fills the target monitor from a probe tree node while the dialog is open', async () => {
    const wrapper = mountSchedule()
    await flushPromises()
    const vm = wrapper.vm
    vm.handleAdd()
    vm.handleTreeNodeClick({ key: 'ASSET:5', nodeKind: 'ASSET', assetKind: 'PROBE', id: 5, caption: '温度探头' })
    expect(vm.form.controlId).toBe(5)
  })

  it('fills the target monitor from a control tree node', async () => {
    const wrapper = mountSchedule()
    await flushPromises()
    const vm = wrapper.vm
    vm.handleAdd()
    vm.handleTreeNodeClick({ key: 'ASSET:6', nodeKind: 'ASSET', assetKind: 'CONTROL', id: 6, caption: '开关' })
    expect(vm.form.controlId).toBe(6)
  })

  it('ignores group nodes and id-less synthetic nodes when filling the target', async () => {
    const wrapper = mountSchedule()
    await flushPromises()
    const vm = wrapper.vm
    vm.handleAdd()
    vm.handleTreeNodeClick({ key: 'GROUP:1', nodeKind: 'GROUP', id: 1, caption: '一楼', children: [] })
    vm.handleTreeNodeClick({ key: 'KIND:PROBE', nodeKind: 'ASSET', assetKind: 'PROBE', id: null, caption: '孤儿监测器' })
    expect(vm.form.controlId).toBeFalsy()
  })

  it('renders without errors', () => {
    expect(() => mountSchedule()).not.toThrow()
  })
})
