import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { defineComponent, nextTick, ref } from 'vue'

// jsdom's localStorage is non-functional in this setup (getItem is not a
// function); useFormDefaults.saveDefaults propagates that failure, so give
// the whole file a working in-memory implementation.
vi.stubGlobal('localStorage', (() => {
  const store = new Map()
  return {
    getItem: k => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => { store.set(k, String(v)) },
    removeItem: k => { store.delete(k) },
    clear: () => { store.clear() }
  }
})())

// Forest fixture shared by the asset API mock and the useAssetTree mock.
const FOREST = [
  { key: 'KIND:SERVICE', nodeKind: 'ASSET', assetKind: 'SERVICE', id: null, name: '服务', caption: '服务', children: [] },
  { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null, name: '设备', caption: '设备', children: [
    { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, name: 'ups_001', caption: 'UPS', state: 'NORMAL', enabled: true, children: [] }
  ] }
]

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
  deleteGroupTree: vi.fn().mockResolvedValue({}),
  getAsset: vi.fn().mockResolvedValue({ data: {} }),
  getAssetTypes: vi.fn().mockResolvedValue({ data: [] }),
  getTypeProperties: vi.fn().mockResolvedValue({ data: [] }),
  createAsset: vi.fn().mockResolvedValue({ data: 1 }),
  updateAsset: vi.fn().mockResolvedValue({}),
  deleteAsset: vi.fn().mockResolvedValue({}),
  detectAsset: vi.fn().mockResolvedValue({}),
  executeControl: vi.fn().mockResolvedValue({}),
  startAsset: vi.fn().mockResolvedValue({}),
  stopAsset: vi.fn().mockResolvedValue({}),
  disableAsset: vi.fn().mockResolvedValue({}),
  enableAsset: vi.fn().mockResolvedValue({}),
  batchStart: vi.fn().mockResolvedValue({}),
  batchStop: vi.fn().mockResolvedValue({}),
  batchEnable: vi.fn().mockResolvedValue({}),
  batchDisable: vi.fn().mockResolvedValue({}),
  batchDelete: vi.fn().mockResolvedValue({}),
  listAssets: vi.fn().mockResolvedValue({ data: [
    { id: 101, name: 'temp_in', caption: '输入温度' },
    { id: 102, name: 'temp_out', caption: '输出温度' }
  ] }),
}))

vi.mock('@/api/iot/trend', () => ({
  getTrendDefault: vi.fn().mockResolvedValue({ data: {} }),
  getTrendData: vi.fn().mockResolvedValue({ data: { dataPoints: [] } }),
}))

vi.mock('@/stores/websocket', () => ({
  useWebSocketStore: vi.fn(() => ({
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn(),
    probeValues: {},
  }))
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

// Mirror the real module export face (KIND_TREE + useAssetTree) so consumers
// importing either name do not blow up on the mock (T9 convention).
vi.mock('@/composables/useAssetTree', () => ({
  KIND_TREE: 'kind',
  useAssetTree: vi.fn()
}))

vi.mock('@/composables/useAutoRefresh', () => ({
  useAutoRefresh: vi.fn(() => ({
    enabled: { value: true },
    highlightedIds: { value: new Set() },
    start: vi.fn(),
    stop: vi.fn(),
  }))
}))

const routeMock = vi.hoisted(() => ({ path: '/operations', query: {} }))

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn() })),
  useRoute: vi.fn(() => routeMock)
}))

// Stub components that have ref methods called by the parent
const StubDurationInput = defineComponent({
  name: 'DurationInput',
  template: '<div />'
})
const StubControlCommandInput = defineComponent({
  name: 'ControlCommandInput',
  template: '<div />'
})
const StubTrendChart = defineComponent({
  name: 'TrendChart',
  template: '<div />'
})
const StubInlineEdit = defineComponent({
  name: 'InlineEdit',
  props: ['value', 'type', 'options', 'min', 'max', 'placeholder', 'disabled'],
  template: '<span class="stub-inline-edit">{{ value }}</span>'
})
const StubConfirmDialog = defineComponent({
  name: 'ConfirmDialog',
  props: ['visible', 'title', 'message', 'impact', 'requireInput', 'expectedInput', 'inputPlaceholder'],
  template: '<div />'
})
const StubSkeleton = defineComponent({
  name: 'Skeleton',
  props: ['variant', 'rows', 'columns', 'animated'],
  template: '<div />'
})

