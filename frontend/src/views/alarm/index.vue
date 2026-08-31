<template>
  <div class="app-container">
    <Breadcrumb />
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 告警规则 -->
      <el-tab-pane label="告警规则" name="rules">
        <el-row :gutter="10" class="mb8">
          <right-toolbar v-model:showSearch="showSearch" @queryTable="loadRules"></right-toolbar>
        </el-row>

        <EnhancedTable
          :columns="rulesColumns"
          :data="rules"
          :loading="rulesLoading"
          :exportable="true"
          export-file-name="alarm-rules"
        >
          <template #column-strategy="{ row }">
            <el-tag size="small">{{ strategyLabel(row.strategy) }}</el-tag>
          </template>
          <template #column-way="{ row }">
            <span>{{ wayLabel(row.way) }}</span>
          </template>
          <template #column-start="{ row }">
            <span>第 {{ row.start || 1 }} 次</span>
          </template>
          <template #column-enabled="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </EnhancedTable>
      </el-tab-pane>

      <!-- 告警消息 -->
      <el-tab-pane label="告警消息" name="messages">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-select v-model="messageFilter.state" placeholder="处理状态" clearable style="width: 120px" @change="handleMessageQuery">
              <el-option label="待处理" :value="1" />
              <el-option label="已处理" :value="2" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-select v-model="messageFilter.recovered" placeholder="恢复状态" clearable style="width: 120px" @change="handleMessageQuery">
              <el-option label="未恢复" :value="0" />
              <el-option label="已恢复" :value="1" />
            </el-select>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="loadMessages"></right-toolbar>
        </el-row>

        <EnhancedTable
          :columns="messagesColumns"
          :data="messages"
          :loading="messagesLoading"
          :row-class-name="messageRowClassName"
          :exportable="true"
          export-file-name="alarm-messages"
          @sort-change="handleMessageSort"
        >
          <template #column-state="{ row }">
            <el-tag :type="row.state === 1 ? 'warning' : 'success'" size="small">
              {{ row.state === 1 ? '待处理' : '已处理' }}
            </el-tag>
          </template>
          <template #column-escalationLevel="{ row }">
            <span>{{ row.escalationLevel || 0 }}</span>
          </template>
          <template #column-recovered="{ row }">
            <el-tag :type="row.recovered === 1 ? 'success' : 'danger'" size="small">
              {{ row.recovered === 1 ? '已恢复' : '未恢复' }}
            </el-tag>
          </template>
          <template #column-alarmTime="{ row }">
            <span>{{ parseTime(row.alarmTime) }}</span>
          </template>
        </EnhancedTable>

        <pagination
          v-show="messageTotal > 0"
          :total="messageTotal"
          v-model:page="messageParams.page"
          v-model:limit="messageParams.size"
          @pagination="loadMessages"
        />
      </el-tab-pane>

      <!-- 关联规则 -->
      <el-tab-pane label="关联规则" name="correlation">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain @click="handleAddCorrelation">新增</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="loadCorrelationRules"></right-toolbar>
        </el-row>

        <EnhancedTable
          :columns="corrColumns"
          :data="corrRules"
          :loading="corrLoading"
          :context-menu="corrContextMenu"
          :exportable="true"
          export-file-name="alarm-correlation"
          @context-command="handleCorrContextCmd"
        >
          <template #column-deviceId="{ row }">
            <span>{{ row.deviceId || '全局' }}</span>
          </template>
          <template #column-enabled="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
          <template #column-operations="{ row }">
            <el-button link type="primary" @click="handleEditCorrelation(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteCorrelation(row)">删除</el-button>
          </template>
        </EnhancedTable>

        <pagination
          v-show="corrTotal > 0"
          :total="corrTotal"
          v-model:page="corrParams.page"
          v-model:limit="corrParams.size"
          @pagination="loadCorrelationRules"
        />
      </el-tab-pane>

      <!-- 升级策略 -->
      <el-tab-pane label="升级策略" name="escalation">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain @click="handleAddEscalation">新增</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="loadEscalationPolicies"></right-toolbar>
        </el-row>

        <EnhancedTable
          :columns="escColumns"
          :data="escPolicies"
          :loading="escLoading"
          :context-menu="escContextMenu"
          :exportable="true"
          export-file-name="alarm-escalation"
          @context-command="handleEscContextCmd"
        >
          <template #column-enabled="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
          <template #column-operations="{ row }">
            <el-button link type="primary" @click="handleEditEscalation(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteEscalation(row)">删除</el-button>
          </template>
        </EnhancedTable>

        <pagination
          v-show="escTotal > 0"
          :total="escTotal"
          v-model:page="escParams.page"
          v-model:limit="escParams.size"
          @pagination="loadEscalationPolicies"
        />
      </el-tab-pane>

      <!-- 静默窗口 -->
      <el-tab-pane label="静默窗口" name="silence">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain @click="handleAddSilence">新增</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="loadSilenceWindows"></right-toolbar>
        </el-row>

        <EnhancedTable
          :columns="silenceColumns"
          :data="silenceWindows"
          :loading="silenceLoading"
          :context-menu="silenceContextMenu"
          :exportable="true"
          export-file-name="alarm-silence"
          @context-command="handleSilenceContextCmd"
        >
          <template #column-deviceId="{ row }">
            <span>{{ row.deviceId || '全部' }}</span>
          </template>
          <template #column-monitorId="{ row }">
            <span>{{ row.monitorId || '全部' }}</span>
          </template>
          <template #column-startTime="{ row }">
            <span>{{ parseTime(row.startTime) }}</span>
          </template>
          <template #column-endTime="{ row }">
            <span>{{ parseTime(row.endTime) }}</span>
          </template>
          <template #column-enabled="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
          <template #column-operations="{ row }">
            <el-button link type="primary" @click="handleEditSilence(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteSilence(row)">删除</el-button>
          </template>
        </EnhancedTable>

        <pagination
          v-show="silenceTotal > 0"
          :total="silenceTotal"
          v-model:page="silenceParams.page"
          v-model:limit="silenceParams.size"
          @pagination="loadSilenceWindows"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 关联规则编辑对话框 -->
    <el-dialog v-model="corrDialogVisible" :title="corrDialogTitle" width="500px">
      <el-form ref="corrFormRef" :model="corrForm" :rules="corrRules_form" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="corrForm.name" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="设备ID" prop="deviceId">
          <el-input v-model.number="corrForm.deviceId" placeholder="留空表示全局规则" />
        </el-form-item>
        <el-form-item label="时间窗口(秒)" prop="windowSeconds">
          <el-input-number v-model="corrForm.windowSeconds" :min="1" :max="86400" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="corrForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="corrDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCorrelation">确定</el-button>
      </template>
    </el-dialog>

    <!-- 升级策略编辑对话框 -->
    <el-dialog v-model="escDialogVisible" :title="escDialogTitle" width="500px">
      <el-form ref="escFormRef" :model="escForm" :rules="escRules_form" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="escForm.name" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="源级别" prop="fromLevel">
          <el-input-number v-model="escForm.fromLevel" :min="1" />
        </el-form-item>
        <el-form-item label="目标级别" prop="toLevel">
          <el-input-number v-model="escForm.toLevel" :min="1" />
        </el-form-item>
        <el-form-item label="超时(分钟)" prop="timeoutMinutes">
          <el-input-number v-model="escForm.timeoutMinutes" :min="1" />
        </el-form-item>
        <el-form-item label="通知方式" prop="notifyType">
          <el-select v-model="escForm.notifyType" placeholder="请选择通知方式">
            <el-option label="站内通知" value="site_notice" />
            <el-option label="邮件" value="email" />
            <el-option label="短信" value="sms" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="escForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="escDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEscalation">确定</el-button>
      </template>
    </el-dialog>

    <!-- 静默窗口编辑对话框 -->
    <el-dialog v-model="silenceDialogVisible" :title="silenceDialogTitle" width="500px">
      <el-form ref="silenceFormRef" :model="silenceForm" :rules="silenceRules_form" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="silenceForm.name" placeholder="请输入窗口名称" />
        </el-form-item>
        <el-form-item label="设备ID" prop="deviceId">
          <el-input v-model.number="silenceForm.deviceId" placeholder="留空表示全部设备" />
        </el-form-item>
        <el-form-item label="监测器ID" prop="monitorId">
          <el-input v-model.number="silenceForm.monitorId" placeholder="留空表示全部监测器" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="silenceForm.startTime" type="datetime" placeholder="选择开始时间" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="silenceForm.endTime" type="datetime" placeholder="选择结束时间" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="silenceForm.reason" placeholder="如：计划维护" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="silenceForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="silenceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSilence">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="IotAlarm">
