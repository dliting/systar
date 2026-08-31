<template>
  <div class="ops-container">
    <div class="bg-grid"></div>
    <div class="bg-scanline"></div>

    <div class="page-header">
      <h1 class="page-title">
        <span class="title-bar"></span> 巡检管理
        <span class="title-sub">巡检计划与执行</span>
      </h1>
      <div class="header-line"></div>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab" class="ops-tabs" @tab-change="handleTabChange">
        <!-- Plans Tab -->
        <el-tab-pane name="plans">
          <template #label><span class="tab-label"><el-icon><Notebook /></el-icon> 巡检计划</span></template>

          <el-row :gutter="12" class="filter-row">
            <el-col :span="4">
              <el-select v-model="planFilters.enabled" placeholder="启用状态" clearable @change="loadPlans">
                <el-option label="已启用" :value="true" />
                <el-option label="已禁用" :value="false" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-button class="btn-create" @click="openPlanDialog()">
                <el-icon><Plus /></el-icon> 新建计划
              </el-button>
            </el-col>
            <el-col :span="8" class="toolbar-right">
              <right-toolbar v-model:showSearch="showSearch" @queryTable="loadPlans"></right-toolbar>
            </el-col>
          </el-row>

          <EnhancedTable
            :columns="planColumns"
            :data="planData"
            :loading="planLoading"
            theme="dark"
            :context-menu="planContextMenu"
            :exportable="true"
            export-file-name="inspection-plans"
            @context-command="handlePlanContextCmd"
          >
            <template #column-cronExpression="{ row }">
              <span class="mono-text">{{ row.cronExpression }}</span>
            </template>
            <template #column-enabled="{ row }">
              <span class="status-badge" :class="row.enabled === 1 ? 'st-closed' : 'st-cancelled'">
                {{ row.enabled === 1 ? '启用' : '禁用' }}
              </span>
            </template>
            <template #column-autoCreateWorkorder="{ row }">
              <span class="status-badge" :class="row.autoCreateWorkorder === 1 ? 'st-closed' : 'st-cancelled'">
                {{ row.autoCreateWorkorder === 1 ? '是' : '否' }}
              </span>
            </template>
            <template #column-operations="{ row }">
              <el-button text type="primary" size="small" @click="viewPlan(row)">详情</el-button>
              <el-button text type="primary" size="small" @click="openPlanDialog(row)">编辑</el-button>
              <el-button text type="danger" size="small" @click="handleDeletePlan(row)">删除</el-button>
            </template>
          </EnhancedTable>

          <div class="pagination-wrap">
            <el-pagination v-model:current-page="planPagination.page" v-model:page-size="planPagination.size"
              :page-sizes="[10,20,50]" :total="planPagination.total" layout="total,sizes,prev,next"
              @size-change="loadPlans" @current-change="loadPlans" background />
          </div>
        </el-tab-pane>

        <!-- Tasks Tab -->
        <el-tab-pane name="tasks">
          <template #label><span class="tab-label"><el-icon><List /></el-icon> 巡检任务</span></template>

          <el-row :gutter="12" class="filter-row">
            <el-col :span="4">
              <el-select v-model="taskFilters.status" placeholder="状态" clearable @change="loadTasks">
                <el-option label="待执行" value="PENDING" />
                <el-option label="执行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-input v-model="taskFilters.assigneeId" placeholder="负责人ID" clearable @keyup.enter="loadTasks" />
            </el-col>
            <el-col :span="8" class="toolbar-right">
              <right-toolbar v-model:showSearch="showSearch" @queryTable="loadTasks"></right-toolbar>
            </el-col>
          </el-row>

          <EnhancedTable
            :columns="inspTaskColumns"
            :data="taskData"
            :loading="taskLoading"
            theme="dark"
            :context-menu="taskContextMenu"
            :exportable="true"
            export-file-name="inspection-tasks"
            @context-command="handleTaskContextCmd"
          >
            <template #column-taskNo="{ row }">
              <span class="mono-text">{{ row.taskNo }}</span>
            </template>
            <template #column-status="{ row }">
              <span class="status-badge" :class="'st-' + (row.status || '').toLowerCase()">{{ taskStatusLabel(row.status) }}</span>
            </template>
            <template #column-scheduledTime="{ row }">
              <span class="time-text">{{ fmtTime(row.scheduledTime) }}</span>
            </template>
            <template #column-startedAt="{ row }">
              <span class="time-text">{{ fmtTime(row.startedAt) }}</span>
            </template>
            <template #column-operations="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button text type="success" size="small" @click="handleStartTask(row)">开始</el-button>
                <el-button text type="danger" size="small" @click="openCancelTask(row)">取消</el-button>
              </template>
              <template v-else-if="row.status === 'IN_PROGRESS'">
                <el-button text type="primary" size="small" @click="openResultDialog(row)">提交结果</el-button>
                <el-button text type="success" size="small" @click="handleCompleteTask(row)">完成</el-button>
                <el-button text type="danger" size="small" @click="openCancelTask(row)">取消</el-button>
              </template>
              <template v-else>
                <span class="no-action">--</span>
              </template>
            </template>
          </EnhancedTable>

          <div class="pagination-wrap">
            <el-pagination v-model:current-page="taskPagination.page" v-model:page-size="taskPagination.size"
              :page-sizes="[10,20,50]" :total="taskPagination.total" layout="total,sizes,prev,next"
              @size-change="loadTasks" @current-change="loadTasks" background />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Plan Dialog -->
    <el-dialog v-model="planVisible" :title="editingPlan ? '编辑巡检计划' : '新建巡检计划'" width="640px" class="ops-dialog" :close-on-click-modal="false">
      <el-form ref="planFormRef" :model="planForm" :rules="planRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="planForm.name" placeholder="计划名称" />
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="planForm.cronExpression" placeholder="0 0 9 ? * MON" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="planForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="默认负责人">
          <el-input-number v-model="planForm.defaultAssigneeId" :min="1" />
        </el-form-item>
        <el-form-item label="自动建工单">
          <el-switch v-model="planForm.autoCreateWorkorder" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="检查项">
          <div v-for="(item, i) in planForm.items" :key="i" class="item-row">
            <el-input v-model="item.itemName" placeholder="名称" style="width:160px" />
            <el-select v-model="item.itemType" style="width:120px">
              <el-option label="检查" value="CHECK" />
              <el-option label="测量" value="MEASUREMENT" />
              <el-option label="观察" value="OBSERVATION" />
            </el-select>
            <el-input v-model="item.expectedValue" placeholder="期望值" style="width:120px" />
            <el-button text type="danger" @click="planForm.items.splice(i,1)"><el-icon><Delete /></el-icon></el-button>
          </div>
          <el-button class="btn-outline" @click="planForm.items.push({ itemName:'', itemType:'CHECK', expectedValue:'', sortOrder: planForm.items.length+1 })">
            <el-icon><Plus /></el-icon> 添加检查项
          </el-button>
        </el-form-item>
        <el-form-item label="关联设备">
          <div v-for="(d, i) in planForm.deviceIds" :key="i" class="tag-row">
            <el-tag closable size="small" @close="planForm.deviceIds.splice(i,1)">设备 {{ d }}</el-tag>
          </div>
          <el-input-number v-model="newDeviceId" :min="1" placeholder="设备ID" size="small" style="width:120px" />
          <el-button size="small" class="btn-outline" @click="planForm.deviceIds.push(newDeviceId); newDeviceId=null">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" class="btn-create" @click="handleSavePlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- Result Dialog -->
    <el-dialog v-model="resultVisible" title="提交巡检结果" width="650px" class="ops-dialog">
      <el-table :data="resultData" size="small" class="inner-table" max-height="400">
        <el-table-column label="检查项" prop="itemName" width="160" />
        <el-table-column label="期望值" prop="expectedValue" width="120" />
        <el-table-column label="结果" prop="checkResult" width="120">
          <template #default="{ row }">
            <el-select v-model="row.checkResult" size="small" style="width:100%">
              <el-option label="正常" value="NORMAL" />
              <el-option label="异常" value="ABNORMAL" />
              <el-option label="跳过" value="SKIPPED" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="实际值" prop="actualValue" width="140">
          <template #default="{ row }"><el-input v-model="row.actualValue" size="small" /></template>
        </el-table-column>
        <el-table-column label="备注" prop="remark">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button type="primary" class="btn-create" @click="handleSubmitResults">提交</el-button>
      </template>
    </el-dialog>

    <!-- Cancel Task Dialog -->
    <el-dialog v-model="cancelTaskVisible" title="取消任务" width="400px" class="ops-dialog">
      <el-input v-model="cancelTaskRemark" placeholder="取消原因" />
      <template #footer>
        <el-button @click="cancelTaskVisible = false">取消</el-button>
        <el-button type="danger" @click="handleCancelTask">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Notebook, List, Plus, Delete } from '@element-plus/icons-vue'