import Operations from '../index.vue'
import ConfirmDialog from '@/components/ConfirmDialog/index.vue'
import { createAsset } from '@/api/iot/asset'
import { useAssetTree } from '@/composables/useAssetTree'

async function mountAndFlush() {
  useAssetTree.mockReturnValue({
    trees: ref([]),
    currentTree: ref('kind'),
    currentTreeCaption: ref('按类型'),
    forest: ref(FOREST),
    loading: ref(false),
    init: vi.fn().mockResolvedValue(),
    refresh: vi.fn().mockResolvedValue(),
    switchTree: vi.fn().mockResolvedValue()
  })
  const wrapper = mount(Operations, {
    global: {
      plugins: [ElementPlus],
      components: {
        DurationInput: StubDurationInput,
        ControlCommandInput: StubControlCommandInput,
        TrendChart: StubTrendChart,
        InlineEdit: StubInlineEdit,
        ConfirmDialog: StubConfirmDialog,
        Skeleton: StubSkeleton,
      },
      stubs: {
        'right-toolbar': { template: '<div />' },
        RouterView: true,
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('Operations', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders operations page with tree sidebar layout', async () => {
    const wrapper = await mountAndFlush()
    expect(wrapper.find('.tree-sidebar-manage-wrap').exists()).toBe(true)
    wrapper.unmount()
  })

  it('initializes the asset tree panel on mount', async () => {
    const wrapper = await mountAndFlush()
    expect(useAssetTree).toHaveBeenCalled()
    expect(useAssetTree().init).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('renders tree sidebar with content layout', async () => {
    const wrapper = await mountAndFlush()
    expect(wrapper.find('.tree-sidebar-content').exists()).toBe(true)
    wrapper.unmount()
  })

  it('does not fetch asset detail for group or id-less synthetic nodes', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const { getAsset } = await import('@/api/iot/asset')

    vm.handleNodeClick({ key: 'GROUP:1', nodeKind: 'GROUP', id: 1, caption: '一楼', children: [
      { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, name: 'ups_001', caption: 'UPS' }
    ] })
    expect(getAsset).not.toHaveBeenCalled()
    expect(vm.isCompound).toBe(true)
    expect(vm.canAddChild).toBe(true)
    expect(vm.childCount).toBe(1)

    vm.handleNodeClick({ key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null, caption: '设备', children: [] })
    expect(getAsset).not.toHaveBeenCalled()
    expect(vm.detail).toEqual({})
    wrapper.unmount()
  })

  it('treats device assets as containers but monitors as leaves', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.handleNodeClick({ key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, children: [] })
    expect(vm.isCompound).toBe(true)
    expect(vm.canAddChild).toBe(true)

    vm.handleNodeClick({ key: 'ASSET:11', nodeKind: 'ASSET', assetKind: 'PROBE', id: 11, children: [] })
    expect(vm.isCompound).toBe(false)
    expect(vm.canAddChild).toBe(false)
    wrapper.unmount()
  })

  it('clears detail-driven state so enable/disable cannot act on a group selection', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.detail = { id: 11, kind: 'PROBE', enabled: true }
    vm.handleNodeClick({ key: 'GROUP:1', nodeKind: 'GROUP', id: 1, caption: '一楼', children: [] })
    expect(vm.canDisable).toBe(false)
    expect(vm.canEnable).toBe(false)
    wrapper.unmount()
  })

  it('restores the deep-linked asset selection on mount', async () => {
    routeMock.query = { node: '10' }
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const { getAsset } = await import('@/api/iot/asset')
    expect(getAsset).toHaveBeenCalledWith(10)
    expect(vm.selectedNode?.key).toBe('ASSET:10')
    routeMock.query = {}
    wrapper.unmount()
  })

  it('ignores a non-numeric deep-link parameter', async () => {
    routeMock.query = { node: 'abc' }
    const wrapper = await mountAndFlush()
    const { getAsset } = await import('@/api/iot/asset')
    expect(getAsset).not.toHaveBeenCalled()
    routeMock.query = {}
    wrapper.unmount()
  })
})

describe('VirtualProbe form', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('extractProbeIds parses #probe[N].value references from expression', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    // Simulate expression input
    vm.form.expression = '#probe[101].value / #probe[102].value * 100'
    vm.extractProbeIds()
    expect(vm.form.dependsOnIds).toEqual([101, 102])

    wrapper.unmount()
  })

  it('extractProbeIds deduplicates probe IDs', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.form.expression = '#probe[101].value + #probe[101].value'
    vm.extractProbeIds()
    expect(vm.form.dependsOnIds).toEqual([101])

    wrapper.unmount()
  })

  it('extractProbeIds does nothing for empty expression', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.form.dependsOnIds = [200]
    vm.form.expression = ''
    vm.extractProbeIds()
    expect(vm.form.dependsOnIds).toEqual([200])

    wrapper.unmount()
  })

  it('buildProperties includes isVirtual, expression, and dependsOn for VP probe', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.form.kind = 'PROBE'
    vm.form.isVirtual = true
    vm.form.expression = '#probe[101].value * 2'
    vm.form.dependsOnIds = [101]

    const props = vm.buildProperties()
    expect(props.isVirtual).toBe(1)
    expect(props.expression).toBe('#probe[101].value * 2')
    expect(props.dependsOn).toBe('101')

    wrapper.unmount()
  })

  it('buildProperties clears VP fields when isVirtual is false', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.form.kind = 'PROBE'
    vm.form.isVirtual = false
    vm.form.expression = ''
    vm.form.dependsOnIds = []
    vm.form.unit = '℃'

    const props = vm.buildProperties()
    expect(props).not.toBeNull()
    expect(props.isVirtual).toBe(0)
    expect(props.expression).toBe('')
    expect(props.dependsOn).toBe('')
    expect(props.unit).toBe('℃')

    wrapper.unmount()
  })

  it('openEditDialog parses dependsOn string to dependsOnIds array', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.detail = {
      kind: 'PROBE', name: 'eff', caption: '效率',
      isVirtual: true, expression: '#probe[101].value / #probe[102].value * 100',
      dependsOn: '101,102', unit: '%', minValue: null, maxValue: null
    }

    vm.openEditDialog()
    expect(vm.form.isVirtual).toBe(true)
    expect(vm.form.expression).toBe('#probe[101].value / #probe[102].value * 100')
    expect(vm.form.dependsOnIds).toEqual([101, 102])

    wrapper.unmount()
  })

  it('openEditDialog handles null dependsOn', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm

    vm.detail = {
      kind: 'PROBE', name: 'p1', caption: 'P1',
      isVirtual: false, expression: null, dependsOn: null,
      unit: '', minValue: null, maxValue: null
    }

    vm.openEditDialog()
    expect(vm.form.dependsOnIds).toEqual([])

    wrapper.unmount()
  })
})