import { ref, reactive, watch, onBeforeUnmount } from 'vue'
import {
  getAlarmRules, getAlarmMessages,
  listCorrelationRules, addCorrelationRule, updateCorrelationRule, deleteCorrelationRule,
  listEscalationPolicies, addEscalationPolicy, updateEscalationPolicy, deleteEscalationPolicy,
  listSilenceWindows, addSilenceWindow, updateSilenceWindow, deleteSilenceWindow
} from "@/api/iot/alarm"
import { parseTime } from "@/utils/formatters"
import { showSystarError } from "@/utils/errorHandler"
import { ElMessageBox, ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import Breadcrumb from '@/components/Breadcrumb.vue'
import EnhancedTable from '@/components/EnhancedTable/index.vue'
import { useAutoRefresh } from '@/composables/useAutoRefresh'

const route    = useRoute()

const TAB_MAP = {
  messages: 'messages',
  rules: 'rules',
  correlation: 'correlation',
  escalation: 'escalation',
  silence: 'silence'
}
const activeTab = ref(TAB_MAP[route.query.tab] || 'rules')

watch(() => route.query.tab, (tab) => {
  if (tab && TAB_MAP[tab]) activeTab.value = tab
})

const showSearch = ref(true)

// --- Column definitions ---
const rulesColumns = [
  { prop: 'id', label: 'ID', width: 80, align: 'center', sortable: true },
  { prop: 'monitorId', label: '监测器ID', width: 120, align: 'center' },
  { prop: 'strategy', label: '策略', width: 120, align: 'center', slot: true,
    filterable: true, filterOptions: [
      { label: '单次', value: 'ONLY_ONCE' }, { label: '持续', value: 'CONTINUOUS' }, { label: '选择', value: 'SELECTIVE' }
    ] },
  { prop: 'way', label: '通知方式', width: 100, align: 'center', slot: true },
  { prop: 'eventRankId', label: '告警级别', width: 100, align: 'center' },
  { prop: 'start', label: '触发次数', width: 100, align: 'center', slot: true },
  { prop: 'messageTemplate', label: '消息模板', showOverflowTooltip: true },
  { prop: 'enabled', label: '状态', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }] },
]

