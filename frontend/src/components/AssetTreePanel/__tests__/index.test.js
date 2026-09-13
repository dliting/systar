import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, defineComponent, nextTick } from 'vue'

vi.mock('@/composables/useAssetTree', () => ({
  KIND_TREE: 'kind',
  useAssetTree: vi.fn()
}))

vi.mock('@/api/iot/asset', async (importOriginal) => ({
  ...await importOriginal(),
  createGroup: vi.fn().mockResolvedValue(),
  deleteGroup: vi.fn().mockResolvedValue(),
  listGroups: vi.fn().mockResolvedValue({ data: [] }),
  reorderGroups: vi.fn().mockResolvedValue(),
  replaceGroupAssets: vi.fn().mockResolvedValue(),
  updateGroup: vi.fn().mockResolvedValue()
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn() }),
  ElMessageBox: { prompt: vi.fn(), confirm: vi.fn() }
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

import { useAssetTree } from '@/composables/useAssetTree'
import { ElMessage } from 'element-plus'
import { showSystarError } from '@/utils/errorHandler'
import { listGroups, reorderGroups, replaceGroupAssets, updateGroup } from '@/api/iot/asset'
import AssetTreePanel from '@/components/AssetTreePanel/index.vue'

// Id spaces are deliberately misaligned (as in the seed DB): `id` is the
// per-kind runtime id the /assets CRUD API addresses, `assetRowId` is the
// t_asset row id the group-membership writes must use. E.g. runtime 1003
// sits on t_asset row 22.
const kindForest = [
  { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', name: '设备', caption: '设备', id: null, assetRowId: null, children: [
    { key: 'ASSET:10', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 10, assetRowId: 3, name: 'ups_001', caption: 'UPS', state: 'NORMAL', enabled: true, children: [] }
  ] }
]

const groupForest = [
  { key: 'GROUP:1', nodeKind: 'GROUP', id: 1, name: 'g1', caption: '一楼', assetRowId: null, children: [
    { key: 'ASSET:1003', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 1003, assetRowId: 22, name: 'ups_001', caption: 'UPS', children: [] },
    { key: 'ASSET:100', nodeKind: 'ASSET', assetKind: 'SERVICE', id: 100, assetRowId: 10, name: 'ping', caption: 'Ping 服务', children: [] },
    { key: 'ASSET:2001', nodeKind: 'ASSET', assetKind: 'PROBE', id: 2001, assetRowId: 30, name: 'probe_1', caption: '探测器', children: [] }
  ] },
  { key: 'GROUP:2', nodeKind: 'GROUP', id: 2, name: 'g2', caption: '二楼', assetRowId: null, children: [] },
  { key: 'UNGROUPED', nodeKind: 'GROUP', id: null, name: null, caption: '未分组', assetRowId: null, children: [] }
]

const setCurrentKeySpy = vi.fn()

const TreePanelStub = defineComponent({
  name: 'TreePanelStub',
  props: ['treeData', 'draggable', 'allowDrop'],
  emits: ['node-click', 'node-drop', 'node-contextmenu', 'refresh'],
  methods: { setCurrentKey: setCurrentKeySpy },
  template: '<div class="tree-panel-stub" />'
})

const DialogStub = defineComponent({
  name: 'DialogStub',
  props: ['modelValue'],
  emits: ['update:modelValue', 'changed'],
  template: '<div class="dialog-stub" />'
})

/** Minimal el-tree node double: { data, parent: { data } }. */
function nodeOf(data, parentData = null) {
  return { data, parent: parentData === null ? undefined : { data: parentData } }
}

function mountPanel(composableOverrides = {}) {
  useAssetTree.mockReturnValue({
    trees: ref([{ id: 1, name: 'region', caption: '按区域', sequence: 1 }]),
    currentTree: ref('1'),
    currentTreeCaption: ref('按区域'),
    forest: ref(groupForest),
    loading: ref(false),
    init: vi.fn().mockResolvedValue(),
    refresh: vi.fn().mockResolvedValue(),
    switchTree: vi.fn().mockResolvedValue(),
    ...composableOverrides
  })
  return mount(AssetTreePanel, {
    global: { stubs: { TreePanel: TreePanelStub, GroupTreeManageDialog: DialogStub } }
  })
}