import EnhancedTable from '@/components/EnhancedTable/index.vue'
import {
  listPlans, getPlan, createPlan, updatePlan, deletePlan,
  listTasks, startTask, getTask, submitResults, completeTask, cancelTask
} from '@/api/iot/inspection'

const activeTab = ref('plans')
const showSearch = ref(true)

// Plans
const planLoading = ref(false)
const planData = ref([])
const planPagination = reactive({ page: 1, size: 20, total: 0 })
const planFilters = reactive({ enabled: null })
const planVisible = ref(false)
const editingPlan = ref(null)
const planFormRef = ref()
const planForm = reactive({
  name: '', cronExpression: '0 0 9 ? * MON', enabled: 1, defaultAssigneeId: 1,
  autoCreateWorkorder: 0, creatorId: 1, items: [], deviceIds: []
})
const planRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }]
}
const newDeviceId = ref(null)

const planColumns = [
  { prop: 'id', label: 'ID', width: 60, align: 'center', sortable: true },
  { prop: 'name', label: '名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'cronExpression', label: 'Cron', width: 160, slot: true },
  { prop: 'enabled', label: '状态', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }] },
  { prop: 'defaultAssigneeId', label: '默认负责人', width: 100, align: 'center' },
  { prop: 'autoCreateWorkorder', label: '自动建工单', width: 110, align: 'center', slot: true },
  { prop: 'operations', label: '操作', width: 200, align: 'center', fixed: 'right', slot: true },
]