const messagesColumns = [
  { prop: 'id', label: 'ID', width: 80, align: 'center', sortable: true },
  { prop: 'monitorId', label: '监测器ID', width: 120, align: 'center' },
  { prop: 'caption', label: '告警标题', showOverflowTooltip: true },
  { prop: 'correlationGroup', label: '关联组', width: 180, showOverflowTooltip: true },
  { prop: 'state', label: '处理状态', width: 100, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '待处理', value: 1 }, { label: '已处理', value: 2 }] },
  { prop: 'escalationLevel', label: '升级', width: 80, align: 'center', slot: true },
  { prop: 'recovered', label: '恢复', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '未恢复', value: 0 }, { label: '已恢复', value: 1 }] },
  { prop: 'alarmTime', label: '告警时间', width: 180, align: 'center', slot: true, sortable: 'custom' },
]

const corrColumns = [
  { prop: 'id', label: 'ID', width: 80, align: 'center', sortable: true },
  { prop: 'name', label: '名称', showOverflowTooltip: true },
  { prop: 'deviceId', label: '设备ID', width: 120, align: 'center', slot: true },
  { prop: 'windowSeconds', label: '时间窗口(秒)', width: 140, align: 'center' },
  { prop: 'enabled', label: '状态', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }] },
  { prop: 'operations', label: '操作', width: 160, align: 'center', fixed: 'right', slot: true },
]

