<template>
  <div class="asset-tree-panel">
    <div class="tree-switcher">
      <el-select :model-value="currentTree" size="small" @change="onSwitch">
        <el-option label="按类型" value="kind" />
        <el-option v-for="t in trees" :key="t.id" :label="t.caption || t.name" :value="String(t.id)" />
      </el-select>
      <el-button size="small" text :icon="Setting" @click="manageVisible = true" title="管理分组树" />
      <el-button size="small" text :icon="Refresh" @click="onRefresh" title="刷新" />
    </div>
    <TreePanel
      ref="treePanelRef"
      :tree-data="forest"
      :tree-props="{ label: 'caption', children: 'children' }"
      node-key="key"
      :draggable="currentTree !== KIND_TREE"
      :allow-drop="allowDrop"
      :expand-on-click-node="false"
      @node-click="(data, node, tv) => emit('node-click', data, node, tv)"
      @node-drop="onNodeDrop"
      @node-contextmenu="onContextMenu"
      @refresh="onRefresh"
    />
    <div v-if="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
      <div class="context-menu-item" @click="createChildGroup">新建子分组</div>
      <div class="context-menu-item" @click="renameGroup">重命名</div>
      <div class="context-menu-item danger" @click="removeGroup">删除分组</div>
    </div>
    <GroupTreeManageDialog v-model="manageVisible" @changed="onRefresh" />
  </div>
</template>

<script setup>
import { reactive, ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Setting } from '@element-plus/icons-vue'
import TreePanel from '@/components/TreePanel/index.vue'
import GroupTreeManageDialog from './GroupTreeManageDialog.vue'
import { KIND_TREE, useAssetTree } from '@/composables/useAssetTree'
import { showSystarError } from '@/utils/errorHandler'
import {
  createGroup, deleteGroup, listGroups, replaceGroupAssets, updateGroup
} from '@/api/iot/asset'

const ROOT_PARENT_ID     = 0
const MEMBER_ASSET_KINDS = ['DEVICE', 'SERVICE']
const UNGROUPED_KEY      = 'UNGROUPED'

const emit = defineEmits(['node-click'])

const { trees, currentTree, forest, init, refresh, switchTree } = useAssetTree()
const treePanelRef  = ref(null)
const manageVisible = ref(false)
const contextMenu   = reactive({ visible: false, x: 0, y: 0, data: null })
let initialLoad     = null

onMounted(() => {
  initialLoad = load()
  document.addEventListener('click', hideContextMenu, true)
})
onBeforeUnmount(() => document.removeEventListener('click', hideContextMenu, true))

function hideContextMenu() {
  contextMenu.visible = false
}

/** Initial forest load; failures surface via the shared error helper. */
async function load() {
  try {
    await init()
  } catch (e) {
    showSystarError(e, '加载资产树失败')
  }
}

async function onSwitch(value) {
  try {
    await switchTree(value)
  } catch (e) {
    showSystarError(e, '切换分组树失败')
  }
}

async function onRefresh() {
  try {
    await refresh()
  } catch (e) {
    showSystarError(e, '刷新失败')
  }
}

/** The id-less UNGROUPED bucket on group trees holds assets that belong to no
 *  group of the current tree. Dropping a member asset into it unmounts the
 *  asset from every group of that tree; cross-tree mounting is unaffected.
 *  The kind tree disables dragging entirely, so it never reaches here. */
function isUngroupedBucket(data) {
  return data.nodeKind === 'GROUP' && data.id == null && data.key === UNGROUPED_KEY
}

/** Drag rules: DEVICE/SERVICE leaves -> group (join) or UNGROUPED bucket
 *  (unmount, inner only); group -> group/prev/next. Monitor leaves and the
 *  bucket itself are never eligible drag sources or group drop targets. */
function allowDrop(draggingNode, dropNode, type) {
  const dragData = draggingNode.data
  const dropData = dropNode.data
  if (isUngroupedBucket(dropData)) {
    return type === 'inner'
      && dragData.nodeKind === 'ASSET'
      && MEMBER_ASSET_KINDS.includes(dragData.assetKind)
  }
  if (dropData.nodeKind !== 'GROUP' || dropData.id == null) return false
  if (dragData.nodeKind === 'ASSET') {
    return type === 'inner' && MEMBER_ASSET_KINDS.includes(dragData.assetKind)
  }
  if (dragData.nodeKind === 'GROUP') return dragData.id != null && dragData.id !== dropData.id
  return false
}

/** Drop handler: asset into UNGROUPED = unmount from all groups; asset into
 *  group = membership write; group move = parent change. */
async function onNodeDrop(draggingNode, dropNode, dropType) {
  const dragData = draggingNode.data
  try {
    if (dragData.nodeKind === 'ASSET') {
      await onAssetDrop(dragData, dropNode, dropType)
    } else if (dragData.nodeKind === 'GROUP') {
      // Defensive: the id-less UNGROUPED bucket never moves (allowDrop already blocks it).
      if (dragData.id == null) return
      const parentId = dropType === 'inner' ? dropNode.data.id : dropNode.parent?.data?.id ?? ROOT_PARENT_ID
      await updateGroup(dragData.id, { treeId: Number(currentTree.value), parent: parentId })
      if (dropType === 'prev' || dropType === 'next') {
        await reorderSiblings(dragData, dropNode, dropType, parentId)
      }
      ElMessage.success('分组已移动')
    }
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
    await refresh()
  }
}

/** Membership writes address t_asset rows, so the node's assetRowId is the
 *  write key — never the runtime id (the two spaces are independent). A node
 *  without its row id (no view row behind the live asset) warns and skips. */
