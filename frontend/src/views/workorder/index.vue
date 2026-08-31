<template>
  <div class="ops-container">
    <div class="bg-grid"></div>
    <div class="bg-scanline"></div>

    <div class="page-header">
      <h1 class="page-title">
        <span class="title-bar"></span> 工单管理
        <span class="title-sub">运维工单跟踪</span>
      </h1>
      <div class="header-line"></div>
    </div>

    <!-- Stat Cards Row -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6" v-for="(card, i) in statCards" :key="card.key">
        <div class="stat-card" :style="{ animationDelay: `${i * 0.08}s` }">
          <div class="stat-glow"></div>
          <div class="stat-body">
            <div class="stat-icon">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ animatedValues[i] }}</span>
              <span class="stat-label">{{ card.label }}</span>
            </div>
          </div>
          <div class="stat-bar" :style="{ background: card.barColor }"></div>
        </div>
      </el-col>
    </el-row>

    <!-- Tabs -->
    <div class="panel">
      <el-tabs v-model="activeTab" class="ops-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="list">
          <template #label>
            <span class="tab-label">
              <el-icon><List /></el-icon> 工单列表
            </span>
          </template>

          <!-- Filters -->
          <el-row :gutter="12" class="filter-row">
            <el-col :span="4">
              <el-select v-model="filters.status" placeholder="状态" clearable @change="loadData">
                <el-option label="已创建" value="CREATED" />
                <el-option label="已分配" value="ASSIGNED" />
                <el-option label="处理中" value="PROCESSING" />
                <el-option label="已关闭" value="CLOSED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-select v-model="filters.type" placeholder="类型" clearable @change="loadData">
                <el-option label="维修" value="REPAIR" />
                <el-option label="维护" value="MAINTENANCE" />
                <el-option label="巡检" value="INSPECTION" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-input v-model="filters.deviceId" placeholder="设备ID" clearable @keyup.enter="loadData" />
            </el-col>
            <el-col :span="4">
              <el-button class="btn-create" @click="openCreateDialog">
                <el-icon><Plus /></el-icon> 新建工单
              </el-button>
            </el-col>
            <el-col :span="8" class="toolbar-right">
              <right-toolbar v-model:showSearch="showSearch" @queryTable="loadData"></right-toolbar>
            </el-col>
          </el-row>

          <!-- Table -->
          <EnhancedTable
            :columns="workorderColumns"
            :data="tableData"
            :loading="loading"
            theme="dark"
            :exportable="true"
            export-file-name="workorders"
            :context-menu="workorderContextMenu"
            row-class-name="ops-row"
            @context-command="handleWorkorderContextCmd"
            @sort-change="handleWorkorderSort"
          >
            <template #column-orderNo="{ row }">
              <span class="mono-text">{{ row.orderNo }}</span>
            </template>
            <template #column-type="{ row }">
              <span class="type-badge" :class="'type-' + (row.type || '').toLowerCase()">{{ row.type }}</span>
            </template>
            <template #column-priority="{ row }">
              <span class="pri-badge" :class="'pri-' + row.priority">P{{ row.priority }}</span>
            </template>
            <template #column-status="{ row }">
              <span class="status-badge" :class="'st-' + (row.status || '').toLowerCase()">{{ statusLabel(row.status) }}</span>
            </template>
            <template #column-dueTime="{ row }">
              <span class="time-text">{{ fmtTime(row.dueTime) }}</span>
            </template>
            <template #column-operations="{ row }">
              <template v-if="row.status === 'CREATED'">
                <el-button text type="primary" size="small" @click="openAssign(row)">分配</el-button>
                <el-button text type="danger" size="small" @click="openCancel(row)">取消</el-button>
              </template>
              <template v-else-if="row.status === 'ASSIGNED'">
                <el-button text type="primary" size="small" @click="handleProcess(row)">处理</el-button>
                <el-button text type="danger" size="small" @click="openCancel(row)">取消</el-button>
              </template>
              <template v-else-if="row.status === 'PROCESSING'">
                <el-button text type="success" size="small" @click="openClose(row)">关闭</el-button>
              </template>
              <template v-else>
                <span class="no-action">--</span>
              </template>
            </template>
          </EnhancedTable>

          <div class="pagination-wrap">
            <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
              :page-sizes="[10,20,50]" :total="pagination.total" layout="total,sizes,prev,next"
              @size-change="loadData" @current-change="loadData" background />
          </div>
        </el-tab-pane>

        <!-- Statistics Tab -->
        <el-tab-pane name="stats">
          <template #label>
            <span class="tab-label">
              <el-icon><DataAnalysis /></el-icon> 统计概览
            </span>
          </template>
          <div class="stats-panel">
            <div class="stats-grid">
              <div class="stats-card" v-for="s in statsDetail" :key="s.label">
                <div class="stats-card-num mono-text" :style="{ color: s.color }">{{ s.value }}</div>
                <div class="stats-card-label">{{ s.label }}</div>
              </div>
            </div>
            <div ref="chartRef" class="chart-container"></div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="createVisible" title="新建工单" width="520px" class="ops-dialog" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="请输入工单标题" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="createForm.type" placeholder="请选择类型" style="width:100%">
            <el-option label="维修" value="REPAIR" />
            <el-option label="维护" value="MAINTENANCE" />
            <el-option label="巡检" value="INSPECTION" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备ID" prop="deviceId">
          <el-input-number v-model="createForm.deviceId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="createForm.priority" style="width:100%">
            <el-option label="P4 紧急" :value="4" />
            <el-option label="P3 高" :value="3" />
            <el-option label="P2 中" :value="2" />
            <el-option label="P1 低" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" class="btn-create" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- Assign Dialog -->
    <el-dialog v-model="assignVisible" title="分配工单" width="400px" class="ops-dialog">
      <el-form label-width="80px">
        <el-form-item label="负责人ID">
          <el-input-number v-model="assignForm.assigneeId" :min="1" style="width:100%" placeholder="请输入负责人ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- Close Dialog -->
    <el-dialog v-model="closeVisible" title="关闭工单" width="500px" class="ops-dialog">
      <el-form label-width="80px">
        <el-form-item label="解决方案">
          <el-input v-model="closeForm.resolution" type="textarea" :rows="4" placeholder="请输入解决方案（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="success" @click="handleClose">确认关闭</el-button>
      </template>
    </el-dialog>

    <!-- Cancel Dialog -->
    <el-dialog v-model="cancelVisible" title="取消工单" width="400px" class="ops-dialog">
      <el-form label-width="80px">
        <el-form-item label="原因">
          <el-input v-model="cancelForm.comment" placeholder="请输入取消原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" @click="handleCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, List, DataAnalysis, Document, Check, Clock, Close } from '@element-plus/icons-vue'

