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

  // ======================== node-key collision pins ========================
  // t_group ids and per-kind runtime ids are independent number spaces: a
  // GROUP node and an ASSET node in one tree can carry the same numeric id.
  // el-tree node identity must therefore be the composite TreeNodeVO.key, or
  // checked-key writes resolve to whichever node registered last for the id.

  const COLLISION_FOREST = [
    { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null, caption: '设备', children: [
      { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, name: 'ups_001', caption: 'UPS', children: [
        { key: 'ASSET:20', nodeKind: 'ASSET', assetKind: 'PROBE', id: 20, name: 'temp_in', caption: '进温', children: [] },
        { key: 'ASSET:30', nodeKind: 'ASSET', assetKind: 'CONTROL', id: 30, name: 'switch_1', caption: '开关', children: [] }
      ] }
    ] },
    // Second null-id root (the real kind-tree orphan bucket): under
    // node-key="id" it collides with KIND:DEVICE in the tree's node map.
    { key: 'KIND:PROBE', nodeKind: 'ASSET', assetKind: 'PROBE', id: null, caption: '孤儿监测器', children: [
      { key: 'ASSET:40', nodeKind: 'ASSET', assetKind: 'PROBE', id: 40, name: 'loose_probe', caption: '游离探头', children: [] }
    ] },
    // GROUP id 20 collides with PROBE runtime id 20, GROUP id 30 with CONTROL
    // runtime id 30 — both survive pruning via their own kind children.
    { key: 'GROUP:20', nodeKind: 'GROUP', id: 20, name: 'g20', caption: '一楼', children: [
      { key: 'ASSET:25', nodeKind: 'ASSET', assetKind: 'PROBE', id: 25, name: 'temp_out', caption: '出温', children: [] }
    ] },
    { key: 'GROUP:30', nodeKind: 'GROUP', id: 30, name: 'g30', caption: '二楼', children: [
      { key: 'ASSET:35', nodeKind: 'ASSET', assetKind: 'CONTROL', id: 35, name: 'switch_2', caption: '开关2', children: [] }
    ] }
  ]

  async function mountWithCollisionFixture(rules) {
    const { getAssetTree, listLinkageRules } = await import('@/api/iot/linkage')
    getAssetTree.mockResolvedValueOnce({ data: COLLISION_FOREST })
    listLinkageRules.mockResolvedValueOnce({ data: rules })
    const wrapper = mountLinkage()
    await flushPromises()
    return wrapper.vm
  }

  it('editing a MONITOR rule checks the colliding probe node, not the group sharing its id', async () => {
    const vm = await mountWithCollisionFixture([
      { rule: { id: 1, name: 'r1', causeType: 'MONITOR', caption: '', enabled: true },
        causes: [{ causeMonitorId: 20, triggerValue: '1' }],
        effects: [{ effectMonitorId: 30, effectCommand: '-1' }] }
    ])

    await vm.handleEdit(vm.rules[0])
    await flushPromises()

    // Every node stays individually addressable under its composite key —
    // including the two null-id synthetic roots sharing one pruned tree.
    expect(vm.causeTreeRef.getNode('ASSET:20')).toBeTruthy()
    expect(vm.causeTreeRef.getNode('GROUP:20')).toBeTruthy()
    expect(vm.causeTreeRef.getNode('KIND:DEVICE')).toBeTruthy()
    expect(vm.causeTreeRef.getNode('KIND:PROBE')).toBeTruthy()
    // The rule's probe is the checked node — not the same-id group.
    const checkedKeys = vm.causeTreeRef.getCheckedNodes().map(n => n.key)
    expect(checkedKeys).toContain('ASSET:20')
    expect(checkedKeys).not.toContain('GROUP:20')
    // Round trip: the check handler maps the node back to the NUMERIC runtime
    // id the API payload needs, preserving the stored trigger value.
    vm.onCauseCheck()
    expect(vm.form.causes).toEqual([{ causeMonitorId: 20, triggerValue: '1' }])
  })

  it('editing a MONITOR rule checks the colliding control node, not the group sharing its id', async () => {
    const vm = await mountWithCollisionFixture([
      { rule: { id: 2, name: 'r2', causeType: 'MONITOR', caption: '', enabled: true },
        causes: [{ causeMonitorId: 25, triggerValue: '1' }],
        effects: [{ effectMonitorId: 30, effectCommand: '-1' }] }
    ])

    await vm.handleEdit(vm.rules[0])
    await flushPromises()

    expect(vm.effectTreeRef.getNode('ASSET:30')).toBeTruthy()
    expect(vm.effectTreeRef.getNode('GROUP:30')).toBeTruthy()
    const checkedKeys = vm.effectTreeRef.getCheckedNodes().map(n => n.key)
    expect(checkedKeys).toContain('ASSET:30')
    expect(checkedKeys).not.toContain('GROUP:30')
    // Round trip: numeric effect id + preserved command for the API payload.
    vm.onEffectCheck()
    expect(vm.form.effects).toEqual([{ effectMonitorId: 30, effectCommand: '-1' }])
  })

  it('editing an ALARM rule checks the colliding probe node in the alarm tree', async () => {
    const vm = await mountWithCollisionFixture([
      { rule: { id: 3, name: 'r3', causeType: 'ALARM', caption: '', enabled: true },
        causes: [{ causeMonitorId: 20, triggerValue: 'ALARM' }],
        effects: [{ effectMonitorId: 35, effectCommand: '-1' }] }
    ])

    await vm.handleEdit(vm.rules[0])
    await flushPromises()

    const checkedKeys = vm.alarmTreeRef.getCheckedNodes().map(n => n.key)
    expect(checkedKeys).toContain('ASSET:20')
    expect(checkedKeys).not.toContain('GROUP:20')
  })
})
