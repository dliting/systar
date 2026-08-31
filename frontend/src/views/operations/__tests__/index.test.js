import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { defineComponent, nextTick } from 'vue'

vi.mock('@/api/iot/asset', () => ({
  getAssetTree: vi.fn().mockResolvedValue({ data: { id: 1, name: 'root', kind: 'SPACE', children: [] } }),
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

vi.mock('@/composables/useAutoRefresh', () => ({
  useAutoRefresh: vi.fn(() => ({
    enabled: { value: true },
    highlightedIds: { value: new Set() },
    start: vi.fn(),
    stop: vi.fn(),
  }))
}))

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn() })),
  useRoute: vi.fn(() => ({ path: '/operations', query: {} }))
}))

// Stub components that have ref methods called by the parent
const StubTreePanel = defineComponent({
  name: 'TreePanel',
  props: ['title', 'treeData', 'treeProps', 'searchPlaceholder', 'storageKey', 'defaultExpandAll'],
  methods: { setCurrentKey: vi.fn() },
  template: '<div class="stub-tree-panel" />'
})
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

async function mountAndFlush() {
  const wrapper = mount(Operations, {
    global: {
      plugins: [ElementPlus],
      components: {
        TreePanel: StubTreePanel,
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

  it('calls getAssetTree on mount', async () => {
    const wrapper = await mountAndFlush()
    const { getAssetTree } = await import('@/api/iot/asset')
    expect(getAssetTree).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('renders tree sidebar with content layout', async () => {
    const wrapper = await mountAndFlush()
    expect(wrapper.find('.tree-sidebar-content').exists()).toBe(true)
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

  it('handleDelete opens confirm dialog', async () => {
    const wrapper = await mountAndFlush()
    const vm = wrapper.vm
    vm.detail = { id: 42, name: 'test', caption: '测试资产' }
    vm.handleDelete()
    expect(vm.confirmDanger.dialogVisible.value).toBe(true)
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
    expect(vm.confirmDanger.dialogVisible.value).toBe(true)
    expect(vm.confirmDanger.dialogImpact.value).toContain('A')
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

    vm.detail = { id: 1, kind: 'SPACE', children: [{ id: 10, state: 'NORMAL' }] }
    vm.handleNodeClick({ id: 2, kind: 'PROBE' })
    await flushPromises()
    expect(instance.stop).toHaveBeenCalled()
    wrapper.unmount()
  })
})