describe('Create wizard', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('initializes wizardStep to 1 when opening create dialog', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    expect(vm.wizardStep).toBe(1)
    expect(vm.dialogMode).toBe('create')
    wrapper.unmount()
  })

  it('canNextStep requires kind and typeName for step 1', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    expect(vm.canNextStep).toBe(false)
    vm.form.kind = 'PROBE'
    expect(vm.canNextStep).toBe(false)
    vm.form.typeName = 'TemperatureProbe'
    expect(vm.canNextStep).toBe(true)
    wrapper.unmount()
  })

  it('canNextStep requires name and caption for step 2', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.wizardStep = 2
    expect(vm.canNextStep).toBe(false)
    vm.form.name = 'temp_probe'
    expect(vm.canNextStep).toBe(false)
    vm.form.caption = '温度探头'
    expect(vm.canNextStep).toBe(true)
    wrapper.unmount()
  })

  it('nextStep increments wizard step', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.form.kind = 'PROBE'
    vm.form.typeName = 'TemperatureProbe'
    vm.nextStep()
    expect(vm.wizardStep).toBe(2)
    wrapper.unmount()
  })

  it('nextStep does not increment when canNextStep is false', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    expect(vm.canNextStep).toBe(false)
    vm.nextStep()
    expect(vm.wizardStep).toBe(1)
    wrapper.unmount()
  })

  it('prevStep decrements wizard step', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.wizardStep = 3
    vm.prevStep()
    expect(vm.wizardStep).toBe(2)
    wrapper.unmount()
  })

  it('prevStep does not go below 1', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.wizardStep = 1
    vm.prevStep()
    expect(vm.wizardStep).toBe(1)
    wrapper.unmount()
  })

  it('resetForm resets wizardStep to 1', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.wizardStep = 3
    vm.resetForm()
    expect(vm.wizardStep).toBe(1)
    wrapper.unmount()
  })

  it('canNextStep is always true for steps 3 and 4', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.wizardStep = 3
    expect(vm.canNextStep).toBe(true)
    vm.wizardStep = 4
    expect(vm.canNextStep).toBe(true)
    wrapper.unmount()
  })

  it('submitForm warns visibly when validation fails instead of aborting silently', async () => {
    const { ElMessage } = await import('element-plus')
    const warnSpy = vi.spyOn(ElMessage, 'warning')
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.wizardStep = 4
    vm.formRef = { validate: vi.fn().mockRejectedValue({ fields: { name: [] } }) }
    await vm.submitForm()
    expect(warnSpy).toHaveBeenCalledTimes(1)
    expect(createAsset).not.toHaveBeenCalled()
    warnSpy.mockRestore()
    wrapper.unmount()
  })
})