import EnhancedTable from '@/components/EnhancedTable/index.vue'
import * as echarts from 'echarts'
import {
  listWorkOrders, getWorkOrderStats, createWorkOrder,
  assignWorkOrder, processWorkOrder, closeWorkOrder, cancelWorkOrder
} from '@/api/iot/workorder'

// --- State ---
const activeTab = ref('list')
const showSearch = ref(true)
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive({ status: '', type: '', deviceId: '' })
const stats = reactive({ total: 0, open: 0, closed: 0, cancelled: 0 })
const animatedValues = ref([0, 0, 0, 0])

// Dialogs
const createVisible = ref(false)
const assignVisible = ref(false)
const closeVisible = ref(false)
const cancelVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ title: '', type: 'REPAIR', deviceId: 1001, priority: 2, description: '', source: 'MANUAL', creatorId: 1 })
const createRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  deviceId: [{ required: true, message: '请输入设备ID', trigger: 'blur' }],
}
const assignForm = reactive({ assigneeId: null })
const closeForm = reactive({ resolution: '' })
const cancelForm = reactive({ comment: '' })
let operatingRow = null

// Chart
const chartRef = ref(null)
let chart = null

const statCards = [
  { key: 'total', label: '工单总数', icon: Document, barColor: '#4fc3f7' },
  { key: 'open', label: '进行中', icon: Clock, barColor: '#ffab00' },
  { key: 'closed', label: '已关闭', icon: Check, barColor: '#00e676' },
  { key: 'cancelled', label: '已取消', icon: Close, barColor: '#ff5252' },
]
const statsDetail = computed(() => [
  { label: '总数', value: stats.total, color: '#4fc3f7' },
  { label: '进行中', value: stats.open, color: '#ffab00' },
  { label: '已关闭', value: stats.closed, color: '#00e676' },
  { label: '已取消', value: stats.cancelled, color: '#ff5252' },
])

