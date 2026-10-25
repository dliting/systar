import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/scheduledTask', () => ({
  listTasks: vi.fn().mockResolvedValue({ data: [
    { id: 1, name: '早班启动', controlId: 10, command: 'ON', cronExpression: '0 0 8 * * ?', enabled: true, nextFireTime: null }
  ] }),
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
        // Renders each column header plus the first data row through the
        // view's scoped column slots, so slot-based rendering is observable.
        EnhancedTable: {
          name: 'EnhancedTable',
          props: ['columns', 'data', 'loading', 'exportable', 'exportFileName', 'contextMenu'],
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

  // Operators read "每天 08:00" in the task table, not the raw cron string.
  it('humanizes the cron expression in the task table', async () => {
    const wrapper = mountSchedule()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('每天 08:00')
    expect(text).not.toContain('0 0 8 * * ?')
    const labels = wrapper.findAll('.stub-col-label').map(n => n.text())
    expect(labels).toContain('调度规则')
  })
})
