<template>
  <div class="app-container tree-sidebar-manage-wrap">
    <tree-panel
      title="资产树"
      :tree-data="treeData"
      :tree-props="{ children: 'children', label: 'caption' }"
      search-placeholder="搜索资产名称"
      storage-key="iot-schedule-sidebar-width"
      :default-expand-all="true"
      @node-click="handleTreeNodeClick"
      @refresh="loadTree"
      ref="treeRef"
    >
      <template #node="{ data }">
        <span class="tree-node">
          <el-icon class="node-icon">
            <FolderOpened v-if="data.kind === 'SPACE'" />
            <Monitor v-else-if="data.kind === 'DEVICE'" />
            <Odometer v-else-if="data.kind === 'PROBE'" />
            <Setting v-else-if="data.kind === 'CONTROL'" />
            <Service v-else-if="data.kind === 'SERVICE'" />
            <Document v-else />
          </el-icon>
          <span class="node-label" :title="data.caption || data.name">{{ data.caption || data.name }}</span>
          <el-tag
            v-if="data.state"
            :type="stateTagType(data.state)"
            size="small"
            class="node-state-tag"
          >{{ data.stateCaption || data.state }}</el-tag>
        </span>
      </template>
    </tree-panel>

    <div class="tree-sidebar-content">
      <div class="content-inner">
        <Breadcrumb />
        <!-- Toolbar -->
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd">新增任务</el-button>
          </el-col>
          <el-col :span="4">
            <el-input v-model="searchKeyword" placeholder="搜索任务名称" clearable @clear="loadTasks" @keyup.enter="loadTasks" />
          </el-col>
          <el-col :span="1.5">
            <el-button type="primary" icon="Search" @click="loadTasks">搜索</el-button>
          </el-col>
        </el-row>

        <EnhancedTable
          :data="tasks"
          :columns="taskColumns"
          :loading="loading"
          :exportable="true"
          export-file-name="定时任务"
          :context-menu="scheduleContextMenu"
          @context-command="handleScheduleContextCmd"
        >
          <template #column-controlId="{ row }">
            <span v-if="row.targetName" style="color:#409eff">{{ row.targetName }} <span style="color:#999">#{{ row.controlId }}</span></span>
            <span v-else>#{{ row.controlId }}</span>
          </template>
          <template #column-enabled="{ row }">
            <el-switch v-model="row.enabled" @change="val => handleToggle(row, val)" />
          </template>
          <template #column-nextFireTime="{ row }">{{ formatTime(row.nextFireTime) }}</template>
          <template #column-operations="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="handleLogs(row)">日志</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </EnhancedTable>

        <!-- Add/Edit dialog -->
        <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑任务' : '新增任务'" width="500px" destroy-on-close :modal="false">
          <el-form ref="formRef" :model="form" label-width="100px">
            <el-form-item label="任务名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
            <el-form-item label="目标监控器" required>
              <div style="display:flex; gap:8px; align-items:center;">
                <el-input-number v-model="form.controlId" :min="1" />
                <span v-if="resolveTargetName(form.controlId)" style="color:#409eff; white-space:nowrap;">
                  {{ resolveTargetName(form.controlId) }}
                </span>
                <span v-else-if="form.controlId" style="color:#999; white-space:nowrap;">未知资产</span>
              </div>
              <div style="font-size:11px; color:#999; margin-top:2px;">点击左侧资产树可自动填充</div>
            </el-form-item>
            <el-form-item label="命令" required>
              <el-input v-model="form.command" />
            </el-form-item>
            <el-form-item label="调度规则" required>
              <CronWizard v-model="form.cronExpression" :preview-next="previewNext" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitForm">确定</el-button>
          </template>
        </el-dialog>

        <!-- Logs dialog -->
        <el-dialog v-model="logsVisible" title="执行日志" width="700px" destroy-on-close>
          <el-table :data="logs" max-height="400">
            <el-table-column prop="executeTime" label="执行时间" width="170">
              <template #default="{ row }">{{ new Date(row.executeTime).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="command" label="命令" width="80" />
            <el-table-column label="结果" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误信息" min-width="200" />
          </el-table>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTasks, createTask, updateTask, deleteTask, enableTask, disableTask, getTaskLogs, previewCron } from '@/api/iot/scheduledTask'
import { getAssetTree } from '@/api/iot/asset'
import { stateTagType } from '@/utils/formatters'
import { showSystarError } from '@/utils/errorHandler'
import CronWizard from '@/components/CronWizard/index.vue'
import TreePanel from '@/components/TreePanel'
import { FolderOpened, Monitor, Odometer, Setting, Service, Document } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import EnhancedTable from '@/components/EnhancedTable/index.vue'

// ======================== table config ========================