describe('AssetTreePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the forest with composite node keys', async () => {
    vi.mocked(useAssetTree).mockReturnValue({
      trees: ref([{ id: 1, name: 'region', caption: '按区域', sequence: 1 }]),
      currentTree: ref('kind'),
      currentTreeCaption: ref('按类型'),
      forest: ref(kindForest),
      loading: ref(false),
      init: vi.fn().mockResolvedValue(),
      refresh: vi.fn().mockResolvedValue(),
      switchTree: vi.fn().mockResolvedValue()
    })
    const wrapper = mount(AssetTreePanel, { global: { stubs: ['el-tree', 'TreePanel', 'GroupTreeManageDialog'] } })
    await flushPromises()
    expect(useAssetTree).toHaveBeenCalled()
    expect(wrapper.find('.tree-switcher').exists()).toBe(true)
  })

  it('keeps drag disabled on the kind tree and enabled on group trees', () => {
    const kindWrapper = mountPanel({ currentTree: ref('kind'), forest: ref(kindForest) })
    expect(kindWrapper.findComponent(TreePanelStub).props('draggable')).toBe(false)

    const groupWrapper = mountPanel()
    expect(groupWrapper.findComponent(TreePanelStub).props('draggable')).toBe(true)
  })

  it('allowDrop: assets may only drop into groups (inner)', () => {
    const wrapper      = mountPanel()
    const allowDrop    = wrapper.findComponent(TreePanelStub).props('allowDrop')
    const deviceLeaf   = groupForest[0].children[0]
    expect(allowDrop(nodeOf(deviceLeaf), nodeOf(groupForest[1]), 'inner')).toBe(true)
    expect(allowDrop(nodeOf(deviceLeaf), nodeOf(groupForest[1]), 'prev')).toBe(false)
    expect(allowDrop(nodeOf(deviceLeaf), nodeOf(deviceLeaf), 'inner')).toBe(false)
  })

  it('allowDrop: only DEVICE/SERVICE leaves may join a group', () => {
    const wrapper   = mountPanel()
    const allowDrop = wrapper.findComponent(TreePanelStub).props('allowDrop')
    const group     = groupForest[1]
    expect(allowDrop(nodeOf(groupForest[0].children[0]), nodeOf(group), 'inner')).toBe(true)
    expect(allowDrop(nodeOf(groupForest[0].children[1]), nodeOf(group), 'inner')).toBe(true)
    expect(allowDrop(nodeOf(groupForest[0].children[2]), nodeOf(group), 'inner')).toBe(false)
    expect(allowDrop(nodeOf(groupForest[0].children[2]), nodeOf(group), 'prev')).toBe(false)
  })

  it('allowDrop: the UNGROUPED bucket accepts only DEVICE/SERVICE assets, inner only', () => {
    const wrapper   = mountPanel()
    const allowDrop = wrapper.findComponent(TreePanelStub).props('allowDrop')
    const ungrouped = groupForest[2]
    const device    = groupForest[0].children[0]
    const service   = groupForest[0].children[1]
    const probe     = groupForest[0].children[2]
    expect(allowDrop(nodeOf(device), nodeOf(ungrouped), 'inner')).toBe(true)
    expect(allowDrop(nodeOf(service), nodeOf(ungrouped), 'inner')).toBe(true)
    expect(allowDrop(nodeOf(probe), nodeOf(ungrouped), 'inner')).toBe(false)
    expect(allowDrop(nodeOf(device), nodeOf(ungrouped), 'prev')).toBe(false)
    expect(allowDrop(nodeOf(groupForest[1]), nodeOf(ungrouped), 'inner')).toBe(false)
    expect(allowDrop(nodeOf(groupForest[1]), nodeOf(ungrouped), 'prev')).toBe(false)
  })

  it('allowDrop: the id-less UNGROUPED bucket can never be the drag source', () => {
    const wrapper   = mountPanel()
    const allowDrop = wrapper.findComponent(TreePanelStub).props('allowDrop')
    const ungrouped = groupForest[2]
    expect(allowDrop(nodeOf(ungrouped), nodeOf(groupForest[1]), 'inner')).toBe(false)
    expect(allowDrop(nodeOf(ungrouped), nodeOf(groupForest[1]), 'prev')).toBe(false)
    expect(allowDrop(nodeOf(ungrouped), nodeOf(groupForest[0]), 'inner')).toBe(false)
  })

  it('a null-id GROUP drag never reaches the update API', async () => {
    const wrapper = mountPanel()
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(groupForest[2]), nodeOf(groupForest[1]), 'inner')
    await flushPromises()
    expect(updateGroup).not.toHaveBeenCalled()
  })

  it('allowDrop: groups may drop anywhere except into themselves', () => {
    const wrapper   = mountPanel()
    const allowDrop = wrapper.findComponent(TreePanelStub).props('allowDrop')
    expect(allowDrop(nodeOf(groupForest[1]), nodeOf(groupForest[1]), 'inner')).toBe(false)
    expect(allowDrop(nodeOf(groupForest[1]), nodeOf(groupForest[0]), 'inner')).toBe(true)
    expect(allowDrop(nodeOf(groupForest[0]), nodeOf(groupForest[1]), 'prev')).toBe(true)
  })

  it('dropping an asset into a group writes merged membership in the t_asset row-id space', async () => {
    // The dragged node carries runtime id 1003 but row id 22; the group lists
    // row id 23. The write must merge row ids: [23, 22] — not the runtime id.
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, assetIds: ['23'] }] })
    const wrapper = mountPanel()
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(groupForest[0].children[0]), nodeOf(groupForest[1]), 'inner')
    await flushPromises()
    expect(listGroups).toHaveBeenCalledWith(1)
    expect(replaceGroupAssets).toHaveBeenCalledWith(2, [23, 22])
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('dropping an asset without assetRowId warns and never reaches the membership API', async () => {
    const wrapper = mountPanel()
    const ghost = { key: 'ASSET:77', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 77, assetRowId: null, name: 'ghost', caption: 'G', children: [] }
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(ghost), nodeOf(groupForest[1]), 'inner')
    await flushPromises()
    expect(listGroups).not.toHaveBeenCalled()
    expect(replaceGroupAssets).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledTimes(1)
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(ghost), nodeOf(groupForest[2]), 'inner')
    await flushPromises()
    expect(listGroups).not.toHaveBeenCalled()
    expect(replaceGroupAssets).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledTimes(2)
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('dropping a group sends a move-only payload with the new parent', async () => {
    const wrapper = mountPanel()
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(groupForest[1]), nodeOf(groupForest[0]), 'inner')
    await flushPromises()
    expect(updateGroup).toHaveBeenCalledWith(2, { treeId: 1, parent: 1 })
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('a prev drop sends one atomic reorder with the complete ordered id list', async () => {
    // Siblings a(1) b(2) c(3) d(4); dragging a before c yields [b, a, c, d] —
    // ONE reorder request carries the parent's full ordered child id list;
    // updateGroup stays the move write only, never a per-group sequence patch.
    // mockResolvedValue, not Once: an unconsumed once-value survives clearAllMocks
    // and would poison the next test that reads the list.
    listGroups.mockResolvedValue({ data: [
      { id: 1, treeId: 1, name: 'ga', caption: 'A', parent: 0, sequence: 1 },
      { id: 2, treeId: 1, name: 'gb', caption: 'B', parent: 0, sequence: 2 },
      { id: 3, treeId: 1, name: 'gc', caption: 'C', parent: 0, sequence: 3 },
      { id: 4, treeId: 1, name: 'gd', caption: 'D', parent: 0, sequence: 4 }
    ] })
    const wrapper = mountPanel()
    const ga = { key: 'GROUP:1', nodeKind: 'GROUP', id: 1, name: 'ga', caption: 'A', children: [] }
    const gc = { key: 'GROUP:3', nodeKind: 'GROUP', id: 3, name: 'gc', caption: 'C', children: [] }
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop', nodeOf(ga), nodeOf(gc), 'prev')
    await flushPromises()
    expect(updateGroup).toHaveBeenCalledTimes(1)
    expect(updateGroup).toHaveBeenCalledWith(1, { treeId: 1, parent: 0 }) // the move write only
    expect(reorderGroups).toHaveBeenCalledTimes(1)
    expect(reorderGroups).toHaveBeenCalledWith(1, { parent: 0, orderedGroupIds: [2, 1, 3, 4] })
    expect(ElMessage.success).toHaveBeenCalledWith('分组已移动')
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('a next drop sends the spliced order to the same atomic endpoint', async () => {
    // Siblings a(1) b(2) c(3) d(4); dragging d after b yields [a, b, d, c].
    listGroups.mockResolvedValue({ data: [
      { id: 1, treeId: 1, name: 'ga', caption: 'A', parent: 0, sequence: 1 },
      { id: 2, treeId: 1, name: 'gb', caption: 'B', parent: 0, sequence: 2 },
      { id: 3, treeId: 1, name: 'gc', caption: 'C', parent: 0, sequence: 3 },
      { id: 4, treeId: 1, name: 'gd', caption: 'D', parent: 0, sequence: 4 }
    ] })
    const wrapper = mountPanel()
    const gd = { key: 'GROUP:4', nodeKind: 'GROUP', id: 4, name: 'gd', caption: 'D', children: [] }
    const gb = { key: 'GROUP:2', nodeKind: 'GROUP', id: 2, name: 'gb', caption: 'B', children: [] }
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop', nodeOf(gd), nodeOf(gb), 'next')
    await flushPromises()
    expect(updateGroup).toHaveBeenCalledTimes(1)
    expect(reorderGroups).toHaveBeenCalledTimes(1)
    expect(reorderGroups).toHaveBeenCalledWith(1, { parent: 0, orderedGroupIds: [1, 2, 4, 3] })
  })

  it('a prev drop that leaves the order unchanged still sends the identity reorder', async () => {
    // a(1) b(2); dragging a before b is the identity order — the request is
    // still sent (complete-set semantics; the server rewrite is idempotent).
    listGroups.mockResolvedValue({ data: [
      { id: 1, treeId: 1, name: 'ga', caption: 'A', parent: 0, sequence: 1 },
      { id: 2, treeId: 1, name: 'gb', caption: 'B', parent: 0, sequence: 2 }
    ] })
    const wrapper = mountPanel()
    const ga = { key: 'GROUP:1', nodeKind: 'GROUP', id: 1, name: 'ga', caption: 'A', children: [] }
    const gb = { key: 'GROUP:2', nodeKind: 'GROUP', id: 2, name: 'gb', caption: 'B', children: [] }
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop', nodeOf(ga), nodeOf(gb), 'prev')
    await flushPromises()
    expect(updateGroup).toHaveBeenCalledTimes(1)
    expect(updateGroup).toHaveBeenCalledWith(1, { treeId: 1, parent: 0 })
    expect(reorderGroups).toHaveBeenCalledTimes(1)
    expect(reorderGroups).toHaveBeenCalledWith(1, { parent: 0, orderedGroupIds: [1, 2] })
    expect(ElMessage.success).toHaveBeenCalledWith('分组已移动')
  })

  it('a failed reorder surfaces the error and refreshes', async () => {
    listGroups.mockResolvedValue({ data: [
      { id: 1, treeId: 1, name: 'ga', caption: 'A', parent: 0, sequence: 1 },
      { id: 2, treeId: 1, name: 'gb', caption: 'B', parent: 0, sequence: 2 }
    ] })
    reorderGroups.mockRejectedValueOnce(new Error('stale list'))
    const wrapper = mountPanel()
    const ga = { key: 'GROUP:1', nodeKind: 'GROUP', id: 1, name: 'ga', caption: 'A', children: [] }
    const gb = { key: 'GROUP:2', nodeKind: 'GROUP', id: 2, name: 'gb', caption: 'B', children: [] }
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop', nodeOf(ga), nodeOf(gb), 'prev')
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('stale list')
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('dropping an asset into UNGROUPED removes its row id from every owning group of the tree', async () => {
    listGroups.mockResolvedValueOnce({ data: [
      { id: 2, assetIds: ['22', '24'] },
      { id: 3, assetIds: ['25'] },
      { id: 4, assetIds: ['22'] }
    ] })
    const wrapper = mountPanel()
    const ups = groupForest[0].children[0] // runtime id 1003, row id 22
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(ups), nodeOf(groupForest[2]), 'inner')
    await flushPromises()
    expect(listGroups).toHaveBeenCalledWith(1)
    expect(replaceGroupAssets).toHaveBeenCalledTimes(2)
    expect(replaceGroupAssets).toHaveBeenNthCalledWith(1, 2, [24])
    expect(replaceGroupAssets).toHaveBeenNthCalledWith(2, 4, [])
    expect(ElMessage.success).toHaveBeenCalledWith('已从分组卸载')
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('dropping an asset into UNGROUPED skips the API when no group lists its row id', async () => {
    listGroups.mockResolvedValueOnce({ data: [{ id: 2, assetIds: ['23', '24'] }] })
    const wrapper = mountPanel()
    const ping = groupForest[0].children[1] // row id 10, listed by no group
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(ping), nodeOf(groupForest[2]), 'inner')
    await flushPromises()
    expect(replaceGroupAssets).not.toHaveBeenCalled()
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('reloads when the inner TreePanel emits refresh', async () => {
    const wrapper = mountPanel()
    wrapper.findComponent(TreePanelStub).vm.$emit('refresh')
    await flushPromises()
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('surfaces an error when the TreePanel refresh fails', async () => {
    const wrapper = mountPanel({ refresh: vi.fn().mockRejectedValue(new Error('panel refresh boom')) })
    wrapper.findComponent(TreePanelStub).vm.$emit('refresh')
    await flushPromises()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'panel refresh boom' }), '刷新失败')
  })

  it('a failed drop still refreshes and surfaces the backend error', async () => {
    replaceGroupAssets.mockRejectedValueOnce(new Error('boom'))
    const wrapper = mountPanel()
    wrapper.findComponent(TreePanelStub).vm.$emit('node-drop',
      nodeOf(groupForest[0].children[0]), nodeOf(groupForest[1]), 'inner')
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('boom')
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('context menu opens only for group nodes on group trees', async () => {
    const wrapper = mountPanel()
    const treeStub = wrapper.findComponent(TreePanelStub)
    const evt = { preventDefault: vi.fn(), clientX: 10, clientY: 20 }

    treeStub.vm.$emit('node-contextmenu', evt, groupForest[0].children[0])
    await nextTick()
    expect(wrapper.find('.context-menu').exists()).toBe(false)

    treeStub.vm.$emit('node-contextmenu', evt, groupForest[0])
    await nextTick()
    expect(evt.preventDefault).toHaveBeenCalled()
    expect(wrapper.find('.context-menu').exists()).toBe(true)

    const kindWrapper = mountPanel({ currentTree: ref('kind') })
    kindWrapper.findComponent(TreePanelStub).vm.$emit('node-contextmenu', evt, groupForest[0])
    await nextTick()
    expect(kindWrapper.find('.context-menu').exists()).toBe(false)
  })

  it('context menu stays closed on the id-less UNGROUPED bucket', async () => {
    const wrapper = mountPanel()
    const evt = { preventDefault: vi.fn(), clientX: 10, clientY: 20 }
    wrapper.findComponent(TreePanelStub).vm.$emit('node-contextmenu', evt, groupForest[2])
    await nextTick()
    expect(wrapper.find('.context-menu').exists()).toBe(false)
  })

  it('surfaces an error when the initial tree load fails', async () => {
    mountPanel({ init: vi.fn().mockRejectedValue(new Error('net down')) })
    await flushPromises()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'net down' }), '加载资产树失败')
  })

  it('surfaces an error when switching trees fails', async () => {
    const wrapper = mountPanel({ switchTree: vi.fn().mockRejectedValue(new Error('switch boom')) })
    await wrapper.find('el-select').trigger('change')
    await flushPromises()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'switch boom' }), '切换分组树失败')
  })

  it('surfaces an error when the refresh button fails', async () => {
    const wrapper = mountPanel({ refresh: vi.fn().mockRejectedValue(new Error('refresh boom')) })
    await wrapper.find('el-button[title="刷新"]').trigger('click')
    await flushPromises()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'refresh boom' }), '刷新失败')
  })

  it('surfaces an error when the post-change reload fails', async () => {
    const wrapper = mountPanel({ refresh: vi.fn().mockRejectedValue(new Error('reload boom')) })
    wrapper.findComponent(DialogStub).vm.$emit('changed')
    await flushPromises()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'reload boom' }), '刷新失败')
  })

  it('exposes refresh so parent views can reload after content-side mutations', async () => {
    const wrapper = mountPanel()
    await wrapper.vm.refresh()
    expect(useAssetTree().refresh).toHaveBeenCalled()
  })

  it('selectAssetById highlights the node and notifies like a user click', async () => {
    const wrapper = mountPanel()
    await wrapper.vm.selectAssetById(1003)
    expect(setCurrentKeySpy).toHaveBeenCalledWith('ASSET:1003')
    expect(wrapper.emitted('node-click')).toEqual([[groupForest[0].children[0]]])
  })

  it('selectAssetById waits for the initial load before searching the forest', async () => {
    let resolveInit
    const wrapper = mountPanel({ init: vi.fn(() => new Promise(resolve => { resolveInit = resolve })) })
    const pending = wrapper.vm.selectAssetById(1003)
    expect(setCurrentKeySpy).not.toHaveBeenCalled()
    resolveInit()
    await pending
    expect(setCurrentKeySpy).toHaveBeenCalledWith('ASSET:1003')
  })

  it('selectAssetById is a no-op for an asset missing from the forest', async () => {
    const wrapper = mountPanel()
    await expect(wrapper.vm.selectAssetById(999)).resolves.toBeUndefined()
    expect(setCurrentKeySpy).not.toHaveBeenCalled()
    expect(wrapper.emitted('node-click')).toBeUndefined()
  })
})
