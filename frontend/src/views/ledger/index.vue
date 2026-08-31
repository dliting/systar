<template>
  <div class="ops-container">
    <div class="bg-grid"></div>
    <div class="bg-scanline"></div>

    <div class="page-header">
      <h1 class="page-title">
        <span class="title-bar"></span> 设备台账
        <span class="title-sub">资产全生命周期管理</span>
      </h1>
      <div class="header-line"></div>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6" v-for="(card, i) in statCards" :key="card.key">
        <div class="stat-card" :style="{ animationDelay: `${i * 0.08}s` }">
          <div class="stat-glow"></div>
          <div class="stat-body">
            <div class="stat-icon"><el-icon :size="24"><component :is="card.icon" /></el-icon></div>
            <div class="stat-info">
              <span class="stat-value">{{ animatedValues[i] }}</span>
              <span class="stat-label">{{ card.label }}</span>
            </div>
          </div>
          <div class="stat-bar" :style="{ background: card.barColor }"></div>
        </div>
      </el-col>
    </el-row>

    <div class="panel">
      <el-tabs v-model="activeTab" class="ops-tabs" @tab-change="handleTabChange">
        <!-- Device List Tab -->
        <el-tab-pane name="list">
          <template #label><span class="tab-label"><el-icon><Monitor /></el-icon> 设备列表</span></template>

          <el-row :gutter="12" class="filter-row">
            <el-col :span="4">
              <el-select v-model="filters.catalog" placeholder="设备类型" clearable @change="loadData">
                <el-option label="传感器" :value="1" />
                <el-option label="控制器" :value="2" />
                <el-option label="网关" :value="3" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-select v-model="filters.lifecycleStatus" placeholder="生命周期" clearable @change="loadData">
                <el-option label="服务中" value="IN_SERVICE" />
                <el-option label="维修中" value="UNDER_REPAIR" />
                <el-option label="已退役" value="RETIRED" />
              </el-select>
            </el-col>
            <el-col :span="8" class="toolbar-right">
              <right-toolbar v-model:showSearch="showSearch" @queryTable="loadData"></right-toolbar>
            </el-col>
          </el-row>

          <EnhancedTable
            :columns="ledgerColumns"
            :data="tableData"
            :loading="loading"
            theme="dark"
            :context-menu="ledgerContextMenu"
            :exportable="true"
            export-file-name="devices"
            @context-command="handleLedgerContextCmd"
            @expand-change="handleExpand"
          >
            <template #expanded-row="{ row }">
              <div class="expand-content">
                <div class="expand-section">
                  <h4 class="expand-title">扩展属性</h4>
                  <el-table :data="expandAttrs[row.id] || []" size="small" class="inner-table">
                    <el-table-column label="Key" prop="attrKey" width="180" />
                    <el-table-column label="Value" prop="attrValue" />
                    <el-table-column label="Type" prop="attrType" width="100" />
                  </el-table>
                </div>
                <div class="expand-section">
                  <h4 class="expand-title">最近维护记录</h4>
                  <el-timeline v-if="(expandRecords[row.id] || []).length">
                    <el-timeline-item v-for="r in expandRecords[row.id]" :key="r.id"
                      :timestamp="fmtTime(r.performedAt)" placement="top" :color="r.type === 'REPAIR' ? '#ff5252' : '#4fc3f7'">
                      <span class="timeline-text">{{ r.title }}</span>
                    </el-timeline-item>
                  </el-timeline>
                  <span v-else class="empty-text">暂无维护记录</span>
                </div>
              </div>
            </template>
            <template #column-model="{ row }">
              <span class="mono-text">{{ row.model || '--' }}</span>
            </template>
            <template #column-serialNumber="{ row }">
              <span class="mono-text">{{ row.serialNumber || '--' }}</span>
            </template>
            <template #column-lifecycleStatus="{ row }">
              <span class="status-badge" :class="'st-' + (row.lifecycleStatus || '').toLowerCase().replace('_','-')">
                {{ lifecycleLabel(row.lifecycleStatus) }}
              </span>
            </template>
            <template #column-lastMaintenanceDate="{ row }">
              <span class="time-text">{{ row.lastMaintenanceDate || '--' }}</span>
            </template>
            <template #column-operations="{ row }">
              <el-button text type="primary" size="small" @click="openAttrDialog(row)">属性</el-button>
              <el-button text type="primary" size="small" @click="openMaintenanceDialog(row)">维护</el-button>
            </template>
          </EnhancedTable>

          <div class="pagination-wrap">
            <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
              :page-sizes="[10,20,50]" :total="pagination.total" layout="total,sizes,prev,next"
              @size-change="loadData" @current-change="loadData" background />
          </div>
        </el-tab-pane>

        <!-- Stats Tab -->
        <el-tab-pane name="stats">
          <template #label><span class="tab-label"><el-icon><DataAnalysis /></el-icon> 统计概览</span></template>
          <div class="stats-panel">
            <div class="stats-grid">
              <div class="stats-card" v-for="s in statsDetail" :key="s.label">
                <div class="stats-card-num mono-text" :style="{ color: s.color }">{{ s.value }}</div>
                <div class="stats-card-label">{{ s.label }}</div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Attribute Dialog -->
    <el-dialog v-model="attrVisible" title="设置扩展属性" width="550px" class="ops-dialog">
      <div v-for="(item, i) in attrForm" :key="i" class="attr-row">
        <el-input v-model="item.attrKey" placeholder="Key" style="width:160px" />
        <el-input v-model="item.attrValue" placeholder="Value" style="width:200px" />
        <el-select v-model="item.attrType" style="width:110px">
          <el-option label="string" value="string" />
          <el-option label="number" value="number" />
          <el-option label="boolean" value="boolean" />
        </el-select>
        <el-button text type="danger" @click="attrForm.splice(i,1)"><el-icon><Delete /></el-icon></el-button>
      </div>
      <el-button class="btn-outline" @click="attrForm.push({ attrKey:'', attrValue:'', attrType:'string' })">
        <el-icon><Plus /></el-icon> 添加属性
      </el-button>
      <template #footer>
        <el-button @click="attrVisible = false">取消</el-button>
        <el-button type="primary" class="btn-create" @click="handleSaveAttr">保存</el-button>
      </template>
    </el-dialog>

    <!-- Maintenance Dialog -->
    <el-dialog v-model="maintenanceVisible" title="创建维护记录" width="480px" class="ops-dialog">
      <el-form label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="maintenanceForm.type" style="width:100%">
            <el-option label="维修" value="REPAIR" />
            <el-option label="维护" value="MAINTENANCE" />
            <el-option label="巡检" value="INSPECTION" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="maintenanceForm.title" placeholder="维护标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="maintenanceForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="执行人ID">
          <el-input-number v-model="maintenanceForm.performerId" :min="1" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="maintenanceVisible = false">取消</el-button>
        <el-button type="primary" class="btn-create" @click="handleCreateMaintenance">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, DataAnalysis, Plus, Delete, Cpu, Setting, Check, Close } from '@element-plus/icons-vue'