const workorderColumns = [
  { prop: 'id', label: 'ID', width: 70, align: 'center', sortable: true },
  { prop: 'orderNo', label: '工单号', width: 200, slot: true },
  { prop: 'title', label: '标题', minWidth: 140, showOverflowTooltip: true },
  { prop: 'type', label: '类型', width: 90, align: 'center', slot: true,
    filterable: true, filterOptions: [
      { label: '维修', value: 'REPAIR' }, { label: '维护', value: 'MAINTENANCE' },
      { label: '巡检', value: 'INSPECTION' }, { label: '其他', value: 'OTHER' }
    ] },
  { prop: 'deviceId', label: '设备', width: 70, align: 'center' },
  { prop: 'priority', label: '优先级', width: 80, align: 'center', slot: true },
  { prop: 'status', label: '状态', width: 100, align: 'center', slot: true,
    filterable: true, filterOptions: [
      { label: '已创建', value: 'CREATED' }, { label: '已分配', value: 'ASSIGNED' },
      { label: '处理中', value: 'PROCESSING' }, { label: '已关闭', value: 'CLOSED' },
      { label: '已取消', value: 'CANCELLED' }
    ] },
  { prop: 'assigneeId', label: '负责人', width: 80, align: 'center' },
  { prop: 'dueTime', label: '截止时间', width: 170, slot: true, sortable: 'custom' },
  { prop: 'operations', label: '操作', width: 200, align: 'center', fixed: 'right', slot: true },
]

const workorderContextMenu = [
  { label: '分配', command: 'assign', visible: row => row.status === 'CREATED' },
  { label: '处理', command: 'process', visible: row => row.status === 'ASSIGNED' },
  { label: '关闭', command: 'close', visible: row => row.status === 'PROCESSING' },
  { label: '取消', command: 'cancel', visible: row => ['CREATED', 'ASSIGNED'].includes(row.status), danger: true },
]

function handleWorkorderContextCmd(command, row) {
  if (command === 'assign') openAssign(row)
  else if (command === 'process') handleProcess(row)
  else if (command === 'close') openClose(row)
  else if (command === 'cancel') openCancel(row)
}

function handleWorkorderSort({ prop, order }) {
  if (!prop || !order) return loadData()
  const sorted = [...tableData.value].sort((a, b) => {
    const va = a[prop] ?? ''
    const vb = b[prop] ?? ''
    const cmp = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb))
    return order === 'ascending' ? cmp : -cmp
  })
  tableData.value = sorted
}

function statusLabel(s) {
  const map = { CREATED: '已创建', ASSIGNED: '已分配', PROCESSING: '处理中', CLOSED: '已关闭', CANCELLED: '已取消' }
  return map[s] || s
}

function fmtTime(t) {
  if (!t) return '--'
  const s = typeof t === 'string' ? t : String(t)
  return s.replace('T', ' ').substring(0, 19)
}