async function onAssetDrop(dragData, dropNode, dropType) {
  if (dragData.assetRowId == null) {
    ElMessage.warning('该资产缺少统一视图行（t_asset），分组操作未执行')
    return
  }
  if (isUngroupedBucket(dropNode.data)) {
    await unmountAssetFromCurrentTree(dragData.assetRowId)
    ElMessage.success('已从分组卸载')
  } else if (dropType === 'inner') {
    const groupId = dropNode.data.id
    const assetIds = await listMembersForWrite(groupId, dragData.assetRowId)
    await replaceGroupAssets(groupId, assetIds)
    ElMessage.success('已挂载到分组')
  }
}

/** Remove the asset from every group of the current tree that still lists its
 *  row id (read-modify-write per group, spec §4.5 single-admin assumption). */
async function unmountAssetFromCurrentTree(assetRowId) {
  const res = await listGroups(Number(currentTree.value))
  const owningGroups = (res.data || [])
    .filter(g => (g.assetIds || []).map(Number).includes(assetRowId))
  for (const group of owningGroups) {
    const remaining = (group.assetIds || []).map(Number).filter(id => id !== assetRowId)
    await replaceGroupAssets(group.id, remaining)
  }
}

/** Read-modify-write membership (single-admin assumption, spec §4.5). */
async function listMembersForWrite(groupId, assetRowId) {
  const res   = await listGroups(Number(currentTree.value))
  const group = (res.data || []).find(g => g.id === groupId)
  const ids   = (group?.assetIds || []).map(Number)
  if (!ids.includes(assetRowId)) ids.push(assetRowId)
  return ids
}

/** Sibling resequence for prev/next group drops: splice the dragged group in
 *  before/after the drop target, then rewrite the parent's child order 1..n —
 *  writing only the groups whose sequence actually changed (rename+sequence
 *  payload; the parent change above already went through the move path).
 *  Stored sequences come from the fetched list: tree-node data carries none. */
async function reorderSiblings(dragData, dropNode, dropType, parentId) {
  const treeId = Number(currentTree.value)
  const res    = await listGroups(treeId)
  const rows   = res.data || []
  const storedSequenceById = new Map(rows.map(g => [g.id, g.sequence]))
  const order  = rows.filter(g => Number(g.parent) === Number(parentId) && g.id !== dragData.id)
  const pivot  = order.findIndex(g => g.id === dropNode.data.id)
  if (pivot < 0) return
  order.splice(dropType === 'prev' ? pivot : pivot + 1, 0, dragData)
  for (let i = 0; i < order.length; i++) {
    const group    = order[i]
    const sequence = i + 1
    if (sequence === storedSequenceById.get(group.id)) continue
    await updateGroup(group.id, { name: group.name, caption: group.caption, sequence })
  }
}

function onContextMenu(event, data) {
  if (currentTree.value === KIND_TREE || data.nodeKind !== 'GROUP' || data.id == null) return
  event.preventDefault()
  Object.assign(contextMenu, { visible: true, x: event.clientX, y: event.clientY, data })
}

async function createChildGroup() {
  const parent = contextMenu.data
  try {
    const { value } = await ElMessageBox.prompt('分组显示名', '新建子分组', { inputValue: '' })
    await createGroup({
      treeId: Number(currentTree.value),
      name: `group_${Date.now()}`,
      caption: value,
      parent: parent.id,
      sequence: (parent.children?.length || 0) + 1
    })
    await refresh()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function renameGroup() {
  const group = contextMenu.data
  try {
    const { value } = await ElMessageBox.prompt('分组显示名', '重命名', { inputValue: group.caption })
    await updateGroup(group.id, { name: group.name, caption: value })
    await refresh()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function removeGroup() {
  const group = contextMenu.data
  try {
    await ElMessageBox.confirm(`删除分组「${group.caption}」？`, '提示', { type: 'warning' })
    await deleteGroup(group.id)
    await refresh()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

/** Deep-link restore: highlight the asset and notify consumers via node-click.
 *  Handler contract: the first argument is always the node data; the node /
 *  tree-instance arguments exist only on real click events.
 *  Waits for the initial load; assets missing from the forest are ignored. */
async function selectAssetById(assetId) {
  await initialLoad
  // Let the el-tree data watcher rebuild its nodesMap before setting the
  // current key — pre-flush job ordering is an implementation detail, not a contract.
  await nextTick()
  const target = findNodeByKey(forest.value, `ASSET:${assetId}`)
  if (!target) return
  treePanelRef.value?.setCurrentKey(target.key)
  emit('node-click', target)
}

function findNodeByKey(nodes, key) {
  for (const node of nodes || []) {
    if (node.key === key) return node
    const found = findNodeByKey(node.children, key)
    if (found) return found
  }
  return null
}

// Parent views call refresh after content-side mutations (create/delete) and
// selectAssetById to restore a deep-linked selection (?node=<id>). The latter
// emits node-click with the node data as its single argument (no node /
// tree-instance args, unlike a real click).
defineExpose({ refresh: onRefresh, selectAssetById })
</script>

<style scoped>
.asset-tree-panel { height: 100%; display: flex; flex-direction: column; }
.tree-switcher { display: flex; gap: 4px; padding: 4px 8px; align-items: center; }
.tree-switcher .el-select { flex: 1; }
.context-menu {
  position: fixed; z-index: 3000; background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light); border-radius: 4px;
  padding: 4px 0; box-shadow: var(--el-box-shadow-light);
}
.context-menu-item { padding: 4px 16px; font-size: 13px; cursor: pointer; }
.context-menu-item:hover { background: var(--el-fill-color-light); }
.context-menu-item.danger { color: var(--el-color-danger); }
</style>
