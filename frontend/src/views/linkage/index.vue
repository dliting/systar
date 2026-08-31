<template>
  <div class="app-container linkage-page">
    <Breadcrumb />
    <!-- toolbar -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增规则</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadRules" />
    </el-row>

    <!-- rule list -->
    <EnhancedTable
      :data="rules"
      :columns="linkageColumns"
      :loading="loading"
      row-key="rule.id"
      :default-expand-all="true"
      :exportable="true"
      export-file-name="联动规则"
      :context-menu="linkageContextMenu"
      @context-command="handleLinkageContextCmd"
    >
      <template #expanded-row="{ row }">
        <div class="expand-content">
          <div class="expand-section">
            <span class="expand-title">触发条件：</span>
            <div class="expand-tags">
              <el-tag v-for="c in row.causes" :key="c.id" size="small" type="warning" class="condition-tag">
                {{ assetNameMap[c.causeMonitorId] || ('#' + c.causeMonitorId) }}
                <span v-if="c.triggerValue"> = {{ c.triggerValue === 'ALARM' ? '报警' : c.triggerValue }}</span>
              </el-tag>
              <el-empty v-if="!row.causes || row.causes.length === 0" description="无条件" :image-size="30" />
            </div>
          </div>
          <div class="expand-section">
            <span class="expand-title">执行动作：</span>
            <div class="expand-tags">
              <el-tag v-for="e in row.effects" :key="e.id" size="small" type="success" class="condition-tag">
                {{ assetNameMap[e.effectMonitorId] || ('#' + e.effectMonitorId) }} → {{ e.effectCommand === '-1' ? '切换' : e.effectCommand }}
              </el-tag>
              <el-empty v-if="!row.effects || row.effects.length === 0" description="无动作" :image-size="30" />
            </div>
          </div>
        </div>
      </template>
      <template #column-rule.causeType="{ row }">
        <el-tag :type="row.rule.causeType === 'ALARM' ? 'danger' : 'primary'" size="small">
          {{ row.rule.causeType === 'ALARM' ? '报警联动' : '监控器联动' }}
        </el-tag>
      </template>
      <template #column-rule.enabled="{ row }">
        <el-switch
          :model-value="row.rule.enabled"
          size="small"
          @change="handleToggle(row.rule)"
        />
      </template>
      <template #column-operations="{ row }">
        <el-button link type="primary" icon="Edit" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="danger" icon="Delete" @click="handleDelete(row.rule)">删除</el-button>
      </template>
    </EnhancedTable>

    <el-empty v-if="!loading && rules.length === 0" description="暂无联动规则" />

    <!-- create / edit dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入规则名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="触发类型" prop="causeType">
              <el-radio-group v-model="form.causeType" @change="onCauseTypeChange">
                <el-radio value="MONITOR">监控器联动</el-radio>
                <el-radio value="ALARM">报警联动</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规则描述">
          <el-input v-model="form.caption" type="textarea" :rows="2" placeholder="描述（选填）" />
        </el-form-item>

        <!-- dual-tree for MONITOR linkage -->
        <template v-if="form.causeType === 'MONITOR'">
          <el-divider content-position="left">选择触发监测器和目标控制器</el-divider>
          <div class="dual-tree-container">
            <div class="tree-panel">
              <div class="tree-panel-header">
                <el-icon><Monitor /></el-icon> 触发监测器
                <el-tag size="small" type="info">{{ form.causes.length }} 个</el-tag>
              </div>
              <div class="tree-panel-body">
                <el-input v-model="causeSearchKey" placeholder="搜索监测器" clearable class="tree-search" />
                <el-tree
                  ref="causeTreeRef"
                  :data="monitorTreeData"
                  :props="treeProps"
                  show-checkbox
                  check-strictly
                  node-key="id"
                  :filter-node-method="filterNode"
                  :default-checked-keys="selectedCauseIds"
                  @check="onCauseCheck"
                >
                  <template #default="{ node, data }">
                    <span class="tree-node-label">
                      <el-icon v-if="data.kind === 'PROBE'" class="node-icon probe"><Monitor /></el-icon>
                      <el-icon v-else-if="data.kind === 'SPACE' || data.kind === 'DEVICE'" class="node-icon folder"><FolderOpened /></el-icon>
                      <el-icon v-else class="node-icon"><Coin /></el-icon>
                      {{ node.label }}
                    </span>
                  </template>
                </el-tree>
              </div>
              <!-- cause trigger value -->
              <div class="selected-list">
                <div v-for="c in form.causes" :key="c.causeMonitorId" class="selected-item">
                  <el-tag size="small" closable @close="removeCause(c.causeMonitorId)">
                    {{ assetNameMap[c.causeMonitorId] || ('#' + c.causeMonitorId) }}
                  </el-tag>
                  <el-input
                    v-model="c.triggerValue"
                    placeholder="触发值"
                    style="width: 90px; margin-left: 6px"
                    size="small"
                  />
                </div>
              </div>
            </div>

            <div class="tree-divider">
              <el-icon :size="24" color="#c0c4cc"><Right /></el-icon>
            </div>

            <div class="tree-panel">
              <div class="tree-panel-header">
                <el-icon><Switch /></el-icon> 目标控制器
                <el-tag size="small" type="info">{{ form.effects.length }} 个</el-tag>
              </div>
              <div class="tree-panel-body">
                <el-input v-model="effectSearchKey" placeholder="搜索控制器" clearable class="tree-search" />
                <el-tree
                  ref="effectTreeRef"
                  :data="controlTreeData"
                  :props="treeProps"
                  show-checkbox
                  check-strictly
                  node-key="id"
                  :filter-node-method="filterNode"
                  :default-checked-keys="selectedEffectIds"
                  @check="onEffectCheck"
                >
                  <template #default="{ node, data }">
                    <span class="tree-node-label">
                      <el-icon v-if="data.kind === 'CONTROL'" class="node-icon control"><Switch /></el-icon>
                      <el-icon v-else-if="data.kind === 'SPACE' || data.kind === 'DEVICE'" class="node-icon folder"><FolderOpened /></el-icon>
                      <el-icon v-else class="node-icon"><Coin /></el-icon>
                      {{ node.label }}
                    </span>
                  </template>
                </el-tree>
              </div>
              <!-- effect commands -->
              <div class="selected-list">
                <div v-for="e in form.effects" :key="e.effectMonitorId" class="selected-item">
                  <el-tag size="small" type="success" closable @close="removeEffect(e.effectMonitorId)">{{ assetNameMap[e.effectMonitorId] || ('#' + e.effectMonitorId) }}</el-tag>
                  <el-select v-model="e.effectCommand" style="width: 110px; margin-left: 6px" size="small">
                    <el-option label="开启 (1)" value="1" />
                    <el-option label="关闭 (0)" value="0" />
                    <el-option label="切换 (-1)" value="-1" />
                  </el-select>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- ALARM linkage: alarm source selector -->
        <template v-if="form.causeType === 'ALARM'">
          <el-divider content-position="left">选择报警源和目标控制器</el-divider>
          <div class="dual-tree-container">
            <div class="tree-panel">
              <div class="tree-panel-header">
                <el-icon><WarningFilled /></el-icon> 报警监测器
                <el-tag size="small" type="info">{{ selectedCauseIds.length }} 个</el-tag>
              </div>
              <div class="tree-panel-body">
                <el-input v-model="alarmSearchKey" placeholder="搜索监测器" clearable class="tree-search" />
                <el-tree
                  ref="alarmTreeRef"
                  :data="monitorTreeData"
                  :props="treeProps"
                  show-checkbox
                  check-strictly
                  node-key="id"
                  :filter-node-method="filterNode"
                  :default-checked-keys="selectedCauseIds"
                  @check="onAlarmCheck"
                >
                  <template #default="{ node, data }">
                    <span class="tree-node-label">
                      <el-icon v-if="data.kind === 'PROBE'" class="node-icon probe"><Monitor /></el-icon>
                      <el-icon v-else-if="data.kind === 'SPACE' || data.kind === 'DEVICE'" class="node-icon folder"><FolderOpened /></el-icon>
                      <el-icon v-else class="node-icon"><Coin /></el-icon>
                      {{ node.label }}
                    </span>
                  </template>
                </el-tree>
              </div>
              <!-- selected alarm causes -->
              <div class="selected-list">
                <div v-for="c in form.causes" :key="c.causeMonitorId" class="selected-item">
                  <el-tag size="small" type="warning" closable @close="removeCause(c.causeMonitorId)">
                    {{ assetNameMap[c.causeMonitorId] || ('#' + c.causeMonitorId) }}
                  </el-tag>
                  <el-tag size="small" type="danger" style="margin-left: 6px">报警</el-tag>
                </div>
              </div>
            </div>

            <div class="tree-divider">
              <el-icon :size="24" color="#c0c4cc"><Right /></el-icon>
            </div>

            <div class="tree-panel">
              <div class="tree-panel-header">
                <el-icon><Switch /></el-icon> 目标控制器
                <el-tag size="small" type="info">{{ form.effects.length }} 个</el-tag>
              </div>
              <div class="tree-panel-body">
                <el-input v-model="effectSearchKey" placeholder="搜索控制器" clearable class="tree-search" />
                <el-tree
                  ref="effectTreeRef"
                  :data="controlTreeData"
                  :props="treeProps"
                  show-checkbox
                  check-strictly
                  node-key="id"
                  :filter-node-method="filterNode"
                  :default-checked-keys="selectedEffectIds"
                  @check="onEffectCheck"
                >
                  <template #default="{ node, data }">
                    <span class="tree-node-label">
                      <el-icon v-if="data.kind === 'CONTROL'" class="node-icon control"><Switch /></el-icon>
                      <el-icon v-else-if="data.kind === 'SPACE' || data.kind === 'DEVICE'" class="node-icon folder"><FolderOpened /></el-icon>
                      <el-icon v-else class="node-icon"><Coin /></el-icon>
                      {{ node.label }}
                    </span>
                  </template>
                </el-tree>
              </div>
              <div class="selected-list">
                <div v-for="e in form.effects" :key="e.effectMonitorId" class="selected-item">
                  <el-tag size="small" type="success" closable @close="removeEffect(e.effectMonitorId)">
                    {{ assetNameMap[e.effectMonitorId] || ('#' + e.effectMonitorId) }}
                  </el-tag>
                  <el-select v-model="e.effectCommand" style="width: 110px; margin-left: 6px" size="small">
                    <el-option label="开启 (1)" value="1" />
                    <el-option label="关闭 (0)" value="0" />
                    <el-option label="切换 (-1)" value="-1" />
                  </el-select>
                </div>
              </div>
            </div>
          </div>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="IotLinkage">