const escColumns = [
  { prop: 'id', label: 'ID', width: 80, align: 'center', sortable: true },
  { prop: 'name', label: '名称', showOverflowTooltip: true },
  { prop: 'fromLevel', label: '源级别', width: 100, align: 'center' },
  { prop: 'toLevel', label: '目标级别', width: 100, align: 'center' },
  { prop: 'timeoutMinutes', label: '超时(分钟)', width: 120, align: 'center' },
  { prop: 'notifyType', label: '通知方式', width: 120, align: 'center' },
  { prop: 'enabled', label: '状态', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }] },
  { prop: 'operations', label: '操作', width: 160, align: 'center', fixed: 'right', slot: true },
]

const silenceColumns = [
  { prop: 'id', label: 'ID', width: 80, align: 'center', sortable: true },
  { prop: 'name', label: '名称', showOverflowTooltip: true },
  { prop: 'deviceId', label: '设备ID', width: 120, align: 'center', slot: true },
  { prop: 'monitorId', label: '监测器ID', width: 120, align: 'center', slot: true },
  { prop: 'startTime', label: '开始时间', width: 180, align: 'center', slot: true, sortable: 'custom' },
  { prop: 'endTime', label: '结束时间', width: 180, align: 'center', slot: true },
  { prop: 'reason', label: '原因', showOverflowTooltip: true },
  { prop: 'enabled', label: '状态', width: 80, align: 'center', slot: true,
    filterable: true, filterOptions: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }] },
  { prop: 'operations', label: '操作', width: 160, align: 'center', fixed: 'right', slot: true },
]

// --- Context menus ---
const corrContextMenu = [
  { label: '编辑', command: 'edit' },
  { label: '删除', command: 'delete', danger: true },
]

const escContextMenu = [
  { label: '编辑', command: 'edit' },
  { label: '删除', command: 'delete', danger: true },
]

const silenceContextMenu = [
  { label: '编辑', command: 'edit' },
  { label: '删除', command: 'delete', danger: true },
]

// --- 告警规则 ---
const rules = ref([])
const rulesLoading = ref(false)

function loadRules() {
  rulesLoading.value = true
  getAlarmRules().then(res => {
    rules.value = res.data || []
    rulesLoading.value = false
  }).catch(err => {
    rulesLoading.value = false
    showSystarError(err, '加载告警规则失败')
  })
}

// --- 告警消息 ---
const messages = ref([])
const messageTotal = ref(0)
const messagesLoading = ref(false)
const messageParams = ref({ page: 1, size: 20 })
const messageFilter = reactive({ state: undefined, recovered: undefined })

// Apply state filter from query param (e.g. ?state=1 for pending)
if (route.query.state) {
  const stateVal = Number(route.query.state)
  if (!isNaN(stateVal)) messageFilter.state = stateVal
}

function buildMessageParams() {
  const params = { ...messageParams.value }
  if (messageFilter.state !== undefined) params.state = messageFilter.state
  if (messageFilter.recovered !== undefined) params.recovered = messageFilter.recovered
  return params
}

function loadMessages() {
  messagesLoading.value = true
  getAlarmMessages(buildMessageParams()).then(res => {
    messages.value = res.rows || []
    messageTotal.value = res.total || 0
    messagesLoading.value = false
  }).catch(err => {
    messagesLoading.value = false
    showSystarError(err, '加载告警消息失败')
  })
}

const msgAutoRefresh = useAutoRefresh(
  async () => {
    const res = await getAlarmMessages(buildMessageParams())
    messages.value = res.rows || []
    messageTotal.value = res.total || 0
    return res.rows || []
  },
  { interval: 15000, idField: 'id', compareField: 'state', highlightDuration: 3000 }
)
function messageRowClassName({ row }) {
  return msgAutoRefresh.highlightedIds.value.has(row.id) ? 'row-changed' : ''
}

function handleMessageQuery() {
  messageParams.value.page = 1
  loadMessages()
  msgAutoRefresh.stop()
  msgAutoRefresh.start()
}

