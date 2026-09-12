<template>
  <el-dialog :model-value="modelValue" title="管理分组树" width="640px" @update:model-value="v => emit('update:modelValue', v)" @open="load">
    <el-form inline>
      <el-select v-model="selectedTree" placeholder="选择树" style="width: 200px" @change="loadGroups">
        <el-option v-for="t in treeList" :key="t.id" :label="t.caption || t.name" :value="t.id" />
      </el-select>
      <el-button @click="createTree">新建树</el-button>
      <el-button :disabled="!selectedTree" @click="createGroupRow">新建分组</el-button>
      <el-button :disabled="!selectedTree" type="danger" plain @click="removeTree">删除树</el-button>
    </el-form>
    <el-table :data="groupRows" size="small" max-height="360">
      <el-table-column prop="caption" label="分组" />
      <el-table-column prop="level" label="层级" width="70" />
      <el-table-column label="成员" width="90">
        <template #default="{ row }">{{ (row.assetIds || []).length }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link size="small" @click="renameRow(row)">改名</el-button>
          <el-button link size="small" type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showSystarError } from '@/utils/errorHandler'
import {
  createGroup, createGroupTree, deleteGroup, deleteGroupTree,
  listGroupTrees, listGroups, updateGroup
} from '@/api/iot/asset'

const ROOT_PARENT_ID = 0

const props = defineProps({ modelValue: Boolean })
const emit  = defineEmits(['update:modelValue', 'changed'])

const treeList    = ref([])
const selectedTree = ref(null)
const groupRows   = ref([])

async function load() {
  try {
    const res      = await listGroupTrees()
    treeList.value = res.data || []
    if (!selectedTree.value && treeList.value.length) {
      selectedTree.value = treeList.value[0].id
    }
    await loadGroups()
  } catch (e) {
    showSystarError(e, '加载分组树失败')
  }
}

async function loadGroups() {
  if (!selectedTree.value) { groupRows.value = []; return }
  try {
    const res       = await listGroups(selectedTree.value)
    groupRows.value = res.data || []
  } catch (e) {
    showSystarError(e, '加载分组失败')
  }
}

async function createTree() {
  try {
    const { value } = await ElMessageBox.prompt('树标识（英文，全局唯一）', '新建分组树')
    const { value: caption } = await ElMessageBox.prompt('显示名', '新建分组树')
    await createGroupTree({ name: value, caption, sequence: treeList.value.length + 1 })
    await load()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function removeTree() {
  try {
    await ElMessageBox.confirm('删除整棵树？需先删空全部分组。', '提示', { type: 'warning' })
    await deleteGroupTree(selectedTree.value)
    selectedTree.value = null
    await load()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function createGroupRow() {
  try {
    const { value } = await ElMessageBox.prompt('分组显示名', '新建分组')
    await createGroup({ treeId: selectedTree.value, name: `group_${Date.now()}`, caption: value, parent: ROOT_PARENT_ID })
    await loadGroups()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function renameRow(row) {
  try {
    const { value } = await ElMessageBox.prompt('分组显示名', '重命名', { inputValue: row.caption })
    await updateGroup(row.id, { treeId: row.treeId, name: row.name, caption: value })
    await loadGroups()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function removeRow(row) {
  try {
    await ElMessageBox.confirm(`删除分组「${row.caption}」？`, '提示', { type: 'warning' })
    await deleteGroup(row.id)
    await loadGroups()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}
</script>