import { ref, computed, watch, nextTick } from 'vue'
import {
  listLinkageRules, createLinkageRule, updateLinkageRule,
  deleteLinkageRule, toggleLinkageRule, getAssetTree
} from '@/api/iot/linkage'
import { showSystarError, showSystarSuccess } from '@/utils/errorHandler'
import { Monitor, Switch, Right, WarningFilled, FolderOpened, Coin } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import EnhancedTable from '@/components/EnhancedTable/index.vue'

// ======================== table config ========================

const linkageColumns = [
  { type: 'expand' },
  { prop: 'rule.name', label: '规则名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'rule.causeType', label: '触发类型', width: 120, align: 'center', slot: true,
    filterable: true, filterOptions: [
      { label: '报警联动', value: 'ALARM' },
      { label: '监控器联动', value: 'MONITOR' },
    ] },
  { prop: 'rule.enabled', label: '状态', width: 90, align: 'center', slot: true },
  { prop: 'rule.caption', label: '描述', minWidth: 160, showOverflowTooltip: true },
  { prop: 'operations', label: '操作', width: 180, align: 'center', fixed: 'right', slot: true },
]

const linkageContextMenu = [
  { label: '编辑', command: 'edit' },
  { label: '删除', command: 'delete', danger: true },
]

function handleLinkageContextCmd(command, row) {
  if (command === 'edit') handleEdit(row)
  else if (command === 'delete') handleDelete(row.rule)
}