function handleMessageSort({ prop, order }) {
  if (!prop || !order) return loadMessages()
  messageParams.value.sort = order ? `${prop},${order === 'ascending' ? 'asc' : 'desc'}` : undefined
  loadMessages()
}

// --- Context command handlers ---
function handleCorrContextCmd(command, row) {
  if (command === 'edit') handleEditCorrelation(row)
  else if (command === 'delete') handleDeleteCorrelation(row)
}

function handleEscContextCmd(command, row) {
  if (command === 'edit') handleEditEscalation(row)
  else if (command === 'delete') handleDeleteEscalation(row)
}

function handleSilenceContextCmd(command, row) {
  if (command === 'edit') handleEditSilence(row)
  else if (command === 'delete') handleDeleteSilence(row)
}

// --- 关联规则 ---
const corrRules = ref([])
const corrTotal = ref(0)
const corrLoading = ref(false)
const corrParams = ref({ page: 1, size: 20 })
const corrDialogVisible = ref(false)
const corrDialogTitle = ref('')
const corrFormRef = ref(null)
const corrForm = ref({})
const corrRules_form = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  windowSeconds: [{ required: true, message: '请输入时间窗口', trigger: 'blur' }]
}

function loadCorrelationRules() {
  corrLoading.value = true
  listCorrelationRules(corrParams.value).then(res => {
    const data = res.data || {}
    corrRules.value = data.records || []
    corrTotal.value = data.total || 0
    corrLoading.value = false
  }).catch(err => {
    corrLoading.value = false
    showSystarError(err, '加载关联规则失败')
  })
}

function handleAddCorrelation() {
  corrForm.value = { name: '', deviceId: null, windowSeconds: 300, enabled: 1 }
  corrDialogTitle.value = '新增关联规则'
  corrDialogVisible.value = true
}

function handleEditCorrelation(row) {
  corrForm.value = { ...row }
  corrDialogTitle.value = '编辑关联规则'
  corrDialogVisible.value = true
}

function submitCorrelation() {
  corrFormRef.value?.validate(valid => {
    if (!valid) return
    const api = corrForm.value.id
      ? updateCorrelationRule(corrForm.value.id, corrForm.value)
      : addCorrelationRule(corrForm.value)
    api.then(() => {
      ElMessage.success('操作成功')
      corrDialogVisible.value = false
      loadCorrelationRules()
    }).catch(err => showSystarError(err, '操作失败'))
  })
}

function handleDeleteCorrelation(row) {
  ElMessageBox.confirm(`确认删除关联规则"${row.name}"?`, '提示', { type: 'warning' }).then(() => {
    deleteCorrelationRule(row.id).then(() => {
      ElMessage.success('删除成功')
      loadCorrelationRules()
    }).catch(err => showSystarError(err, '删除失败'))
  }).catch(() => {})
}

// --- 升级策略 ---
const escPolicies = ref([])
const escTotal = ref(0)
const escLoading = ref(false)
const escParams = ref({ page: 1, size: 20 })
const escDialogVisible = ref(false)
const escDialogTitle = ref('')
const escFormRef = ref(null)
const escForm = ref({})
const escRules_form = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  fromLevel: [{ required: true, message: '请输入源级别', trigger: 'blur' }],
  toLevel: [{ required: true, message: '请输入目标级别', trigger: 'blur' }],
  timeoutMinutes: [{ required: true, message: '请输入超时时间', trigger: 'blur' }]
}

function loadEscalationPolicies() {
  escLoading.value = true
  listEscalationPolicies(escParams.value).then(res => {
    const data = res.data || {}
    escPolicies.value = data.records || []
    escTotal.value = data.total || 0
    escLoading.value = false
  }).catch(err => {
    escLoading.value = false
    showSystarError(err, '加载升级策略失败')
  })
}

function handleAddEscalation() {
  escForm.value = { name: '', fromLevel: 1, toLevel: 2, timeoutMinutes: 30, notifyType: 'site_notice', enabled: 1 }
  escDialogTitle.value = '新增升级策略'
  escDialogVisible.value = true
}

function handleEditEscalation(row) {
  escForm.value = { ...row }
  escDialogTitle.value = '编辑升级策略'
  escDialogVisible.value = true
}