import EnhancedTable from '@/components/EnhancedTable/index.vue'
import {
  listDevices, getDeviceStats, getDeviceAttributes,
  setDeviceAttributes, getDeviceDetail, createMaintenanceRecord, listMaintenanceRecords
} from '@/api/iot/ledger'

const activeTab = ref('list')
const showSearch = ref(true)
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive({ catalog: '', lifecycleStatus: '' })
const stats = reactive({ total: 0, inService: 0, underRepair: 0, retired: 0 })
const animatedValues = ref([0, 0, 0, 0])

const attrVisible = ref(false)
const maintenanceVisible = ref(false)
const attrForm = ref([])
const expandAttrs = reactive({})
const expandRecords = reactive({})
let selectedDeviceId = null

const maintenanceForm = reactive({ type: 'MAINTENANCE', title: '', description: '', performerId: 1, creatorId: 1 })

const ledgerColumns = [
  { type: 'expand' },
  { prop: 'id', label: 'ID', width: 70, align: 'center', sortable: true },
  { prop: 'name', label: '名称', minWidth: 120, showOverflowTooltip: true },
  { prop: 'model', label: '型号', width: 120, slot: true },
  { prop: 'serialNumber', label: '序列号', width: 140, slot: true },
  { prop: 'lifecycleStatus', label: '状态', width: 100, align: 'center', slot: true,
    filterable: true, filterOptions: [
      { label: '服务中', value: 'IN_SERVICE' }, { label: '维修中', value: 'UNDER_REPAIR' },
      { label: '已退役', value: 'RETIRED' }
    ] },
  { prop: 'department', label: '部门', width: 100 },
  { prop: 'responsiblePerson', label: '责任人', width: 100 },
  { prop: 'lastMaintenanceDate', label: '上次维护', width: 120, slot: true },
  { prop: 'operations', label: '操作', width: 180, align: 'center', fixed: 'right', slot: true },
]