// ======================== state ========================

const loading      = ref(false)
const showSearch   = ref(true)
const rules        = ref([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const editingId    = ref(null)
const formRef      = ref(null)

const causeTreeRef  = ref(null)
const effectTreeRef = ref(null)
const alarmTreeRef  = ref(null)

const causeSearchKey  = ref('')
const effectSearchKey = ref('')
const alarmSearchKey  = ref('')

const monitorTreeData = ref([])
const controlTreeData = ref([])
const assetNameMap   = ref({})

const treeProps = { children: 'children', label: 'caption' }

const form = ref({
  name: '',
  causeType: 'MONITOR',
  caption: '',
  causes: [],
  effects: []
})

const formRules = {
  name:      [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  causeType: [{ required: true, message: '请选择触发类型', trigger: 'change' }]
}

const dialogTitle = computed(() => editingId.value ? '编辑联动规则' : '新增联动规则')

const selectedCauseIds  = computed(() => form.value.causes.map(c => c.causeMonitorId))
const selectedEffectIds = computed(() => form.value.effects.map(e => e.effectMonitorId))

// ======================== tree data ========================

function extractLeaves(node, kind, result) {
  if (!node) return
  if (node.kind === kind) {
    result.push(node)
  }
  if (node.children) {
    for (const child of node.children) {
      extractLeaves(child, kind, result)
    }
  }
}

function filterByKind(rootNode, kind) {
  const leaves = []
  extractLeaves(rootNode, kind, leaves)
  const ids = new Set(leaves.map(l => l.id))
  return pruneTree(rootNode, ids)
}

function pruneTree(node, keepIds) {
  if (!node) return null
  const children = (node.children || [])
    .map(c => pruneTree(c, keepIds))
    .filter(Boolean)
  if (keepIds.has(node.id) || children.length > 0) {
    return { ...node, children }
  }
  return null
}

function buildNameMap(node, map) {
  if (!node) return
  map[node.id] = node.caption || node.name
  if (node.children) {
    for (const child of node.children) {
      buildNameMap(child, map)
    }
  }
}

async function loadTreeData() {
  try {
    const res = await getAssetTree()
    const root = res.data
    if (!root) return
    const map = {}
    buildNameMap(root, map)
    assetNameMap.value    = map
    monitorTreeData.value  = [filterByKind(root, 'PROBE')].filter(Boolean)
    controlTreeData.value  = [filterByKind(root, 'CONTROL')].filter(Boolean)
  } catch (e) {
    showSystarError(e, '加载资产树失败')
  }
}

// ======================== tree search ========================

function filterNode(value, data) {
  if (!value) return true
  return (data.caption || data.name || '').indexOf(value) !== -1
}

watch(causeSearchKey, v => causeTreeRef.value?.filter(v))
watch(effectSearchKey, v => effectTreeRef.value?.filter(v))
watch(alarmSearchKey, v => alarmTreeRef.value?.filter(v))

// ======================== tree check handlers ========================

function onCauseCheck() {
  const checked = causeTreeRef.value.getCheckedNodes()
  form.value.causes = checked
    .filter(n => n.kind === 'PROBE')
    .map(n => {
      const existing = form.value.causes.find(c => c.causeMonitorId === n.id)
      return existing || { causeMonitorId: n.id, triggerValue: '1' }
    })
}

function onEffectCheck() {
  const checked = effectTreeRef.value.getCheckedNodes()
  form.value.effects = checked
    .filter(n => n.kind === 'CONTROL')
    .map(n => {
      const existing = form.value.effects.find(e => e.effectMonitorId === n.id)
      return existing || { effectMonitorId: n.id, effectCommand: '-1' }
    })
}

function onAlarmCheck() {
  const checked = alarmTreeRef.value.getCheckedNodes()
  form.value.causes = checked
    .filter(n => n.kind === 'PROBE')
    .map(n => {
      const existing = form.value.causes.find(c => c.causeMonitorId === n.id)
      return existing || { causeMonitorId: n.id, triggerValue: 'ALARM' }
    })
}

function removeCause(monitorId) {
  form.value.causes = form.value.causes.filter(c => c.causeMonitorId !== monitorId)
  nextTick(() => {
    if (form.value.causeType === 'MONITOR') {
      causeTreeRef.value?.setCheckedKeys(form.value.causes.map(c => c.causeMonitorId))
    } else {
      alarmTreeRef.value?.setCheckedKeys(form.value.causes.map(c => c.causeMonitorId))
    }
  })
}

function removeEffect(monitorId) {
  form.value.effects = form.value.effects.filter(e => e.effectMonitorId !== monitorId)
  nextTick(() => {
    effectTreeRef.value?.setCheckedKeys(form.value.effects.map(e => e.effectMonitorId))
  })
}

function onCauseTypeChange() {
  form.value.causes = []
  causeSearchKey.value  = ''
  effectSearchKey.value = ''
  nextTick(() => {
    effectTreeRef.value?.setCheckedKeys(form.value.effects.map(e => e.effectMonitorId))
  })
}

// ======================== data loading ========================

function loadRules() {
  loading.value = true
  listLinkageRules().then(res => {
    rules.value = res.data || []
    loading.value = false
  }).catch(err => {
    loading.value = false
    showSystarError(err, '加载联动规则失败')
  })
}

// ======================== CRUD handlers ========================

async function handleAdd() {
  editingId.value = null
  form.value = { name: '', causeType: 'MONITOR', caption: '', causes: [], effects: [] }
  if (!monitorTreeData.value.length) await loadTreeData()
  dialogVisible.value = true
}

async function handleEdit(row) {
  editingId.value = row.rule.id
  const r = row.rule
  form.value = {
    name: r.name,
    causeType: r.causeType,
    caption: r.caption || '',
    causes: (row.causes || []).map(c => ({
      causeMonitorId: c.causeMonitorId,
      triggerValue: c.triggerValue
    })),
    effects: (row.effects || []).map(e => ({
      effectMonitorId: e.effectMonitorId,
      effectCommand: e.effectCommand
    }))
  }
  if (!monitorTreeData.value.length) await loadTreeData()
  dialogVisible.value = true
  // Set checked keys after tree renders
  nextTick(() => {
    if (form.value.causeType === 'MONITOR') {
      causeTreeRef.value?.setCheckedKeys(form.value.causes.map(c => c.causeMonitorId))
    } else {
      alarmTreeRef.value?.setCheckedKeys(form.value.causes.map(c => c.causeMonitorId))
    }
    effectTreeRef.value?.setCheckedKeys(form.value.effects.map(e => e.effectMonitorId))
  })
}

function handleDelete(rule) {
  ElMessageBox.confirm(`确定要删除联动规则「${rule.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleteLinkageRule(rule.id).then(() => {
      showSystarSuccess('删除成功')
      loadRules()
    }).catch(err => showSystarError(err, '删除失败'))
  }).catch(() => {})
}

function handleToggle(rule) {
  toggleLinkageRule(rule.id).then(() => {
    showSystarSuccess(rule.enabled ? '已禁用' : '已启用')
    loadRules()
  }).catch(err => showSystarError(err, '操作失败'))
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch { return }

  if (form.value.causes.length === 0) {
    ElMessage.warning('请至少选择一个触发条件')
    return
  }
  if (form.value.causeType === 'MONITOR') {
    const emptyCause = form.value.causes.find(c => !c.triggerValue && c.triggerValue !== 0)
    if (emptyCause) {
      ElMessage.warning('请为所有触发条件设置触发值')
      return
    }
  }
  if (form.value.effects.length === 0) {
    ElMessage.warning('请至少选择一个执行动作')
    return
  }

  submitLoading.value = true
  const payload = {
    name: form.value.name,
    causeType: form.value.causeType,
    caption: form.value.caption,
    causes: form.value.causes,
    effects: form.value.effects
  }

  try {
    if (editingId.value) {
      await updateLinkageRule(editingId.value, payload)
      showSystarSuccess('更新成功')
    } else {
      await createLinkageRule(payload)
      showSystarSuccess('创建成功')
    }
    dialogVisible.value = false
    loadRules()
  } catch (err) {
    showSystarError(err, '保存失败')
  } finally {
    submitLoading.value = false
  }
}

loadRules()
loadTreeData()
</script>

<style scoped lang="scss">
.linkage-page {
  .expand-content {
    padding: 12px 48px;
  }
  .expand-section {
    margin-bottom: 8px;
    display: flex;
    align-items: flex-start;
    gap: 8px;
    flex-wrap: wrap;
  }
  .expand-title {
    font-weight: 600;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }
  .expand-tags {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
  .condition-tag {
    margin-right: 4px;
  }

  .dual-tree-container {
    display: flex;
    gap: 0;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    overflow: hidden;
    min-height: 350px;
  }

  .tree-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .tree-panel-header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 14px;
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color-lighter);
    font-weight: 600;
    font-size: 13px;
    color: var(--el-text-color-primary);
  }

  .tree-panel-body {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
    max-height: 280px;
  }

  .tree-search {
    margin-bottom: 8px;
  }

  .tree-divider {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    background: var(--el-fill-color-lighter);
    border-left: 1px solid var(--el-border-color-lighter);
    border-right: 1px solid var(--el-border-color-lighter);
  }

  .selected-list {
    border-top: 1px solid var(--el-border-color-lighter);
    padding: 8px 10px;
    max-height: 120px;
    overflow-y: auto;
    background: var(--el-fill-color-lighter);
  }

  .selected-item {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
  }

  .tree-node-label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;

    .node-icon {
      font-size: 14px;
      &.probe   { color: #409eff; }
      &.control { color: #67c23a; }
      &.folder  { color: #e6a23c; }
    }
  }
}
</style>