const inspTaskColumns = [
  { prop: 'id', label: 'ID', width: 60, align: 'center', sortable: true },
  { prop: 'taskNo', label: '任务编号', width: 180, slot: true },
  { prop: 'planId', label: '计划ID', width: 80, align: 'center' },
  { prop: 'status', label: '状态', width: 90, align: 'center', slot: true,
    filterable: true, filterOptions: [
      { label: '待执行', value: 'PENDING' }, { label: '执行中', value: 'IN_PROGRESS' },
      { label: '已完成', value: 'COMPLETED' }, { label: '已取消', value: 'CANCELLED' }
    ] },
  { prop: 'assigneeId', label: '负责人', width: 80, align: 'center' },
  { prop: 'scheduledTime', label: '计划时间', width: 170, slot: true },
  { prop: 'startedAt', label: '开始时间', width: 170, slot: true },
  { prop: 'operations', label: '操作', width: 240, align: 'center', fixed: 'right', slot: true },
]

const planContextMenu = [
  { label: '编辑', command: 'edit' },
  { label: '删除', command: 'delete', danger: true },
]

const taskContextMenu = [
  { label: '开始', command: 'start', visible: row => row.status === 'PENDING' },
  { label: '提交结果', command: 'submit', visible: row => row.status === 'IN_PROGRESS' },
  { label: '完成', command: 'complete', visible: row => row.status === 'IN_PROGRESS' },
  { label: '取消', command: 'cancel', visible: row => ['PENDING', 'IN_PROGRESS'].includes(row.status), danger: true },
]

function handlePlanContextCmd(command, row) {
  if (command === 'edit') openPlanDialog(row)
  else if (command === 'delete') handleDeletePlan(row)
}

function handleTaskContextCmd(command, row) {
  if (command === 'start') handleStartTask(row)
  else if (command === 'submit') openResultDialog(row)
  else if (command === 'complete') handleCompleteTask(row)
  else if (command === 'cancel') openCancelTask(row)
}

// Tasks
const taskLoading = ref(false)
const taskData = ref([])
const taskPagination = reactive({ page: 1, size: 20, total: 0 })
const taskFilters = reactive({ status: '', assigneeId: '' })
const resultVisible = ref(false)
const resultData = ref([])
const cancelTaskVisible = ref(false)
const cancelTaskRemark = ref('')
let selectedTaskId = null