const ledgerContextMenu = [
  { label: '属性', command: 'attr' },
  { label: '维护', command: 'maintenance' },
]

function handleLedgerContextCmd(command, row) {
  if (command === 'attr') openAttrDialog(row)
  else if (command === 'maintenance') openMaintenanceDialog(row)
}

const statCards = [
  { key: 'total', label: '设备总数', icon: Cpu, barColor: '#4fc3f7' },
  { key: 'inService', label: '服务中', icon: Monitor, barColor: '#00e676' },
  { key: 'underRepair', label: '维修中', icon: Setting, barColor: '#ffab00' },
  { key: 'retired', label: '已退役', icon: Close, barColor: '#ff5252' },
]
const statsDetail = computed(() => [
  { label: '总数', value: stats.total, color: '#4fc3f7' },
  { label: '服务中', value: stats.inService, color: '#00e676' },
  { label: '维修中', value: stats.underRepair, color: '#ffab00' },
  { label: '已退役', value: stats.retired, color: '#ff5252' },
])

function lifecycleLabel(s) {
  const map = { IN_SERVICE: '服务中', UNDER_REPAIR: '维修中', RETIRED: '已退役' }
  return map[s] || s || '--'
}
function fmtTime(t) { if (!t) return '--'; const s = typeof t === 'string' ? t : String(t); return s.replace('T', ' ').substring(0, 19) }