function animateStats(targets) {
  const duration = 600
  const start = performance.now()
  const from = [...animatedValues.value]
  function step(now) {
    const elapsed = now - start
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    animatedValues.value = targets.map((t, i) => Math.round(from[i] + (t - from[i]) * eased))
    if (progress < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

// --- Data ---
async function loadStats() {
  try {
    const res = await getWorkOrderStats()
    if (res) {
      Object.assign(stats, res)
      animateStats([stats.total, stats.open, stats.closed, stats.cancelled])
    }
  } catch { /* stats are non-critical */ }
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (filters.status) params.status = filters.status
    if (filters.type) params.type = filters.type
    if (filters.deviceId) params.deviceId = filters.deviceId
    const res = await listWorkOrders(params)
    if (res && res.records) {
      tableData.value = res.records
      pagination.total = res.total || 0
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleTabChange(tab) {
  if (tab === 'stats') {
    await loadStats()
    nextTick(() => initChart())
  }
}

// --- CRUD ---
function openCreateDialog() {
  createForm.title = ''; createForm.type = 'REPAIR'; createForm.deviceId = null; createForm.priority = 2; createForm.description = ''
  createVisible.value = true
}

async function handleCreate() {
  await createFormRef.value?.validate()
  try {
    await createWorkOrder({ ...createForm })
    ElMessage.success('工单创建成功')
    createVisible.value = false
    loadData(); loadStats()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  }
}

function openAssign(row) { operatingRow = row; assignForm.assigneeId = null; assignVisible.value = true }
async function handleAssign() {
  try {
    await assignWorkOrder(operatingRow.id, { operatorId: 1, assigneeId: assignForm.assigneeId })
    ElMessage.success('工单已分配')
    assignVisible.value = false
    loadData(); loadStats()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function handleProcess(row) {
  try {
    await processWorkOrder(row.id, { operatorId: 1 })
    ElMessage.success('工单已转为处理中')
    loadData(); loadStats()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

function openClose(row) { operatingRow = row; closeForm.resolution = ''; closeVisible.value = true }
async function handleClose() {
  if (!closeForm.resolution.trim()) { ElMessage.warning('解决方案不能为空'); return }
  try {
    await closeWorkOrder(operatingRow.id, { resolution: closeForm.resolution, operatorId: 1 })
    ElMessage.success('工单已关闭')
    closeVisible.value = false
    loadData(); loadStats()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

function openCancel(row) { operatingRow = row; cancelForm.comment = ''; cancelVisible.value = true }
async function handleCancel() {
  if (!cancelForm.comment.trim()) { ElMessage.warning('取消原因不能为空'); return }
  try {
    await cancelWorkOrder(operatingRow.id, { comment: cancelForm.comment, operatorId: 1 })
    ElMessage.success('工单已取消')
    cancelVisible.value = false
    loadData(); loadStats()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

// --- Chart ---
function initChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value, 'dark')
  chart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(10,14,23,0.95)', borderColor: '#1e3a5f' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['总数', '进行中', '已关闭', '已取消'], axisLabel: { color: '#8892b0' }, axisLine: { lineStyle: { color: 'rgba(79,195,247,0.2)' } } },
    yAxis: { type: 'value', axisLabel: { color: '#8892b0' }, splitLine: { lineStyle: { color: 'rgba(79,195,247,0.08)' } } },
    series: [{
      type: 'bar', barWidth: '40%',
      itemStyle: { borderRadius: [6, 6, 0, 0], color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: '#4fc3f7' }, { offset: 1, color: '#0288d1' }
      ]) },
      data: tabsData()
    }]
  })
}

function tabsData() {
  return [stats.total || 0, stats.open || 0, stats.closed || 0, stats.cancelled || 0]
}

onMounted(() => {
  loadData()
  loadStats()
})

onUnmounted(() => {
  chart?.dispose()
})
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

/* Stat cards */
.stat-row { position: relative; z-index: 1; margin-bottom: 20px; }
.stat-card {
  position: relative; background: rgba(15,20,35,0.9); border: 1px solid rgba(79,195,247,0.12);
  border-radius: 10px; padding: 18px 20px; overflow: hidden;
  animation: cardIn 0.5s ease-out both;
}
@keyframes cardIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }
.stat-glow {
  position: absolute; top: -50%; right: -30%; width: 100px; height: 100px;
  background: radial-gradient(circle, rgba(79,195,247,0.15) 0%, transparent 70%);
  border-radius: 50%;
}
.stat-body { display: flex; align-items: center; gap: 14px; position: relative; z-index: 1; }
.stat-icon { color: #4fc3f7; opacity: 0.8; }
.stat-value { font-size: 28px; font-weight: 700; color: #e6f1ff; font-variant-numeric: tabular-nums; }
.stat-label { font-size: 12px; color: #546e7a; display: block; letter-spacing: 1px; text-transform: uppercase; }
.stat-bar { position: absolute; bottom: 0; left: 0; height: 2px; width: 100%; opacity: 0.3; }

/* Panel */
.panel { position: relative; z-index: 1; background: rgba(15,20,35,0.85); border: 1px solid rgba(79,195,247,0.1); border-radius: 12px; padding: 20px; }

.ops-tabs { --el-tabs-header-height: 44px; }
.tab-label { display: flex; align-items: center; gap: 6px; font-size: 14px; letter-spacing: 1px; }
.filter-row { margin-bottom: 16px; }
.toolbar-right { display: flex; justify-content: flex-end; }

.btn-create {
  background: linear-gradient(135deg, #4fc3f7, #0288d1) !important;
  border: none !important; color: #0a0e17 !important; font-weight: 600;
  letter-spacing: 1px;
}
.btn-create:hover { filter: brightness(1.1); }

/* Table */
.ops-row { animation: rowReveal 0.4s ease-out both; }
@keyframes rowReveal { from { opacity: 0; transform: translateX(-8px); } to { opacity: 1; transform: none; } }
.ops-row:nth-child(1) { animation-delay: 0.02s; } .ops-row:nth-child(2) { animation-delay: 0.04s; }
.ops-row:nth-child(3) { animation-delay: 0.06s; } .ops-row:nth-child(4) { animation-delay: 0.08s; }
.ops-row:nth-child(5) { animation-delay: 0.10s; } .ops-row:nth-child(6) { animation-delay: 0.12s; }
.ops-row:nth-child(7) { animation-delay: 0.14s; } .ops-row:nth-child(8) { animation-delay: 0.16s; }
.mono-text { font-family: 'Source Code Pro', 'Consolas', monospace; font-size: 12px; }
.time-text { font-family: 'Source Code Pro', monospace; font-size: 11px; color: #8892b0; }
.no-action { color: #546e7a; }

/* Badges */
.type-badge { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 11px; letter-spacing: 0.5px;
  background: rgba(79,195,247,0.12); color: #4fc3f7; border: 1px solid rgba(79,195,247,0.25); }
.type-repair { background: rgba(255,82,82,0.1); color: #ff5252; border-color: rgba(255,82,82,0.25); }
.type-maintenance { background: rgba(255,171,0,0.1); color: #ffab00; border-color: rgba(255,171,0,0.25); }
.type-other { background: rgba(136,146,176,0.1); color: #8892b0; border-color: rgba(136,146,176,0.25); }
.pri-badge { display: inline-block; min-width: 28px; padding: 1px 8px; border-radius: 4px; font-size: 11px; text-align: center; letter-spacing: 0.5px; }
.pri-4 { background: rgba(255,82,82,0.15); color: #ff5252; }
.pri-3 { background: rgba(255,171,0,0.15); color: #ffab00; }
.pri-2 { background: rgba(79,195,247,0.15); color: #4fc3f7; }
.pri-1 { background: rgba(136,146,176,0.15); color: #8892b0; }

.status-badge { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 11px; letter-spacing: 0.5px; }
.st-created { background: rgba(79,195,247,0.12); color: #4fc3f7; border: 1px solid rgba(79,195,247,0.25); }
.st-assigned { background: rgba(255,171,0,0.12); color: #ffab00; border: 1px solid rgba(255,171,0,0.25); }
.st-processing { background: rgba(255,171,0,0.18); color: #ffab00; border: 1px solid rgba(255,171,0,0.35); animation: pulse-border 2s infinite; }
@keyframes pulse-border { 0%,100% { border-color: rgba(255,171,0,0.35); } 50% { border-color: rgba(255,171,0,0.6); } }
.st-closed { background: rgba(0,230,118,0.12); color: #00e676; border: 1px solid rgba(0,230,118,0.25); }
.st-cancelled { background: rgba(255,82,82,0.1); color: #ff5252; border: 1px solid rgba(255,82,82,0.2); }

.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }

/* Dialogs */
:deep(.ops-dialog) {
  --el-dialog-bg-color: #141b2d;
  --el-dialog-title-font-size: 18px;
  border: 1px solid rgba(79,195,247,0.15);
  border-radius: 12px;
}
:deep(.ops-dialog .el-dialog__header) {
  border-bottom: 1px solid rgba(79,195,247,0.1);
  padding-bottom: 12px;
}
:deep(.ops-dialog .el-dialog__title) { color: #e6f1ff; letter-spacing: 1px; }

/* Stats tab */
.stats-panel { padding: 10px 0; }
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 24px; }
.stats-card { background: rgba(15,20,35,0.9); border: 1px solid rgba(79,195,247,0.1); border-radius: 10px; padding: 24px; text-align: center; }
.stats-card-num { font-size: 36px; font-weight: 700; }
.stats-card-label { font-size: 12px; color: #546e7a; letter-spacing: 1px; margin-top: 4px; text-transform: uppercase; }
.chart-container { width: 100%; height: 350px; }

/* Element Plus overrides for dark theme */
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
</style>