function taskStatusLabel(s) {
  const map = { PENDING: '待执行', IN_PROGRESS: '执行中', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[s] || s
}
function fmtTime(t) { if (!t) return '--'; const s = typeof t === 'string' ? t : String(t); return s.replace('T', ' ').substring(0, 19) }

// --- Plans ---
async function loadPlans() {
  planLoading.value = true
  try {
    const params = { page: planPagination.page, size: planPagination.size }
    if (planFilters.enabled !== null && planFilters.enabled !== '') params.enabled = planFilters.enabled
    const res = await listPlans(params)
    if (res && res.records) { planData.value = res.records; planPagination.total = res.total || 0 }
  } catch (e) { ElMessage.error(e.message || '加载失败') }
  finally { planLoading.value = false }
}

function openPlanDialog(row) {
  if (row) {
    editingPlan.value = row
    planForm.name = row.name; planForm.cronExpression = row.cronExpression
    planForm.enabled = row.enabled; planForm.defaultAssigneeId = row.defaultAssigneeId
    planForm.autoCreateWorkorder = row.autoCreateWorkorder; planForm.creatorId = row.creatorId
    loadPlanDetails(row.id)
  } else {
    editingPlan.value = null
    planForm.name = ''; planForm.cronExpression = '0 0 9 ? * MON'
    planForm.enabled = 1; planForm.defaultAssigneeId = 1
    planForm.autoCreateWorkorder = 0; planForm.creatorId = 1
    planForm.items = []; planForm.deviceIds = []
  }
  planVisible.value = true
}

async function loadPlanDetails(id) {
  try {
    const detail = await getPlan(id)
    if (detail) {
      planForm.items = (detail.items || []).map(i => ({ id: i.id, itemName: i.itemName, itemType: i.itemType, expectedValue: i.expectedValue, sortOrder: i.sortOrder }))
      planForm.deviceIds = (detail.devices || []).map(d => d.deviceId)
    }
  } catch {}
}

async function handleSavePlan() {
  await planFormRef.value?.validate()
  try {
    const payload = { plan: { ...planForm, items: undefined, deviceIds: undefined }, deviceIds: planForm.deviceIds, items: planForm.items }
    if (editingPlan.value) {
      await updatePlan(editingPlan.value.id, payload)
      ElMessage.success('计划已更新')
    } else {
      await createPlan(payload)
      ElMessage.success('计划已创建')
    }
    planVisible.value = false
    loadPlans()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

function viewPlan(row) { openPlanDialog(row) }

async function handleDeletePlan(row) {
  try {
    await ElMessageBox.confirm(`确定要删除计划 "${row.name}" 吗？`, '确认删除', { type: 'warning' })
    await deletePlan(row.id)
    ElMessage.success('计划已删除')
    loadPlans()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '删除失败') }
}

// --- Tasks ---
async function loadTasks() {
  taskLoading.value = true
  try {
    const params = { page: taskPagination.page, size: taskPagination.size }
    if (taskFilters.status) params.status = taskFilters.status
    if (taskFilters.assigneeId) params.assigneeId = taskFilters.assigneeId
    const res = await listTasks(params)
    if (res && res.records) { taskData.value = res.records; taskPagination.total = res.total || 0 }
  } catch (e) { ElMessage.error(e.message || '加载失败') }
  finally { taskLoading.value = false }
}

async function handleStartTask(row) {
  try { await startTask(row.id); ElMessage.success('任务已开始'); loadTasks() }
  catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function openResultDialog(row) {
  selectedTaskId = row.id
  try {
    const taskDetail = await getTask(row.id)
    resultData.value = (taskDetail?.results || []).map(r => ({ ...r }))
  } catch { resultData.value = [] }
  resultVisible.value = true
}

async function handleSubmitResults() {
  try {
    await submitResults(selectedTaskId, resultData.value)
    ElMessage.success('结果已提交')
    resultVisible.value = false
    loadTasks()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function handleCompleteTask(row) {
  try {
    await completeTask(row.id, '')
    ElMessage.success('任务已完成')
    loadTasks()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

function openCancelTask(row) { selectedTaskId = row.id; cancelTaskRemark.value = ''; cancelTaskVisible.value = true }

async function handleCancelTask() {
  try {
    await cancelTask(selectedTaskId, cancelTaskRemark.value)
    ElMessage.success('任务已取消')
    cancelTaskVisible.value = false
    loadTasks()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

function handleTabChange(tab) {
  if (tab === 'plans') loadPlans()
  else if (tab === 'tasks') loadTasks()
}

onMounted(() => { loadPlans() })
</script>

<style scoped>
.ops-container {
  position: relative; margin: -20px; min-height: calc(100vh - 56px); padding: 24px;
  background: #0a0e17; overflow: hidden;
  font-family: 'Courier New', 'Source Code Pro', 'Consolas', monospace;
}
.bg-grid {
  position: absolute; inset: 0; pointer-events: none;
  background-image: linear-gradient(rgba(79,195,247,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79,195,247,0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}
.bg-scanline {
  position: absolute; inset: 0; pointer-events: none;
  background: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(79,195,247,0.008) 2px, rgba(79,195,247,0.008) 4px);
}
.page-header { position: relative; z-index: 1; margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 600; color: #e6f1ff; letter-spacing: 2px; margin: 0; }
.title-bar { display: inline-block; width: 4px; height: 22px; background: linear-gradient(180deg, #00f0ff, #0080ff); border-radius: 2px; vertical-align: middle; margin-right: 10px; }
.title-sub { font-size: 12px; color: #546e7a; margin-left: 12px; letter-spacing: 1px; }
.header-line { margin-top: 8px; height: 1px; background: linear-gradient(90deg, #4fc3f7 0%, transparent 100%); }

.panel { position: relative; z-index: 1; background: rgba(15,20,35,0.85); border: 1px solid rgba(79,195,247,0.1); border-radius: 12px; padding: 20px; }

.ops-tabs { --el-tabs-header-height: 44px; }
.tab-label { display: flex; align-items: center; gap: 6px; font-size: 14px; letter-spacing: 1px; }
.filter-row { margin-bottom: 16px; }
.toolbar-right { display: flex; justify-content: flex-end; }

.btn-create { background: linear-gradient(135deg, #4fc3f7, #0288d1) !important; border: none !important; color: #0a0e17 !important; font-weight: 600; letter-spacing: 1px; }
.btn-create:hover { filter: brightness(1.1); }
.btn-outline { border: 1px solid rgba(79,195,247,0.3) !important; color: #4fc3f7 !important; background: transparent !important; }

.mono-text { font-family: 'Source Code Pro', 'Consolas', monospace; font-size: 12px; }
.time-text { font-family: 'Source Code Pro', monospace; font-size: 11px; color: #8892b0; }
.no-action { color: #546e7a; }

.status-badge { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 11px; letter-spacing: 0.5px; }
.st-created, .st-pending { background: rgba(79,195,247,0.12); color: #4fc3f7; border: 1px solid rgba(79,195,247,0.25); }
.st-assigned { background: rgba(79,195,247,0.18); color: #4fc3f7; border: 1px solid rgba(79,195,247,0.35); }
.st-processing, .st-in-progress { background: rgba(255,171,0,0.18); color: #ffab00; border: 1px solid rgba(255,171,0,0.35); animation: pulse-border 2s infinite; }
@keyframes pulse-border { 0%,100% { border-color: rgba(255,171,0,0.35); } 50% { border-color: rgba(255,171,0,0.6); } }
.st-closed, .st-completed { background: rgba(0,230,118,0.12); color: #00e676; border: 1px solid rgba(0,230,118,0.25); }
.st-cancelled { background: rgba(255,82,82,0.1); color: #ff5252; border: 1px solid rgba(255,82,82,0.2); }

.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }

.item-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }

:deep(.ops-dialog) { --el-dialog-bg-color: #141b2d; border: 1px solid rgba(79,195,247,0.15); border-radius: 12px; }
:deep(.ops-dialog .el-dialog__header) { border-bottom: 1px solid rgba(79,195,247,0.1); padding-bottom: 12px; }
:deep(.ops-dialog .el-dialog__title) { color: #e6f1ff; letter-spacing: 1px; }
:deep(.el-input__wrapper) { background: rgba(10,14,23,0.6) !important; box-shadow: 0 0 0 1px rgba(79,195,247,0.15) !important; }
:deep(.el-input__inner) { color: #ccd6f6 !important; }
:deep(.el-select .el-input__wrapper) { background: rgba(10,14,23,0.6) !important; }
:deep(.el-pagination) { --el-pagination-bg-color: transparent; --el-pagination-text-color: #8892b0; }
:deep(.el-pager li.is-active) { background: #4fc3f7 !important; color: #0a0e17 !important; }
:deep(.el-tabs__nav-wrap::after) { background-color: rgba(79,195,247,0.08) !important; }
:deep(.el-tabs__item) { color: #546e7a !important; letter-spacing: 1px; }
:deep(.el-tabs__item.is-active) { color: #4fc3f7 !important; }
:deep(.el-button--text) { color: #4fc3f7; }
:deep(.el-button--text.el-button--danger) { color: #ff5252; }
:deep(.el-button--text.el-button--success) { color: #00e676; }
.inner-table { --el-table-bg-color: transparent; --el-table-header-bg-color: rgba(10,14,23,0.4); }
</style>