describe('Forest create flow', () => {
  beforeEach(() => { vi.clearAllMocks() })

  // No formRef here: the opened dialog's el-form overwrites a pre-assigned
  // mock on the next flush, so tests assign the mock AFTER flushPromises.
  function openDeviceForm(vm) {
    vm.openCreateDialog()
    vm.form.kind = 'DEVICE'
    vm.form.typeName = 'SimDevice'
    vm.form.name = 'dev_1'
    vm.form.caption = 'Dev 1'
  }

  it('prefills groupIds when opening create from a group node', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.handleNodeClick({ key: 'GROUP:1', nodeKind: 'GROUP', id: 1, caption: '一楼', children: [] })
    vm.openCreateDialog()
    expect(vm.form.groupIds).toEqual([1])
    wrapper.unmount()
  })

  it('does not prefill groupIds when opening create from an asset node', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.handleNodeClick({ key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, children: [] })
    vm.openCreateDialog()
    expect(vm.form.groupIds).toEqual([])
    wrapper.unmount()
  })

  it('creates devices top-level (parentId 0) and attaches them to the selected groups', async () => {
    const { listGroupTrees, listGroups, replaceGroupAssets } = await import('@/api/iot/asset')
    listGroupTrees.mockResolvedValueOnce({ data: [{ id: 1, name: 'region', caption: '按区域' }] })
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, treeId: 1, assetIds: ['7'] }] })  // options load
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, treeId: 1, assetIds: ['7'] }] })  // merge read

    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    openDeviceForm(vm)
    vm.form.groupIds = [2]
    await flushPromises()
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    await vm.submitForm()

    const { showSystarError } = await import('@/utils/errorHandler')
    expect(showSystarError).not.toHaveBeenCalled()
    expect(createAsset).toHaveBeenCalledWith(expect.objectContaining({ kind: 'DEVICE', parentId: 0 }))
    expect(listGroups).toHaveBeenCalledWith(1)
    expect(replaceGroupAssets).toHaveBeenCalledWith(2, [7, 1])
    expect(useAssetTree().refresh).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('passes the create response (t_asset row id) into the group membership write', async () => {
    // Id spaces are deliberately misaligned: the create API responds with the
    // t_asset row id (22), not the runtime id (1003), and group assetIds are
    // row ids too — the merged write must carry [23, 22].
    const { listGroupTrees, listGroups, replaceGroupAssets } = await import('@/api/iot/asset')
    listGroupTrees.mockResolvedValueOnce({ data: [{ id: 1, name: 'region', caption: '按区域' }] })
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, treeId: 1, assetIds: ['23'] }] })  // options load
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, treeId: 1, assetIds: ['23'] }] })  // merge read
    createAsset.mockResolvedValueOnce({ data: 22 })

    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    openDeviceForm(vm)
    vm.form.groupIds = [2]
    await flushPromises()
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    await vm.submitForm()

    expect(createAsset).toHaveBeenCalledWith(expect.objectContaining({ kind: 'DEVICE', parentId: 0 }))
    expect(replaceGroupAssets).toHaveBeenCalledWith(2, [23, 22])
    wrapper.unmount()
  })

  it('creates monitors under the chosen parent device instead of a group path', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.openCreateDialog()
    vm.form.kind = 'PROBE'
    vm.form.typeName = 'TemperatureProbe'
    vm.form.name = 'p1'
    vm.form.caption = 'P1'
    vm.form.parentId = 10
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    await vm.submitForm()

    expect(createAsset).toHaveBeenCalledWith(expect.objectContaining({ kind: 'PROBE', parentId: 10 }))
    wrapper.unmount()
  })

  it('still creates the asset when the group write fails after creation', async () => {
    const { listGroupTrees, listGroups, replaceGroupAssets } = await import('@/api/iot/asset')
    const { showSystarError } = await import('@/utils/errorHandler')
    listGroupTrees.mockResolvedValueOnce({ data: [{ id: 1, name: 'region', caption: '按区域' }] })
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, treeId: 1, assetIds: [] }] })
    replaceGroupAssets.mockRejectedValueOnce(new Error('rel write boom'))

    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    openDeviceForm(vm)
    vm.form.groupIds = [2]
    await flushPromises()
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    await vm.submitForm()

    expect(createAsset).toHaveBeenCalled()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'rel write boom' }), '资产已创建，但写入分组失败')
    expect(vm.confirmDanger.dialogVisible).toBe(false)
    wrapper.unmount()
  })

  it('refreshes the tree panel after deleting an asset', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: 'Test' }
    vm.pendingDeleteId = 42
    await vm.doDelete()

    const { deleteAsset } = await import('@/api/iot/asset')
    expect(deleteAsset).toHaveBeenCalledWith(42)
    expect(useAssetTree().refresh).toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('Expression validator', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('accepts valid expression', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const callback = vi.fn()
    vm.expressionValidator({}, '#probe[101].value / #probe[102].value * 100', callback)
    expect(callback).toHaveBeenCalledWith()
    wrapper.unmount()
  })

  it('rejects mismatched brackets (unclosed)', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const callback = vi.fn()
    vm.expressionValidator({}, '#probe[101].value + (1 + 2', callback)
    expect(callback).toHaveBeenCalledWith(expect.objectContaining({ message: '括号不匹配' }))
    wrapper.unmount()
  })

  it('rejects closing bracket without opening', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const callback = vi.fn()
    vm.expressionValidator({}, '1 + 2)', callback)
    expect(callback).toHaveBeenCalledWith(expect.objectContaining({ message: '括号不匹配' }))
    wrapper.unmount()
  })

  it('rejects invalid probe reference', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const callback = vi.fn()
    vm.expressionValidator({}, '#probe[abc].value', callback)
    expect(callback).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('无效的探头引用') }))
    wrapper.unmount()
  })

  it('accepts empty expression', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const callback = vi.fn()
    vm.expressionValidator({}, '', callback)
    expect(callback).toHaveBeenCalledWith()
    wrapper.unmount()
  })
})