function animateStats(targets) {
  const duration = 600; const start = performance.now(); const from = [...animatedValues.value]
  function step(now) {
    const elapsed = now - start; const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    animatedValues.value = targets.map((t, i) => Math.round(from[i] + (t - from[i]) * eased))
    if (progress < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

async function loadStats() {
  try {
    const res = await getDeviceStats()
    if (res) { Object.assign(stats, res); animateStats([stats.total, stats.inService, stats.underRepair, stats.retired]) }
  } catch {}
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (filters.catalog) params.catalog = filters.catalog
    if (filters.lifecycleStatus) params.lifecycleStatus = filters.lifecycleStatus
    const res = await listDevices(params)
    if (res && res.records) { tableData.value = res.records; pagination.total = res.total || 0 }
  } catch (e) { ElMessage.error(e.message || '加载失败') }
  finally { loading.value = false }
}

async function handleExpand(row, expandedRows) {
  if (!expandedRows.includes(row)) return
  try {
    const attrs = await getDeviceAttributes(row.id)
    expandAttrs[row.id] = attrs || []
  } catch {}
  try {
    const records = await listMaintenanceRecords(row.id, { page: 1, size: 5 })
    expandRecords[row.id] = records?.records || []
  } catch {}
}

function handleTabChange(tab) { if (tab === 'stats') loadStats() }

function openAttrDialog(row) {
  selectedDeviceId = row.id
  attrForm.value = (expandAttrs[row.id] || []).map(a => ({ attrKey: a.attrKey, attrValue: a.attrValue, attrType: a.attrType || 'string' }))
  if (!attrForm.value.length) attrForm.value.push({ attrKey: '', attrValue: '', attrType: 'string' })
  attrVisible.value = true
}

async function handleSaveAttr() {
  try {
    await setDeviceAttributes(selectedDeviceId, attrForm.value.filter(a => a.attrKey))
    ElMessage.success('属性已保存')
    attrVisible.value = false
    handleExpand({ id: selectedDeviceId }, [{ id: selectedDeviceId }])
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

function openMaintenanceDialog(row) {
  selectedDeviceId = row.id
  maintenanceForm.type = 'MAINTENANCE'; maintenanceForm.title = ''; maintenanceForm.description = ''; maintenanceForm.performerId = null
  maintenanceVisible.value = true
}

async function handleCreateMaintenance() {
  try {
    await createMaintenanceRecord(selectedDeviceId, { ...maintenanceForm })
    ElMessage.success('维护记录已创建')
    maintenanceVisible.value = false
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

onMounted(() => { loadData(); loadStats() })
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

.stat-row { position: relative; z-index: 1; margin-bottom: 20px; }
.stat-card {
  position: relative; background: rgba(15,20,35,0.9); border: 1px solid rgba(79,195,247,0.12);
  border-radius: 10px; padding: 18px 20px; overflow: hidden;
  animation: cardIn 0.5s ease-out both;
}
@keyframes cardIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }
.stat-glow { position: absolute; top: -50%; right: -30%; width: 100px; height: 100px; background: radial-gradient(circle, rgba(79,195,247,0.15) 0%, transparent 70%); border-radius: 50%; }
.stat-body { display: flex; align-items: center; gap: 14px; position: relative; z-index: 1; }
.stat-icon { color: #4fc3f7; opacity: 0.8; }
.stat-value { font-size: 28px; font-weight: 700; color: #e6f1ff; font-variant-numeric: tabular-nums; }
.stat-label { font-size: 12px; color: #546e7a; display: block; letter-spacing: 1px; text-transform: uppercase; }
.stat-bar { position: absolute; bottom: 0; left: 0; height: 2px; width: 100%; opacity: 0.3; }

.panel { position: relative; z-index: 1; background: rgba(15,20,35,0.85); border: 1px solid rgba(79,195,247,0.1); border-radius: 12px; padding: 20px; }

.ops-tabs { --el-tabs-header-height: 44px; }
.tab-label { display: flex; align-items: center; gap: 6px; font-size: 14px; letter-spacing: 1px; }
.filter-row { margin-bottom: 16px; }
.toolbar-right { display: flex; justify-content: flex-end; }

.btn-create { background: linear-gradient(135deg, #4fc3f7, #0288d1) !important; border: none !important; color: #0a0e17 !important; font-weight: 600; letter-spacing: 1px; }
.btn-create:hover { filter: brightness(1.1); }
.btn-outline { border: 1px solid rgba(79,195,247,0.3) !important; color: #4fc3f7 !important; background: transparent !important; margin-top: 8px; }

.mono-text { font-family: 'Source Code Pro', 'Consolas', monospace; font-size: 12px; }
.time-text { font-family: 'Source Code Pro', monospace; font-size: 11px; color: #8892b0; }
.empty-text { color: #546e7a; font-size: 13px; }

.status-badge { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 11px; letter-spacing: 0.5px; }
.st-in-service { background: rgba(0,230,118,0.12); color: #00e676; border: 1px solid rgba(0,230,118,0.25); }
.st-under-repair { background: rgba(255,171,0,0.18); color: #ffab00; border: 1px solid rgba(255,171,0,0.35); }
.st-retired { background: rgba(136,146,176,0.12); color: #8892b0; border: 1px solid rgba(136,146,176,0.25); }

.expand-content { padding: 16px 40px; background: rgba(10,14,23,0.4); }
.expand-section { margin-bottom: 16px; }
.expand-title { color: #4fc3f7; font-size: 13px; letter-spacing: 1px; margin: 0 0 10px 0; text-transform: uppercase; }
.inner-table { --el-table-bg-color: transparent; --el-table-header-bg-color: rgba(10,14,23,0.4); }
.timeline-text { color: #ccd6f6; font-size: 13px; }

.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }

.stats-panel { padding: 10px 0; }
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
.stats-card { background: rgba(15,20,35,0.9); border: 1px solid rgba(79,195,247,0.1); border-radius: 10px; padding: 24px; text-align: center; }
.stats-card-num { font-size: 36px; font-weight: 700; }
.stats-card-label { font-size: 12px; color: #546e7a; letter-spacing: 1px; margin-top: 4px; text-transform: uppercase; }

.attr-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }

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
</style>