function submitEscalation() {
  escFormRef.value?.validate(valid => {
    if (!valid) return
    const api = escForm.value.id
      ? updateEscalationPolicy(escForm.value.id, escForm.value)
      : addEscalationPolicy(escForm.value)
    api.then(() => {
      ElMessage.success('操作成功')
      escDialogVisible.value = false
      loadEscalationPolicies()
    }).catch(err => showSystarError(err, '操作失败'))
  })
}

function handleDeleteEscalation(row) {
  ElMessageBox.confirm(`确认删除升级策略"${row.name}"?`, '提示', { type: 'warning' }).then(() => {
    deleteEscalationPolicy(row.id).then(() => {
      ElMessage.success('删除成功')
      loadEscalationPolicies()
    }).catch(err => showSystarError(err, '删除失败'))
  }).catch(() => {})
}

// --- 静默窗口 ---
const silenceWindows = ref([])
const silenceTotal = ref(0)
const silenceLoading = ref(false)
const silenceParams = ref({ page: 1, size: 20 })
const silenceDialogVisible = ref(false)
const silenceDialogTitle = ref('')
const silenceFormRef = ref(null)
const silenceForm = ref({})
const silenceRules_form = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

function loadSilenceWindows() {
  silenceLoading.value = true
  listSilenceWindows(silenceParams.value).then(res => {
    const data = res.data || {}
    silenceWindows.value = data.records || []
    silenceTotal.value = data.total || 0
    silenceLoading.value = false
  }).catch(err => {
    silenceLoading.value = false
    showSystarError(err, '加载静默窗口失败')
  })
}

function handleAddSilence() {
  silenceForm.value = { name: '', deviceId: null, monitorId: null, startTime: '', endTime: '', reason: '', enabled: 1 }
  silenceDialogTitle.value = '新增静默窗口'
  silenceDialogVisible.value = true
}

function handleEditSilence(row) {
  silenceForm.value = { ...row }
  silenceDialogTitle.value = '编辑静默窗口'
  silenceDialogVisible.value = true
}

function submitSilence() {
  silenceFormRef.value?.validate(valid => {
    if (!valid) return
    const api = silenceForm.value.id
      ? updateSilenceWindow(silenceForm.value.id, silenceForm.value)
      : addSilenceWindow(silenceForm.value)
    api.then(() => {
      ElMessage.success('操作成功')
      silenceDialogVisible.value = false
      loadSilenceWindows()
    }).catch(err => showSystarError(err, '操作失败'))
  })
}

function handleDeleteSilence(row) {
  ElMessageBox.confirm(`确认删除静默窗口"${row.name}"?`, '提示', { type: 'warning' }).then(() => {
    deleteSilenceWindow(row.id).then(() => {
      ElMessage.success('删除成功')
      loadSilenceWindows()
    }).catch(err => showSystarError(err, '删除失败'))
  }).catch(() => {})
}

// --- common ---
function handleTabChange(tab) {
  msgAutoRefresh.stop()
  if (tab === 'rules') loadRules()
  else if (tab === 'messages') { loadMessages(); msgAutoRefresh.start() }
  else if (tab === 'correlation') loadCorrelationRules()
  else if (tab === 'escalation') loadEscalationPolicies()
  else if (tab === 'silence') loadSilenceWindows()
}

function strategyLabel(strategy) {
  const map = { ONLY_ONCE: '单次', CONTINUOUS: '持续', SELECTIVE: '选择' }
  return map[strategy] || strategy || ''
}

function wayLabel(way) {
  if (!way) return ''
  const parts = []
  if (way & 1) parts.push('声音')
  if (way & 2) parts.push('邮件')
  if (way & 4) parts.push('界面')
  return parts.join(', ')
}

if (activeTab.value === 'rules') {
  loadRules()
} else {
  handleTabChange(activeTab.value)
}

onBeforeUnmount(() => { msgAutoRefresh.stop() })
</script>

<style scoped>
:deep(.el-table .row-changed td) {
  background-color: rgba(64, 158, 255, 0.12) !important;
  transition: background-color 0.3s;
}
</style>