describe('InlineEdit integration', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('updateField sends top-level field for name/caption', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: 'Test' }
    await vm.updateField('name', 'new-name')
    const { updateAsset } = await import('@/api/iot/asset')
    expect(updateAsset).toHaveBeenCalledWith(42, { name: 'new-name' })
    wrapper.unmount()
  })

  it('updateField sends properties for non-top-level fields', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: 'Test', unit: '' }
    await vm.updateField('unit', '℃')
    const { updateAsset } = await import('@/api/iot/asset')
    expect(updateAsset).toHaveBeenCalledWith(42, { properties: { unit: '℃' } })
    wrapper.unmount()
  })
})

describe('useOperationStatus integration', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('handleOperate calls API and refreshes detail', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: 'Test' }
    await vm.handleOperate('start')
    const { startAsset } = await import('@/api/iot/asset')
    expect(startAsset).toHaveBeenCalledWith(42)
    wrapper.unmount()
  })

  it('handleRefresh calls detectAsset', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: 'Test' }
    await vm.handleRefresh()
    const { detectAsset } = await import('@/api/iot/asset')
    expect(detectAsset).toHaveBeenCalledWith(42)
    wrapper.unmount()
  })
})

describe('useConfirmDanger integration', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('passes unwrapped props to ConfirmDialog (refs must not leak into template)', async () => {
    const wrapper = await mountAndFlush()
    const dlg = wrapper.findComponent(ConfirmDialog)
    // Regression: nested refs on a plain composable object are NOT auto-unwrapped
    // in templates — a leaked Ref object is truthy and keeps the dialog open forever.
    expect(dlg.props('visible')).toBe(false)
    expect(dlg.props('title')).toBe('')
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: '测试资产' }
    vm.handleDelete()
    await nextTick()
    expect(dlg.props('visible')).toBe(true)
    expect(dlg.props('title')).toBe('删除确认')
    expect(dlg.props('message')).toBe('确认删除「测试资产」吗？删除后无法恢复。')
    wrapper.unmount()
  })

  it('handleDelete opens confirm dialog', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: '测试资产' }
    vm.handleDelete()
    expect(vm.confirmDanger.dialogVisible).toBe(true)
    wrapper.unmount()
  })

  it('doDelete calls deleteAsset when target matches', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: 'Test' }
    vm.pendingDeleteId = 42  // Set pending target
    await vm.doDelete()
    const { deleteAsset } = await import('@/api/iot/asset')
    expect(deleteAsset).toHaveBeenCalledWith(42)
    wrapper.unmount()
  })

  it('doDelete cancels when target changed', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 99, name: 'other', caption: 'Other' }
    vm.pendingDeleteId = 42  // Different from current detail
    await vm.doDelete()
    const { deleteAsset } = await import('@/api/iot/asset')
    expect(deleteAsset).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('batchOperate delete opens confirm dialog', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.selection = [
      { id: 1, name: 'a', caption: 'A' },
      { id: 2, name: 'b', caption: 'B' }
    ]
    vm.batchOperate('delete')
    expect(vm.confirmDanger.dialogVisible).toBe(true)
    expect(vm.confirmDanger.dialogImpact).toContain('A')
    expect(vm.pendingBatchAction).toBe('delete')
    expect(vm.pendingBatchIds).toEqual([1, 2])
    wrapper.unmount()
  })

  // Timeout warning toast is covered by useOperationStatus unit tests
  // (requires timer and watch interaction with composable internals)
})

describe('useAutoRefresh integration', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('creates childRefresh with useAutoRefresh', async () => {
    const { useAutoRefresh } = await import('@/composables/useAutoRefresh')
    await mountAndFlush()
    expect(useAutoRefresh).toHaveBeenCalled()
  })

  it('stops childRefresh when switching nodes', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    const { useAutoRefresh } = await import('@/composables/useAutoRefresh')
    const instance = useAutoRefresh.mock.results[0].value

    vm.detail = { id: 10, assetKind: 'DEVICE', children: [{ id: 30, state: 'NORMAL' }] }
    vm.handleNodeClick({ key: 'ASSET:2', nodeKind: 'ASSET', assetKind: 'PROBE', id: 2 })
    await flushPromises()
    expect(instance.stop).toHaveBeenCalled()
    wrapper.unmount()
  })
})
