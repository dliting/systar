import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/linkage', () => ({
  listLinkageRules: vi.fn().mockResolvedValue({ data: [] }),
  createLinkageRule: vi.fn().mockResolvedValue({ data: 1 }),
  updateLinkageRule: vi.fn().mockResolvedValue({}),
  deleteLinkageRule: vi.fn().mockResolvedValue({}),
  toggleLinkageRule: vi.fn().mockResolvedValue({}),
  getAssetTree: vi.fn().mockResolvedValue({ data: [
    { key: 'KIND:SERVICE', nodeKind: 'ASSET', assetKind: 'SERVICE', id: null, children: [] },
    { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null, children: [
      { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, name: 'ups_001', caption: 'UPS', state: 'NORMAL', enabled: true, children: [] }
    ] }
  ] })
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn(),
  showSystarSuccess: vi.fn()
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  EnhancedTable: { template: '<div class="stub-enhanced-table"><slot /></div>' },
  Monitor: { template: '<span />' },
  Switch: { template: '<span />' },
  Right: { template: '<span />' },
  WarningFilled: { template: '<span />' },
  FolderOpened: { template: '<span />' },
  Coin: { template: '<span />' },
}

import Linkage from '../index.vue'

function mountLinkage() {
  return mount(Linkage, {
    global: { plugins: [ElementPlus], stubs }
  })
}

describe('Linkage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders linkage page container', () => {
    const wrapper = mountLinkage()
    expect(wrapper.find('.linkage-page').exists()).toBe(true)
  })

  it('renders add rule button', () => {
    const wrapper = mountLinkage()
    expect(wrapper.text()).toContain('新增规则')
  })

  it('calls listLinkageRules on mount', async () => {
    mountLinkage()
    await flushPromises()
    const { listLinkageRules } = await import('@/api/iot/linkage')
    expect(listLinkageRules).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountLinkage()).not.toThrow()
  })

  it('builds monitor and control trees from the forest', async () => {
    const { getAssetTree } = await import('@/api/iot/linkage')
    // Group id 20 deliberately collides with probe id 20: the group must show
    // up only in the control tree (via its control child), never in the probe tree.
    getAssetTree.mockResolvedValueOnce({ data: [
      { key: 'KIND:SERVICE', nodeKind: 'ASSET', assetKind: 'SERVICE', id: null, caption: '服务', children: [] },
      { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null, caption: '设备', children: [
        { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, name: 'ups_001', caption: 'UPS', children: [
          { key: 'ASSET:20', nodeKind: 'ASSET', assetKind: 'PROBE', id: 20, name: 'temp_in', caption: '进温', children: [] }
        ] }
      ] },
      { key: 'GROUP:20', nodeKind: 'GROUP', id: 20, name: 'g20', caption: '一楼', children: [
        { key: 'ASSET:30', nodeKind: 'ASSET', assetKind: 'CONTROL', id: 30, name: 'switch_1', caption: '开关', children: [] }
      ] }
    ] })
    const wrapper = mountLinkage()
    await flushPromises()
    const vm = wrapper.vm

    expect(vm.assetNameMap[20]).toBe('进温')
    expect(vm.assetNameMap[30]).toBe('开关')

    expect(vm.monitorTreeData).toHaveLength(1)
    expect(vm.monitorTreeData[0].caption).toBe('设备')
    expect(vm.monitorTreeData[0].children[0].children[0].id).toBe(20)

    expect(vm.controlTreeData).toHaveLength(1)
    expect(vm.controlTreeData[0].id).toBe(20)
    expect(vm.controlTreeData[0].children[0].id).toBe(30)
  })
})