const taskColumns = [
  { prop: 'id', label: 'ID', width: 60, sortable: true },
  { prop: 'name', label: '任务名称', minWidth: 120, showOverflowTooltip: true },
  { prop: 'controlId', label: '目标', minWidth: 140, slot: true },
  { prop: 'command', label: '命令', width: 100 },
  { prop: 'cronExpression', label: 'Cron 表达式', width: 140 },
  { prop: 'enabled', label: '状态', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '启用', value: true }, { label: '禁用', value: false }] },
  { prop: 'nextFireTime', label: '下次执行', width: 170, slot: true },
  { prop: 'operations', label: '操作', width: 200, fixed: 'right', slot: true },
]

const scheduleContextMenu = [
  { label: '编辑', command: 'edit' },
  { label: '日志', command: 'logs' },
  { label: '删除', command: 'delete', danger: true, divided: true },
]

function handleScheduleContextCmd(command, row) {
  if (command === 'edit') handleEdit(row)
  else if (command === 'logs') handleLogs(row)
  else if (command === 'delete') handleDelete(row)
}

const loading       = ref(false)
const tasks         = ref([])
const searchKeyword = ref('')

const dialogVisible = ref(false)
const isEdit        = ref(false)
const form          = ref({})
const previewNext   = ref(null)

const logsVisible = ref(false)
const logs        = ref([])

// Tree state
const treeRef      = ref(null)
const assetTree    = ref({})
const assetNameMap = ref({})

const treeData = computed(() =>
  (assetTree.value && assetTree.value.id !== undefined) ? [assetTree.value] : []
)

let debounceTimer = null
watch(() => form.value.cronExpression, (val) => {
  clearTimeout(debounceTimer)
  if (!val || !val.trim()) {
    previewNext.value = null
    return
  }
  debounceTimer = setTimeout(async () => {
    try {
      const res = await previewCron(val.trim())
      previewNext.value = res.data
    } catch { previewNext.value = null }
  }, 500)
})

// ======================== tree ========================

function buildNameMap(node, map) {
  if (!node) return
  map[node.id] = node.caption || node.name
  if (node.children) {
    for (const child of node.children) {
      buildNameMap(child, map)
    }
  }
}

async function loadTree() {
  try {
    const res = await getAssetTree()
    const root = res.data
    if (!root) return
    assetTree.value = root
    const map = {}
    buildNameMap(root, map)
    assetNameMap.value = map
  } catch (e) {
    showSystarError(e, '加载资产树失败')
  }
}

function handleTreeNodeClick(data) {
  if (!dialogVisible.value) return
  const isMonitor = data.kind === 'PROBE' || data.kind === 'CONTROL'
  if (!isMonitor) return
  if (form.value.controlId != null && form.value.controlId > 0) {
    ElMessage.info('目标已填写，如需更换请先清空')
    return
  }
  form.value.controlId = data.id
}

function resolveTargetName(id) {
  if (!id) return null
  return assetNameMap.value[id] || null
}

// ======================== data loading ========================

function loadTasks() {
  loading.value = true
  const kw = searchKeyword.value || null
  listTasks(null, kw).then(res => { tasks.value = res.data || [] })
    .catch(e => showSystarError(e, '加载任务失败'))
    .finally(() => { loading.value = false })
}

function formatTime(val) {
  if (!val) return '-'
  if (Array.isArray(val)) {
    const [y, M, d, h, m, s] = val
    return `${y}-${String(M).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s || 0).padStart(2, '0')}`
  }
  return new Date(val).toLocaleString()
}

// ======================== CRUD ========================

function handleAdd() {
  isEdit.value = false
  form.value = { name: '', controlId: null, command: '', cronExpression: '0 0 8 * * ?', description: '' }
  previewNext.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  form.value = { id: row.id, name: row.name, controlId: row.controlId, command: row.command, cronExpression: row.cronExpression, description: row.description }
  previewNext.value = row.nextFireTime
  dialogVisible.value = true
}

async function submitForm() {
  const f = form.value
  if (!f.name || !f.command || !f.cronExpression || !f.controlId) {
    ElMessage.warning('请填写必填字段')
    return
  }
  try {
    if (isEdit.value) {
      await updateTask(f.id, f)
    } else {
      await createTask(f)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    loadTasks()
  } catch (e) {
    showSystarError(e, '操作失败')
  }
}

function handleToggle(row, val) {
  const api = val ? enableTask : disableTask
  api(row.id).catch(() => {
    row.enabled = !val
    ElMessage.error('操作失败')
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除任务「${row.name}」？`, '提示', { type: 'warning' })
    .then(() => deleteTask(row.id))
    .then(() => { ElMessage.success('删除成功'); loadTasks() })
    .catch(() => {})
}

function handleLogs(row) {
  logsVisible.value = true
  getTaskLogs(row.id).then(res => { logs.value = res.data || [] })
    .catch(e => showSystarError(e, '加载日志失败'))
}

// ======================== init ========================

loadTree()
loadTasks()
</script>

<style scoped>
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tree-node .node-icon {
  font-size: 14px;
}
.tree-node .node-label {
  font-size: 13px;
}
.node-state-tag {
  margin-left: 4px;
}
.tree-sidebar-manage-wrap :deep(.tree-sidebar) {
  position: relative;
  z-index: 2100;
}
</style>
