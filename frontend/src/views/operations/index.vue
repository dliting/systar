<template>
  <div class="app-container tree-sidebar-manage-wrap">
    <tree-panel
      title="资产树"
      :tree-data="treeData"
      :tree-props="{ children: 'children', label: 'caption' }"
      search-placeholder="搜索资产名称"
      storage-key="iot-ops-sidebar-width"
      :default-expand-all="true"
      @node-click="handleNodeClick"
      @refresh="getTree"
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
          <span class="node-label" :title="data.caption">{{ data.caption || data.name }}</span>
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
        <el-empty v-if="!selectedNode" description="请在左侧选择资产节点" />

        <template v-else>
          <Skeleton v-if="!detail.id" variant="card" animated />
          <template v-else>
          <div class="breadcrumb-bar">
            <span
              v-for="(seg, i) in breadcrumbs"
              :key="i"
              class="breadcrumb-seg"
              :class="{ clickable: i < breadcrumbs.length - 1 }"
              @click="i < breadcrumbs.length - 1 && navigateTo(seg.id)"
            >
              {{ seg.caption || seg.name }}
              <el-icon v-if="i < breadcrumbs.length - 1" class="breadcrumb-arrow"><ArrowRight /></el-icon>
            </span>
            <el-dropdown v-if="siblingNodes.length > 0" trigger="click" :teleported="false" @command="navigateTo">
              <span class="breadcrumb-seg clickable dropdown-trigger">▾</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="sib in siblingNodes"
                    :key="sib.id"
                    :command="sib.id"
                  >{{ sib.caption || sib.name }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <!-- Toolbar -->
          <div class="toolbar-bar">
            <el-button
              v-if="canAddChild"
              type="primary" size="small" icon="Plus"
              @click="openCreateDialog"
            >新增子节点</el-button>
            <el-button size="small" icon="Edit" @click="openEditDialog">编辑</el-button>
            <el-button size="small" type="danger" icon="Delete" @click="handleDelete">删除</el-button>
            <el-divider direction="vertical" />
            <el-button v-if="canEnable" size="small" type="success" icon="VideoPlay" @click="handleOperate('enable')">启用</el-button>
            <el-button v-if="canDisable" size="small" type="warning" icon="VideoPause" @click="handleOperate('disable')">禁用</el-button>
            <el-button v-if="canStartStop" size="small" type="success" icon="VideoPlay" @click="handleOperate('start')">启动</el-button>
            <el-button v-if="canStartStop" size="small" type="danger" icon="VideoPause" @click="handleOperate('stop')">停止</el-button>
          </div>

          <!-- Card Dashboard -->
          <div class="card-dashboard">
            <!-- Basic Info Card -->
            <el-card shadow="never" class="info-card">
              <template #header>
                <div class="card-header">
                  <span>{{ detail.caption || detail.name }}</span>
                  <span>
                    <el-tag :type="stateTagType(detail.state)" size="small">{{ detail.stateCaption || detail.state }}</el-tag>
                    <el-tag :type="detail.enabled ? 'success' : 'info'" size="small" style="margin-left:4px">
                      {{ detail.enabled ? '已启用' : '已禁用' }}
                    </el-tag>
                  </span>
                </div>
              </template>
              <!-- Error state alert -->
              <el-alert
                v-if="detail.state === 'ERROR'"
                :title="detail.runtimeDesc || '未知错误'"
                type="error"
                show-icon
                :closable="false"
                style="margin-bottom:12px"
              />
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
                <el-descriptions-item label="名称">
                  <InlineEdit :value="detail.name || ''" @save="v => updateField('name', v)" />
                </el-descriptions-item>
                <el-descriptions-item label="标题">
                  <InlineEdit :value="detail.caption || ''" placeholder="点击设置标题" @save="v => updateField('caption', v)" />
                </el-descriptions-item>
                <el-descriptions-item label="类型">
                  <el-tag :type="kindTagType(detail.kind)">{{ kindLabel(detail.kind) }}</el-tag>
                  <span v-if="detail.typeCaption" style="margin-left:6px;color:#666;font-size:12px">{{ detail.typeCaption }}</span>
                  <span v-else-if="detail.typeName" style="margin-left:6px;color:#999;font-size:12px">{{ detail.typeName }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="父级ID">{{ detail.parentId || '-' }}</el-descriptions-item>
                <el-descriptions-item label="路径" :span="2">{{ displayPath || '-' }}</el-descriptions-item>
                <el-descriptions-item v-if="detail.kind === 'PROBE' || detail.kind === 'CONTROL'" label="监测值">
                  {{ (detail.value !== null && detail.value !== undefined ? detail.value : '—') + (detail.unit ? ' ' + detail.unit : '') }}
                </el-descriptions-item>
                <el-descriptions-item v-if="detail.kind === 'PROBE' || detail.kind === 'CONTROL'" label="单位">
                  <InlineEdit :value="detail.unit || ''" placeholder="如 ℃" @save="v => updateField('unit', v)" />
                </el-descriptions-item>
                <el-descriptions-item v-if="detail.isVirtual" label="虚拟探头"><el-tag type="info" size="small">是</el-tag></el-descriptions-item>
                <el-descriptions-item v-if="detail.expression" label="表达式" :span="2">
                  <InlineEdit :value="detail.expression" @save="v => updateField('expression', v)" />
                </el-descriptions-item>
                <el-descriptions-item v-if="detail.dependsOn" label="依赖探头">{{ detail.dependsOn }}</el-descriptions-item>
                <template v-if="detail.attributes">
                  <el-descriptions-item
                    v-for="(v, k) in detail.attributes"
                    :key="k"
                    :label="attrLabel(k)"
                  >{{ v }}</el-descriptions-item>
                </template>
              </el-descriptions>

              <!-- Error/Warning banner -->
              <div v-if="detail.runtimeDesc && (detail.state === 'ERROR' || detail.state === 'WARNING')" class="runtime-error-banner" :class="detail.state === 'ERROR' ? 'error' : 'warning'">
                <el-icon><WarningFilled /></el-icon>
                <span>{{ detail.runtimeDesc }}</span>
              </div>
            </el-card>

            <!-- Children Card (Space/Device only) -->
            <el-card v-if="isCompound" shadow="never" class="children-card">
              <template #header>
                <div class="card-header" style="display:flex;justify-content:space-between;align-items:center;">
                  <span>子节点 ({{ childCount }})</span>
                  <div>
                    <span v-if="selection.length > 0" style="font-size:12px;color:#666;margin-right:8px">
                      已选 {{ selection.length }} 项
                    </span>
                    <el-button size="small" @click="batchOperate('start')" :disabled="!selection.length">批量启动</el-button>
                    <el-button size="small" @click="batchOperate('stop')" :disabled="!selection.length">批量停止</el-button>
                    <el-button size="small" type="danger" @click="batchOperate('delete')" :disabled="!selection.length">批量删除</el-button>
                  </div>
                </div>
              </template>
              <!-- Stats bar -->
              <div class="stats-bar">
                <span>总数: {{ childCount }}</span>
                <span class="stat-normal">正常: {{ stateCount('NORMAL') }}</span>
                <span class="stat-warning">警告: {{ stateCount('WARNING') }}</span>
                <span class="stat-error">错误: {{ stateCount('ERROR') }}</span>
                <span class="stat-offline">离线: {{ stateCount('OFFLINE') }}</span>
              </div>
              <el-table
                :data="filteredChildren"
                stripe
                :row-class-name="childRowClassName"
                @selection-change="handleSelectionChange"
                @row-click="handleChildRowClick"
                style="cursor:pointer"
              >
                <el-table-column type="selection" width="40" />
                <el-table-column label="ID" prop="id" width="70" />
                <el-table-column label="名称" prop="name" show-overflow-tooltip />
                <el-table-column label="标题" prop="caption" show-overflow-tooltip />
                <el-table-column label="类型" width="130">
                  <template #default="{ row }">
                    <el-tag :type="kindTagType(row.kind)" size="small">{{ kindLabel(row.kind) }}</el-tag>
                    <span v-if="row.typeCaption" style="margin-left:4px;color:#666;font-size:11px">{{ row.typeCaption }}</span>
                    <span v-else-if="row.typeName" style="margin-left:4px;color:#666;font-size:11px">{{ row.typeName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="70">
                  <template #default="{ row }">
                    <el-tag :type="stateTagType(row.state)" size="small">{{ row.stateCaption || row.state }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="启用" width="70">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="该节点无子资产" :image-size="60" />
                </template>
              </el-table>
            </el-card>

            <!-- Monitoring Card (Probe/Control) -->
            <el-card v-if="detail.kind === 'PROBE' || detail.kind === 'CONTROL'" shadow="never" class="compact-card">
              <template #header>
                <div class="card-header" style="display:flex;justify-content:space-between;">
                  <span>监测数据
                    <el-tag v-if="detail.mode" size="small"
                      :type="detail.mode === 'ACTIVE' ? 'success' : 'info'" style="margin-left:6px">
                      {{ detail.mode === 'ACTIVE' ? '主动' : '被动' }}
                    </el-tag>
                  </span>
                  <span>
                    <el-button v-if="detail.mode === 'ACTIVE'" size="small" type="primary"
                      icon="Refresh" @click="handleRefresh" :loading="refreshing">刷新</el-button>
                    <el-button size="small" link type="primary" @click="openHistoryDialog">详情...</el-button>
                  </span>
                </div>
              </template>
              <div v-if="detail.state === 'ERROR' && detail.runtimeDesc" class="monitor-error-display">
                <el-icon :size="28" color="#ff5252"><WarningFilled /></el-icon>
                <span class="monitor-error-text">{{ detail.runtimeDesc }}</span>
              </div>
              <div v-else class="monitor-snapshot">
                <span class="monitor-value">{{ detail.value !== null && detail.value !== undefined ? detail.value : '—' }}<span class="monitor-unit">{{ detail.unit ? ' ' + detail.unit : '' }}</span></span>
                <span class="monitor-time" v-if="detail.lastDetectTime">{{ parseTime(detail.lastDetectTime) }}</span>
              </div>
              <TrendChart
                ref="miniTrendRef"
                :data-points="miniDataPoints"
                :intraday-points="miniIntradayPoints"
                :summary="miniSummary"
                :granularity="miniGranularity"
                :unit="detail?.unit || ''"
                :loading="miniLoading"
                compact
                chart-height="140px"
              />
            </el-card>

            <!-- Alarm Summary Card -->
            <el-card v-if="showAlarmCard" shadow="never" class="compact-card">
              <template #header>
                <div class="card-header" style="display:flex;justify-content:space-between;">
                  <span>告警状态</span>
                  <el-button size="small" link type="primary">详情...</el-button>
                </div>
              </template>
              <div class="summary-row">
                <span>活动告警: <strong>—</strong></span>
                <span>告警规则: <strong>—</strong></span>
              </div>
            </el-card>

            <!-- Linkage Summary Card -->
            <el-card v-if="showLinkageCard" shadow="never" class="compact-card">
              <template #header>
                <div class="card-header" style="display:flex;justify-content:space-between;">
                  <span>联动规则</span>
                  <el-button size="small" link type="primary">详情...</el-button>
                </div>
              </template>
              <div class="summary-row">
                <span>作为原因: <strong>—</strong></span>
                <span>作为效果: <strong>—</strong></span>
              </div>
            </el-card>

            <!-- Control Card (Control only) -->
            <el-card v-if="detail.kind === 'CONTROL'" shadow="never" class="compact-card">
              <template #header><span>控制面板</span></template>
              <ControlCommandInput
                v-model="controlCommand"
                :data-type="detail.dataType || 'STRING'"
                :view-type="detail.viewType"
                :slider-min="detail.minValue ?? 0"
                :slider-max="detail.maxValue ?? 100"
                :show-execute="true"
                :loading="!!executing"
                @execute="handleControlExecute"
              />
              <div v-if="controlResult" class="control-result" style="margin-top:8px;font-size:12px;color:#666;">
                结果: {{ controlResult }}
              </div>
            </el-card>
          </div>
          </template>
        </template>
      </div>
    </div>

    <!-- Create Dialog (Step Wizard) -->
    <el-dialog
      v-if="dialogMode === 'create'"
      v-model="dialogVisible"
      title="新增资产"
      width="600px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-steps :active="wizardStep - 1" finish-status="success" simple style="margin-bottom:20px">
        <el-step title="选类型" />
        <el-step title="基础信息" />
        <el-step title="属性配置" />
        <el-step title="确认创建" />
      </el-steps>

      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <!-- Step 1: Choose type -->
        <div v-show="wizardStep === 1">
          <el-form-item label="类型" prop="kind">
            <el-select v-model="form.kind" placeholder="选择资产类型" style="width:100%" @change="onKindChange">
              <el-option label="空间 (Space)" value="SPACE" />
              <el-option label="设备 (Device)" value="DEVICE" v-if="isCompound" />
              <el-option label="服务 (Service)" value="SERVICE" v-if="isCompound" />
              <el-option label="监测器 (Probe)" value="PROBE" v-if="isCompound" />
              <el-option label="控制器 (Control)" value="CONTROL" v-if="isCompound" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型名" prop="typeName" v-if="form.kind">
            <el-select v-model="form.typeName" placeholder="选择类型定义" style="width:100%" @change="onTypeNameChange">
              <el-option v-for="t in availableTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </div>

        <!-- Step 2: Basic info -->
        <div v-show="wizardStep === 2">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="技术名称（英文）" />
          </el-form-item>
          <el-form-item label="标题" prop="caption">
            <el-input v-model="form.caption" placeholder="显示标题" />
          </el-form-item>
        </div>

        <!-- Step 3: Properties -->
        <div v-show="wizardStep === 3">
          <el-divider content-position="left">属性</el-divider>

          <!-- Kind-specific fields -->
          <template v-if="form.kind === 'SPACE'">
            <el-form-item label="面积"><el-input-number v-model="form.area" :min="0" style="width:100%"/></el-form-item>
            <el-form-item label="排序号"><el-input-number v-model="form.sequence" :min="0" style="width:100%"/></el-form-item>
            <el-form-item label="客户端可见">
              <el-switch v-model="form.showInClient" active-text="是" inactive-text="否"/>
            </el-form-item>
          </template>
          <template v-if="form.kind === 'DEVICE'">
            <el-form-item label="型号"><el-input v-model="form.model" placeholder="设备型号"/></el-form-item>
            <el-form-item label="序列号"><el-input v-model="form.serialNumber" placeholder="序列号"/></el-form-item>
            <el-form-item label="厂家"><el-input v-model="form.vendor" placeholder="生产厂家"/></el-form-item>
          </template>
          <template v-if="form.kind === 'PROBE'">
            <el-form-item label="单位"><el-input v-model="form.unit" placeholder="如 ℃"/></el-form-item>
            <el-form-item label="采集间隔"><DurationInput v-model="form.detectInterval" /></el-form-item>
            <el-form-item label="存储间隔"><DurationInput v-model="form.savingInterval" /></el-form-item>
            <el-form-item label="告警条件"><el-input v-model="form.warnCondition" placeholder="如 v>80"/></el-form-item>
            <el-form-item label="最小值"><el-input-number v-model="form.minValue" style="width:100%"/></el-form-item>
            <el-form-item label="最大值"><el-input-number v-model="form.maxValue" style="width:100%"/></el-form-item>
            <el-form-item label="虚拟探头"><el-switch v-model="form.isVirtual"/></el-form-item>
            <el-form-item v-if="form.isVirtual" label="表达式" prop="expression" :rules="[{ validator: expressionValidator, trigger: 'blur' }]">
              <div style="display:flex;gap:4px;width:100%">
                <el-input v-model="form.expression" type="textarea" :rows="2" placeholder="如 #probe[101].value / #probe[102].value * 100" style="flex:1"/>
                <el-button size="small" @click="extractProbeIds" title="从表达式提取探头ID">提取</el-button>
              </div>
            </el-form-item>
            <el-form-item v-if="form.isVirtual" label="依赖探头">
              <el-select v-model="form.dependsOnIds" multiple filterable placeholder="选择依赖的探头" style="width:100%">
                <el-option v-for="p in probeList" :key="p.id" :label="p.id + ' — ' + (p.caption || p.name)" :value="p.id" />
              </el-select>
            </el-form-item>
          </template>
          <template v-if="form.kind === 'CONTROL'">
            <el-form-item label="单位"><el-input v-model="form.unit" placeholder="如 %"/></el-form-item>
            <el-form-item label="采集间隔"><DurationInput v-model="form.detectInterval" /></el-form-item>
            <el-form-item label="刷新延迟"><el-input-number v-model="form.refreshDelay" style="width:100%"/></el-form-item>
            <el-form-item label="最小值"><el-input-number v-model="form.minValue" style="width:100%"/></el-form-item>
            <el-form-item label="最大值"><el-input-number v-model="form.maxValue" style="width:100%"/></el-form-item>
          </template>

          <el-divider content-position="left" v-if="typeProperties.length > 0">扩展属性</el-divider>
          <el-form-item
            v-for="prop in typeProperties"
            :key="prop.name"
            :label="prop.description || prop.name"
            :required="prop.required"
          >
            <!-- YESNO: boolean switch (stored as string, displayed as boolean) -->
            <el-switch
              v-if="prop.viewType === 'YESNO'"
              :model-value="attrValues[prop.name] === 'true' || attrValues[prop.name] === true"
              @update:model-value="attrValues[prop.name] = String($event)"
              active-text="是" inactive-text="否"
            />
            <!-- SLIDER: numeric slider -->
            <el-slider
              v-else-if="prop.viewType === 'SLIDER'"
              :model-value="toNumber(attrValues[prop.name])"
              @update:model-value="attrValues[prop.name] = $event != null ? String($event) : ''"
              :min="prop.min ?? 0"
              :max="prop.max ?? 100"
              :step="prop.dataType === 'INT' ? 1 : 0.1"
              show-input
              style="width:100%"
            />
            <!-- PERCENT: 0-100 slider -->
            <el-slider
              v-else-if="prop.viewType === 'PERCENT'"
              :model-value="toNumber(attrValues[prop.name])"
              @update:model-value="attrValues[prop.name] = $event != null ? String($event) : ''"
              :min="0" :max="100" :step="1"
              show-input
              style="width:100%"
            />
            <!-- LIST: dropdown select -->
            <el-select
              v-else-if="prop.viewType === 'LIST'"
              v-model="attrValues[prop.name]"
              :placeholder="prop.defaultValue || '请选择'"
              style="width:100%"
            >
              <el-option
                v-for="opt in listOptions(prop)"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <!-- TEXTAREA: multi-line text -->
            <el-input
              v-else-if="prop.viewType === 'TEXTAREA'"
              v-model="attrValues[prop.name]"
              type="textarea" :rows="3"
              :placeholder="prop.defaultValue"
              :maxlength="prop.maxLength"
            />
            <!-- PASSWORD: masked input -->
            <el-input
              v-else-if="prop.viewType === 'PASSWORD'"
              v-model="attrValues[prop.name]"
              type="password" show-password
              :placeholder="prop.defaultValue"
              :maxlength="prop.maxLength"
            />
            <!-- INT / FLOAT without explicit viewType: numeric input -->
            <el-input-number
              v-else-if="prop.dataType === 'INT' || prop.dataType === 'FLOAT'"
              v-model="attrValues[prop.name]"
              :placeholder="prop.defaultValue"
              :min="prop.min"
              :max="prop.max"
              style="width:100%"
            />
            <!-- TEXTFIELD / fallback: text input -->
            <el-input
              v-else
              v-model="attrValues[prop.name]"
              :placeholder="prop.defaultValue"
              :maxlength="prop.maxLength"
            />
          </el-form-item>
        </div>

        <!-- Step 4: Confirm -->
        <div v-show="wizardStep === 4">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="类型">{{ kindLabel(form.kind) }}</el-descriptions-item>
            <el-descriptions-item v-if="form.typeName" label="类型名">{{ form.typeName }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ form.name }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ form.caption }}</el-descriptions-item>
            <el-descriptions-item v-if="form.unit" label="单位">{{ form.unit }}</el-descriptions-item>
            <el-descriptions-item v-if="form.detectInterval" label="采集间隔">{{ form.detectInterval }}</el-descriptions-item>
            <el-descriptions-item v-if="form.savingInterval" label="存储间隔">{{ form.savingInterval }}</el-descriptions-item>
            <el-descriptions-item v-if="form.warnCondition" label="告警条件">{{ form.warnCondition }}</el-descriptions-item>
            <el-descriptions-item v-if="form.isVirtual" label="虚拟探头">是</el-descriptions-item>
            <el-descriptions-item v-if="form.expression" label="表达式" :span="2">{{ form.expression }}</el-descriptions-item>
            <el-descriptions-item v-if="form.model" label="型号">{{ form.model }}</el-descriptions-item>
            <el-descriptions-item v-if="form.serialNumber" label="序列号">{{ form.serialNumber }}</el-descriptions-item>
            <el-descriptions-item v-if="form.vendor" label="厂家">{{ form.vendor }}</el-descriptions-item>
            <el-descriptions-item v-if="form.area" label="面积">{{ form.area }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-form>

      <template #footer>
        <el-button v-if="wizardStep > 1" @click="prevStep">上一步</el-button>
        <el-button v-if="wizardStep < 4" type="primary" @click="nextStep" :disabled="!canNextStep">下一步</el-button>
        <el-button v-if="wizardStep === 4" type="primary" :loading="submitting" @click="submitForm">确认创建</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- Edit Dialog (unchanged) -->
    <el-dialog
      v-if="dialogMode === 'edit'"
      v-model="dialogVisible"
      :title="'编辑资产 — ' + (form.caption || form.name)"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="技术名称（英文）" />
        </el-form-item>
        <el-form-item label="标题" prop="caption">
          <el-input v-model="form.caption" placeholder="显示标题" />
        </el-form-item>

        <el-divider content-position="left">属性</el-divider>

        <!-- Kind-specific fields -->
        <template v-if="detail.kind === 'SPACE'">
          <el-form-item label="面积"><el-input-number v-model="form.area" :min="0" style="width:100%"/></el-form-item>
          <el-form-item label="排序号"><el-input-number v-model="form.sequence" :min="0" style="width:100%"/></el-form-item>
          <el-form-item label="客户端可见">
            <el-switch v-model="form.showInClient" active-text="是" inactive-text="否"/>
          </el-form-item>
        </template>
        <template v-if="detail.kind === 'DEVICE'">
          <el-form-item label="型号"><el-input v-model="form.model" placeholder="设备型号"/></el-form-item>
          <el-form-item label="序列号"><el-input v-model="form.serialNumber" placeholder="序列号"/></el-form-item>
          <el-form-item label="厂家"><el-input v-model="form.vendor" placeholder="生产厂家"/></el-form-item>
        </template>
        <template v-if="detail.kind === 'PROBE'">
          <el-form-item label="单位"><el-input v-model="form.unit" placeholder="如 ℃"/></el-form-item>
          <el-form-item label="采集间隔"><DurationInput v-model="form.detectInterval" /></el-form-item>
          <el-form-item label="存储间隔"><DurationInput v-model="form.savingInterval" /></el-form-item>
          <el-form-item label="告警条件"><el-input v-model="form.warnCondition" placeholder="如 v>80"/></el-form-item>
          <el-form-item label="最小值"><el-input-number v-model="form.minValue" style="width:100%"/></el-form-item>
          <el-form-item label="最大值"><el-input-number v-model="form.maxValue" style="width:100%"/></el-form-item>
          <el-form-item label="虚拟探头"><el-switch v-model="form.isVirtual"/></el-form-item>
          <el-form-item v-if="form.isVirtual" label="表达式" prop="expression" :rules="[{ validator: expressionValidator, trigger: 'blur' }]">
            <div style="display:flex;gap:4px;width:100%">
              <el-input v-model="form.expression" type="textarea" :rows="2" placeholder="如 #probe[101].value / #probe[102].value * 100" style="flex:1"/>
              <el-button size="small" @click="extractProbeIds" title="从表达式提取探头ID">提取</el-button>
            </div>
          </el-form-item>
          <el-form-item v-if="form.isVirtual" label="依赖探头">
            <el-select v-model="form.dependsOnIds" multiple filterable placeholder="选择依赖的探头" style="width:100%">
              <el-option v-for="p in probeList" :key="p.id" :label="p.id + ' — ' + (p.caption || p.name)" :value="p.id" />
            </el-select>
          </el-form-item>
        </template>
        <template v-if="detail.kind === 'CONTROL'">
          <el-form-item label="单位"><el-input v-model="form.unit" placeholder="如 %"/></el-form-item>
          <el-form-item label="采集间隔"><DurationInput v-model="form.detectInterval" /></el-form-item>
          <el-form-item label="刷新延迟"><el-input-number v-model="form.refreshDelay" style="width:100%"/></el-form-item>
          <el-form-item label="最小值"><el-input-number v-model="form.minValue" style="width:100%"/></el-form-item>
          <el-form-item label="最大值"><el-input-number v-model="form.maxValue" style="width:100%"/></el-form-item>
        </template>

        <el-divider content-position="left" v-if="typeProperties.length > 0">扩展属性</el-divider>
        <el-form-item
          v-for="prop in typeProperties"
          :key="prop.name"
          :label="prop.description || prop.name"
          :required="prop.required"
        >
          <!-- YESNO: boolean switch (stored as string, displayed as boolean) -->
          <el-switch
            v-if="prop.viewType === 'YESNO'"
            :model-value="attrValues[prop.name] === 'true' || attrValues[prop.name] === true"
            @update:model-value="attrValues[prop.name] = String($event)"
            active-text="是" inactive-text="否"
          />
          <!-- SLIDER: numeric slider -->
          <el-slider
            v-else-if="prop.viewType === 'SLIDER'"
            :model-value="toNumber(attrValues[prop.name])"
            @update:model-value="attrValues[prop.name] = $event != null ? String($event) : ''"
            :min="prop.min ?? 0"
            :max="prop.max ?? 100"
            :step="prop.dataType === 'INT' ? 1 : 0.1"
            show-input
            style="width:100%"
          />
          <!-- PERCENT: 0-100 slider -->
          <el-slider
            v-else-if="prop.viewType === 'PERCENT'"
            :model-value="toNumber(attrValues[prop.name])"
            @update:model-value="attrValues[prop.name] = $event != null ? String($event) : ''"
            :min="0" :max="100" :step="1"
            show-input
            style="width:100%"
          />
          <!-- LIST: dropdown select -->
          <el-select
            v-else-if="prop.viewType === 'LIST'"
            v-model="attrValues[prop.name]"
            :placeholder="prop.defaultValue || '请选择'"
            style="width:100%"
          >
            <el-option
              v-for="opt in listOptions(prop)"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <!-- TEXTAREA: multi-line text -->
          <el-input
            v-else-if="prop.viewType === 'TEXTAREA'"
            v-model="attrValues[prop.name]"
            type="textarea" :rows="3"
            :placeholder="prop.defaultValue"
            :maxlength="prop.maxLength"
          />
          <!-- PASSWORD: masked input -->
          <el-input
            v-else-if="prop.viewType === 'PASSWORD'"
            v-model="attrValues[prop.name]"
            type="password" show-password
            :placeholder="prop.defaultValue"
            :maxlength="prop.maxLength"
          />
          <!-- INT / FLOAT without explicit viewType: numeric input -->
          <el-input-number
            v-else-if="prop.dataType === 'INT' || prop.dataType === 'FLOAT'"
            v-model="attrValues[prop.name]"
            :placeholder="prop.defaultValue"
            :min="prop.min"
            :max="prop.max"
            style="width:100%"
          />
          <!-- TEXTFIELD / fallback: text input -->
          <el-input
            v-else
            v-model="attrValues[prop.name]"
            :placeholder="prop.defaultValue"
            :maxlength="prop.maxLength"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- History Detail Dialog -->
    <el-dialog v-model="historyDialogVisible"
      :title="'历史数据 — ' + (detail.caption || detail.name)"
      width="800px" destroy-on-close @opened="loadDetailTrend">
      <div style="display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap;">
        <el-button v-for="r in timeRanges" :key="r.label" size="small"
          :type="selectedRange === r.label ? 'primary' : ''"
          @click="selectTimeRange(r)">{{ r.label }}</el-button>
        <el-date-picker v-model="customRange" type="datetimerange" range-separator="至"
          start-placeholder="开始" end-placeholder="结束" size="small"
          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss"
          @change="onCustomRange" />
      </div>
      <div style="display:flex;gap:4px;margin-bottom:8px;">
        <el-button v-for="p in detailPeriods" :key="p.key" size="small"
          :type="detailGranularity === p.key ? 'primary' : ''"
          @click="switchDetailGranularity(p.key)">{{ p.label }}</el-button>
      </div>
      <TrendChart
        :data-points="detailData"
        :intraday-points="detailIntradayPoints"
        :avg5="detailAvg5" :avg10="detailAvg10" :avg20="detailAvg20"
        :summary="detailSummary"
        :granularity="detailGranularity"
        :unit="detail?.unit || ''"
        :loading="detailLoading"
        @data-point-click="onDetailCandleClick"
        @popout="onDetailPopout"
      />
    </el-dialog>

    <ConfirmDialog
      :visible="confirmDanger.dialogVisible"
      :title="confirmDanger.dialogTitle"
      :message="confirmDanger.dialogMessage"
      :require-input="confirmDanger.requireInput"
      :expected-input="confirmDanger.expectedInput"
      :input-placeholder="confirmDanger.inputPlaceholder"
      :impact="confirmDanger.dialogImpact"
      @confirm="onDangerConfirm"
      @cancel="confirmDanger.handleCancel()"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch, onBeforeUnmount, onMounted } from 'vue'
import Skeleton from '@/components/Skeleton/index.vue'
import { useRouter, useRoute } from 'vue-router'
import TreePanel from '@/components/TreePanel'
import ControlCommandInput from '@/components/control/ControlCommandInput.vue'
import DurationInput from '@/components/DurationInput/index.vue'
import InlineEdit from '@/components/InlineEdit/index.vue'
import ConfirmDialog from '@/components/ConfirmDialog/index.vue'
import TrendChart from '../statistics/components/TrendChart.vue'
import { getTrendDefault, getTrendData } from '@/api/iot/trend'
import { getDrillDownRange } from '../statistics/composables/useTrendDrillDown'
import { useWebSocketStore } from '@/stores/websocket'
import { useFormDefaults } from '@/composables/useFormDefaults'
import { useOperationStatus } from '@/composables/useOperationStatus'
import { useConfirmDanger } from '@/composables/useConfirmDanger'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import {
  getAssetTree, getAsset, getAssetTypes, getTypeProperties,
  createAsset, updateAsset, deleteAsset, listAssets,
  detectAsset, executeControl,
  startAsset, stopAsset, disableAsset, enableAsset,
  batchStart, batchStop, batchEnable, batchDisable, batchDelete
} from '@/api/iot/asset'
import { stateTagType, kindLabel, kindTagType, parseTime } from '@/utils/formatters'
import { showSystarError } from '@/utils/errorHandler'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const treeRef = ref(null)
const assetTree = ref({})
const selectedNode = ref(null)
const detail = ref({})
const selection = ref([])
const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)
const availableTypes = ref([])
const allTypes = ref({})

const form = ref({
  kind: '', name: '', caption: '', typeName: '',
  area: 0, sequence: 0, showInClient: true,
  model: '', serialNumber: '', vendor: '',
  unit: '', detectInterval: '', savingInterval: '', warnCondition: '',
  minValue: null, maxValue: null, refreshDelay: null,
  isVirtual: false, expression: '', dependsOnIds: []
})

const typeProperties = ref([])   // properties from AssetType definition
const attrValues = ref({})       // current values keyed by property name
const probeList   = ref([])      // available probes for VirtualProbe dependsOn selector

// Wizard state for create dialog
const wizardStep = ref(1)
const formMemory = useFormDefaults('asset-create')
const PERSIST_FIELDS = ['unit', 'detectInterval', 'savingInterval']
const HTTP_CONFLICT = 409
const DEFAULT_OP_TIMEOUT_MS = 30000
const nameUniquenessValidator = async (_rule, value, callback) => {
  if (!value) return callback()
  try {
    const res = await listAssets({ name: value, kind: form.value.kind })
    const existing = res.data || []
    const isOwnName = dialogMode.value === 'edit' && detail.value?.name === value
    if (existing.length > 0 && !isOwnName) {
      callback(new Error('名称已被使用'))
    } else {
      callback()
    }
  } catch {
    callback()
  }
}

const expressionValidator = (_rule, value, callback) => {
  if (!value) return callback()
  // Check bracket matching
  let depth = 0
  for (const ch of value) {
    if (ch === '(') depth++
    if (ch === ')') depth--
    if (depth < 0) return callback(new Error('括号不匹配'))
  }
  if (depth !== 0) return callback(new Error('括号不匹配'))
  // Check #probe[id] format
  const probeRefs = value.matchAll(/#probe\[([^\]]*)\]/g)
  for (const match of probeRefs) {
    if (!/^\d+$/.test(match[1])) {
      return callback(new Error(`无效的探头引用: #probe[${match[1]}]`))
    }
  }
  callback()
}

const formRules = {
  kind: [{ required: true, message: '请选择类型', trigger: 'change' }],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { validator: nameUniquenessValidator, trigger: 'blur' }
  ],
  caption: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  typeName: [{ required: true, message: '请选择类型名', trigger: 'change' }]
}

const treeData = computed(() =>
  (assetTree.value && assetTree.value.id !== undefined) ? [assetTree.value] : []
)

const childNodes = computed(() => selectedNode.value?.children || [])
const childCount = computed(() => childNodes.value.length)
const isCompound = computed(() => ['SPACE', 'DEVICE'].includes(selectedNode.value?.kind))

const showAlarmCard = computed(() =>
  ['DEVICE', 'PROBE', 'CONTROL'].includes(detail.value?.kind)
)
const showLinkageCard = computed(() =>
  ['PROBE', 'CONTROL'].includes(detail.value?.kind)
)

const displayPath = computed(() => {
  const path = detail.value?.path || ''
  return path.replace(/^->/, '').replaceAll('->', ' → ') || '-'
})

const breadcrumbs = computed(() => {
  const path = detail.value?.path || ''
  // path format: "->root->building_A->machine_room" — strip leading -> then split
  const clean = path.replace(/^->/, '')
  const segs = clean.split('->').filter(s => s)
  return segs.map(name => {
    const found = findNodeByName(assetTree.value, name)
    return { id: found?.id, name, caption: found?.caption || name }
  })
})

const siblingNodes = computed(() => {
  if (!detail.value?.id) return []
  const parentId = detail.value.parentId
  if (!parentId) return []
  const parent = findNodeById(assetTree.value, parentId)
  return (parent?.children || []).filter(c => c.id !== detail.value.id)
})

function findNodeById(root, id) {
  if (!root) return null
  if (root.id === id) return root
  if (root.children) {
    for (const c of root.children) {
      const r = findNodeById(c, id)
      if (r) return r
    }
  }
  return null
}

function findNodeByName(root, name) {
  if (!root) return null
  if (root.name === name) return root
  if (root.children) {
    for (const c of root.children) {
      const r = findNodeByName(c, name)
      if (r) return r
    }
  }
  return null
}

const filteredChildren = computed(() => childNodes.value)

const canAddChild = computed(() => ['SPACE', 'DEVICE'].includes(selectedNode.value?.kind))
const canEnable = computed(() => detail.value && !detail.value.enabled)
const canDisable = computed(() => detail.value?.enabled)
const canStartStop = computed(() => ['SERVICE', 'PROBE', 'CONTROL'].includes(detail.value?.kind))

const ATTR_LABELS = {
  unit: '单位', dataType: '数据类型', catalog: '分类',
  minValue: '最小值', maxValue: '最大值', warnCondition: '告警条件',
  transform: '转换', model: '型号', serialNumber: '序列号',
  vendor: '厂家', area: '面积', sequence: '排序', refreshDelay: '刷新延迟(ms)',
  detectInterval: '采集间隔', savingInterval: '存储间隔',
  isVirtual: '虚拟探头', expression: '表达式', dependsOnIds: '依赖探头'
}
function attrLabel(key) { return ATTR_LABELS[key] || key }

function toNumber(val) {
  if (val == null || val === '') return undefined
  const n = Number(val)
  return isNaN(n) ? undefined : n
}

function listOptions(prop) {
  // TODO: load from CodeDict or type definition when available
  return []
}

function extractProbeIds() {
  const expr = form.value.expression
  if (!expr) return
  const matches = expr.matchAll(/#probe\[(\d+)\]\.value/g)
  const ids = [...new Set([...matches].map(m => parseInt(m[1])))]
  if (ids.length > 0) {
    form.value.dependsOnIds = ids
  }
}

const canNextStep = computed(() => {
  if (wizardStep.value === 1) return !!(form.value.kind && form.value.typeName)
  if (wizardStep.value === 2) return !!(form.value.name && form.value.caption)
  return true
})

function nextStep() {
  if (!canNextStep.value) return
  if (wizardStep.value === 2) {
    applySmartDefaults()
  }
  wizardStep.value++
}

function prevStep() {
  if (wizardStep.value > 1) wizardStep.value--
}

function applySmartDefaults() {
  const parent = {}
  if (detail.value?.unit) parent.unit = detail.value.unit
  if (detail.value?.detectInterval) parent.detectInterval = detail.value.detectInterval
  if (detail.value?.savingInterval) parent.savingInterval = detail.value.savingInterval

  const typeDefs = {}
  for (const p of typeProperties.value) {
    if (p.defaultValue !== undefined && p.defaultValue !== '') {
      typeDefs[p.name] = p.defaultValue
    }
  }

  const merged = formMemory.defaults(parent, typeDefs)
  if (!form.value.unit && merged.unit) form.value.unit = merged.unit
  if (!form.value.detectInterval && merged.detectInterval) form.value.detectInterval = merged.detectInterval
  if (!form.value.savingInterval && merged.savingInterval) form.value.savingInterval = merged.savingInterval
}

function loadProbeList() {
  listAssets({ kind: 'PROBE' }).then(res => {
    probeList.value = (res.data || []).map(p => ({
      id: p.id, name: p.name, caption: p.caption || p.name
    }))
  }).catch(() => { probeList.value = [] })
}

function stateCount(state) {
  return childNodes.value.filter(c => c.state === state).length
}

const opStatus = useOperationStatus({ timeoutMs: DEFAULT_OP_TIMEOUT_MS })
const refreshing = computed(() => opStatus.isLoading(detail.value?.id))
const wsStore = useWebSocketStore()
const executing = ref(null)
// reactive() unwraps the composable's nested refs so template bindings
// (`confirmDanger.dialogVisible` etc.) pass plain values, not Ref objects.
const confirmDanger = reactive(useConfirmDanger())
const pendingDeleteId = ref(null)
const pendingBatchAction = ref(null)
const pendingBatchIds = ref([])
const controlCommand = ref('')
const controlResult = ref('')
const historyDialogVisible = ref(false)
const selectedRange = ref('1日')
const customRange = ref(null)

// Auto-refresh for child node state changes
const childRefresh = useAutoRefresh(
  async () => {
    if (!detail.value?.id) return []
    const res = await getAsset(detail.value.id)
    if (detail.value?.id === res.data?.id) {
      Object.assign(detail.value, res.data)
    }
    return res.data?.children || []
  },
  { interval: 15000, idField: 'id', compareField: 'state', highlightDuration: 3000 }
)
function childRowClassName({ row }) {
  return childRefresh.highlightedIds.value.has(row.id) ? 'row-changed' : ''
}

let treeRefreshTimer = null

// Mini chart trend state
const miniTrendRef = ref(null)
const miniDataPoints = ref([])
const miniIntradayPoints = ref([])
const miniSummary = ref(null)
const miniGranularity = ref('INTRADAY')
const miniLoading = ref(false)

// Detail dialog trend state
const detailData = ref([])
const detailIntradayPoints = ref([])
const detailAvg5 = ref([])
const detailAvg10 = ref([])
const detailAvg20 = ref([])
const detailSummary = ref(null)
const detailGranularity = ref('HOUR')
const detailLoading = ref(false)

// Granularity tabs for detail dialog
const detailPeriods = [
  { key: 'INTRADAY', label: '分时' },
  { key: 'HOUR', label: '时' },
  { key: 'DAY', label: '日' },
  { key: 'WEEK', label: '周' },
  { key: 'MONTH', label: '月' }
]

function formatDateTime(d) {
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// Time range to granularity mapping
const timeRangeGranularityMap = {
  '1日': 'INTRADAY',
  '1周': 'HOUR',
  '1月': 'DAY',
  '1年': 'WEEK'
}
const timeRanges = [
  { label: '1日', unit: 'day', value: 1 },
  { label: '1周', unit: 'day', value: 7 },
  { label: '1月', unit: 'month', value: 1 },
  { label: '1年', unit: 'year', value: 1 }
]

function handleNodeClick(data) {
  if (detail.value?.id) opStatus.clearStatus(detail.value.id)
  childRefresh.stop()
  selectedNode.value = data
  selection.value = []
  clearMiniChartData()
  router.replace({ query: { node: data.id } })
  getAsset(data.id).then(res => {
    detail.value = res.data
    nextTick(() => {
      loadMiniTrend()
      if (res.data?.children?.length) childRefresh.start()
    })
  }).catch(err => {
    showSystarError(err, '加载资产详情失败')
  })
}

/**
 * Load adaptive default trend data for the mini chart.
 */
function loadMiniTrend() {
  const isProbeOrControl = detail.value?.kind === 'PROBE' || detail.value?.kind === 'CONTROL'
  if (!isProbeOrControl || !detail.value?.id) return
  miniLoading.value = true
  getTrendDefault({
    monitorId: detail.value.id,
    monitorKind: detail.value.kind
  }).then(res => {
    const payload = res.data || res
    miniGranularity.value = payload.granularity || 'INTRADAY'
    miniDataPoints.value = payload.dataPoints || []
    miniIntradayPoints.value = payload.intradayPoints || []
    miniSummary.value = payload.summary || null
  }).catch(() => {
    clearMiniChartData()
  }).finally(() => {
    miniLoading.value = false
  })
}

function clearMiniChartData() {
  miniDataPoints.value = []
  miniIntradayPoints.value = []
  miniSummary.value = null
  miniGranularity.value = 'INTRADAY'
}

async function handleRefresh() {
  if (!detail.value?.id || opStatus.isLoading(detail.value.id)) return
  const id = detail.value.id
  const timeoutMs = (detail.value?.detectTimeoutMs || 30000) + 2000
  opStatus.watchForCompletion(id, () => wsStore.probeValues[detail.value?.id], timeoutMs)
  try {
    await detectAsset(id)
    ElMessage.success('检测请求已提交，等待结果...')
  } catch (e) {
    opStatus.clearStatus(id)
    if (e?.code === HTTP_CONFLICT) {
      ElMessage.warning('该监测器正在检测中，请稍后重试')
    } else {
      showSystarError(e, '刷新失败')
    }
  }
}

// Watch for WS value to update detail on detect completion
watch(() => wsStore.probeValues[detail.value?.id], (msg) => {
  if (!msg || !opStatus.isLoading(detail.value?.id)) return
  const currentId = detail.value?.id
  if (!currentId) return
  getAsset(currentId).then(res => {
    if (detail.value?.id === currentId) {
      detail.value = res.data
      nextTick(() => loadMiniTrend())
    }
  }).catch(e => showSystarError(e, '状态更新失败'))
})

// Cancel pending watch when switching monitors
watch(() => detail.value?.id, (newId, oldId) => {
  if (oldId && newId !== oldId) opStatus.clearStatus(oldId)
})

// Show warning on detect timeout
watch(() => detail.value?.id ? opStatus.getStatus(detail.value.id) : 'idle', (status) => {
  if (status === 'timeout') {
    ElMessage.warning('检测超时，请稍后重试')
  }
})

async function handleControlExecute(cmd) {
  if (!cmd || executing.value) return
  executing.value = cmd
  try {
    const res = await executeControl(detail.value.id, cmd)
    controlResult.value = res.data != null && typeof res.data === 'object' ? JSON.stringify(res.data) : res.data || res.msg || 'OK'
    ElMessage.success('控制命令已执行: ' + cmd)
  } catch (e) {
    controlResult.value = '执行失败'
    if (e?.code === HTTP_CONFLICT) {
      ElMessage.warning('该操控器正在检测中，请稍后重试')
    } else {
      showSystarError(e, '执行失败')
    }
  } finally {
    executing.value = null
  }
}

// === History Detail Dialog ===

function openHistoryDialog() {
  selectedRange.value = '1日'
  customRange.value = null
  detailGranularity.value = 'INTRADAY'
  clearDetailChartData()
  historyDialogVisible.value = true
}

function selectTimeRange(r) {
  selectedRange.value = r.label
  customRange.value = null
  // Map time range to appropriate granularity
  const mappedGranularity = timeRangeGranularityMap[r.label]
  if (mappedGranularity) {
    detailGranularity.value = mappedGranularity
  }
  const end = new Date()
  const start = new Date(end)
  if (r.unit === 'day') start.setDate(start.getDate() - r.value)
  else if (r.unit === 'month') start.setMonth(start.getMonth() - r.value)
  else if (r.unit === 'year') start.setFullYear(start.getFullYear() - r.value)
  fetchDetailTrend(start, end)
}

function onCustomRange(val) {
  if (!val) return
  selectedRange.value = ''
  fetchDetailTrend(new Date(val[0]), new Date(val[1]))
}

function fetchDetailTrend(start, end) {
  if (!detail.value?.id) return
  detailLoading.value = true
  getTrendData({
    monitorId: detail.value.id,
    monitorKind: detail.value.kind,
    startTime: formatDateTime(start),
    endTime: formatDateTime(end),
    granularity: detailGranularity.value
  }).then(res => {
    const payload = res.data || res
    applyDetailResponseData(payload)
  }).catch(() => {
    clearDetailChartData()
  }).finally(() => {
    detailLoading.value = false
  })
}

function loadDetailTrend() {
  // Called when dialog opens — load default for the selected time range
  selectTimeRange(timeRanges[0])
}

function applyDetailResponseData(payload) {
  if (!payload) { clearDetailChartData(); return }
  detailGranularity.value = payload.granularity || detailGranularity.value
  detailData.value = payload.dataPoints || []
  detailIntradayPoints.value = payload.intradayPoints || []
  detailAvg5.value = payload.avg5 || []
  detailAvg10.value = payload.avg10 || []
  detailAvg20.value = payload.avg20 || []
  detailSummary.value = payload.summary || null
}

function clearDetailChartData() {
  detailData.value = []
  detailIntradayPoints.value = []
  detailAvg5.value = []
  detailAvg10.value = []
  detailAvg20.value = []
  detailSummary.value = null
}

function switchDetailGranularity(key) {
  detailGranularity.value = key
  // Reload with current date range
  const end = new Date()
  const start = new Date(end)
  // Determine appropriate date range based on granularity
  const granToDays = { INTRADAY: 1, HOUR: 7, DAY: 30, WEEK: 365, MONTH: 3650 }
  const days = granToDays[key] || 30
  start.setDate(start.getDate() - days)
  fetchDetailTrend(start, end)
}

function onDetailCandleClick({ time, granularity: clickedGranularity }) {
  const range = getDrillDownRange(time, clickedGranularity)
  if (!range) return
  detailGranularity.value = range.granularity
  fetchDetailTrend(range.startTime, range.endTime)
}

function onDetailPopout() {
  if (!detail.value?.id) return
  const q = new URLSearchParams({ monitorId: detail.value.id, monitorKind: detail.value.kind || 'PROBE' })
  window.open(`/trend-standalone?${q}`, '_blank', 'width=1200,height=800')
}

onMounted(() => {
  wsStore.connect()
  loadTypes()
  treeRefreshTimer = setInterval(() => {
    getAssetTree().then(res => { assetTree.value = res.data || {} }).catch(e => console.warn('Tree refresh failed:', e?.message || e))
  }, 30000)
})

onBeforeUnmount(() => {
  opStatus.clearAll()
  childRefresh.stop()
  if (treeRefreshTimer) { clearInterval(treeRefreshTimer); treeRefreshTimer = null }
})

function handleChildRowClick(row) {
  treeRef.value?.setCurrentKey(row.id)
  handleNodeClick(row)
}

function handleSelectionChange(val) {
  selection.value = val
}

function navigateTo(id) {
  treeRef.value?.setCurrentKey(id)
  router.replace({ query: { node: id } })
}

// === CRUD ===

function openCreateDialog() {
  wizardStep.value = 1
  dialogMode.value = 'create'
  form.value = { kind: '', name: '', caption: '', typeName: '',
    area: 0, sequence: 0, showInClient: true,
    model: '', serialNumber: '', vendor: '',
    unit: '', detectInterval: '', savingInterval: '', warnCondition: '',
    minValue: null, maxValue: null, refreshDelay: null,
    isVirtual: false, expression: '', dependsOnIds: []
  }

  typeProperties.value = []
  attrValues.value = {}
  availableTypes.value = []
  loadProbeList()
  dialogVisible.value = true
}

function openEditDialog() {
  dialogMode.value = 'edit'
  const depsStr = detail.value.dependsOn || ''
  const depsIds = depsStr ? depsStr.split(',').map(s => parseInt(s.trim())).filter(n => !isNaN(n)) : []
  form.value = {
    kind: detail.value.kind,
    name: detail.value.name || '',
    caption: detail.value.caption || '',
    typeName: '',
    area: 0, sequence: 0, showInClient: true,
    model: '', serialNumber: '', vendor: '',
    unit: detail.value.unit || '',
    detectInterval: '', savingInterval: '', warnCondition: '',
    minValue: detail.value.minValue ?? null,
    maxValue: detail.value.maxValue ?? null,
    refreshDelay: null,
    isVirtual: detail.value.isVirtual || false, expression: detail.value.expression || '', dependsOnIds: depsIds
  }
  attrValues.value = detail.value.attributes ? { ...detail.value.attributes } : {}
  loadTypeProperties()
  loadProbeList()
  dialogVisible.value = true
}

function onKindChange(kind) {
  availableTypes.value = allTypes.value[kind] || []
  form.value.typeName = ''
  typeProperties.value = []
}

function onTypeNameChange(typeName) {
  if (!typeName || !form.value.kind) { typeProperties.value = []; return }
  getTypeProperties(form.value.kind, typeName).then(res => {
    typeProperties.value = res.data || []
    // Pre-fill defaults for create mode (no existing values)
    attrValues.value = {}
    for (const p of res.data || []) {
      const raw = p.defaultValue !== undefined ? p.defaultValue : ''
      attrValues.value[p.name] = (p.dataType === 'INT' || p.dataType === 'FLOAT') && raw !== ''
        ? Number(raw) : raw
    }
  }).catch(() => { typeProperties.value = [] })
}

function loadTypes() {
  getAssetTypes().then(res => {
    allTypes.value = res.data || {}
    if (form.value.kind) availableTypes.value = allTypes.value[form.value.kind] || []
  }).catch(e => showSystarError(e, '加载类型配置失败'))
}

function loadTypeProperties() {
  const kind = form.value.kind || detail.value?.kind
  if (!kind || kind === 'SPACE' || kind === 'DEVICE') {
    typeProperties.value = []
    return
  }
  const tn = detail.value?.kind === kind ? detail.value?.typeName : null
  if (!tn) { typeProperties.value = []; return }
  getTypeProperties(kind, tn).then(res => {
    typeProperties.value = res.data || []
    // Pre-fill defaults for properties without existing values
    for (const p of res.data || []) {
      if (attrValues.value[p.name] === undefined || attrValues.value[p.name] === null) {
        const raw = p.defaultValue !== undefined ? p.defaultValue : ''
        attrValues.value[p.name] = (p.dataType === 'INT' || p.dataType === 'FLOAT') && raw !== ''
          ? Number(raw) : raw
      } else if (p.dataType === 'INT' || p.dataType === 'FLOAT') {
        // Ensure existing values are numeric for el-input-number
        const v = attrValues.value[p.name]
        attrValues.value[p.name] = v !== '' && v !== null && v !== undefined ? Number(v) : v
      }
    }
  }).catch(() => { typeProperties.value = [] })
}

function resetForm() {
  wizardStep.value = 1
  form.value = { kind: '', name: '', caption: '', typeName: '',
    area: 0, sequence: 0, showInClient: true,
    model: '', serialNumber: '', vendor: '',
    unit: '', detectInterval: '', savingInterval: '', warnCondition: '',
    minValue: null, maxValue: null, refreshDelay: null,
    isVirtual: false, expression: '', dependsOnIds: []
  }

  typeProperties.value = []
  attrValues.value = {}
}

function buildProperties() {
  const p = {}
  const kind = form.value.kind || detail.value?.kind
  if (kind === 'SPACE') {
    if (form.value.area) p.area = form.value.area
    if (form.value.sequence) p.sequence = form.value.sequence
    p.showInClient = form.value.showInClient ? 1 : 0
  }
  if (kind === 'DEVICE') {
    if (form.value.model) p.model = form.value.model
    if (form.value.serialNumber) p.serialNumber = form.value.serialNumber
    if (form.value.vendor) p.vendor = form.value.vendor
  }
  if (kind === 'PROBE') {
    if (form.value.unit) p.unit = form.value.unit
    if (form.value.detectInterval) p.detectInterval = form.value.detectInterval
    if (form.value.savingInterval) p.savingInterval = form.value.savingInterval
    if (form.value.warnCondition) p.warnCondition = form.value.warnCondition
    if (form.value.minValue !== null) p.minValue = form.value.minValue
    if (form.value.maxValue !== null) p.maxValue = form.value.maxValue
    if (form.value.isVirtual) {
      p.isVirtual = 1
      if (form.value.expression) p.expression = form.value.expression
      if (form.value.dependsOnIds.length > 0) p.dependsOn = form.value.dependsOnIds.join(',')
    } else {
      p.isVirtual = 0
      p.expression = ''
      p.dependsOn = ''
    }
  }
  if (kind === 'CONTROL') {
    if (form.value.unit) p.unit = form.value.unit
    if (form.value.detectInterval) p.detectInterval = form.value.detectInterval
    if (form.value.refreshDelay !== null) p.refreshDelay = form.value.refreshDelay
    if (form.value.minValue !== null) p.minValue = form.value.minValue
    if (form.value.maxValue !== null) p.maxValue = form.value.maxValue
  }
  return Object.keys(p).length > 0 ? p : null
}

function buildAttributes() {
  const attrs = {}
  for (const k of Object.keys(attrValues.value)) {
    const v = attrValues.value[k]
    if (v !== null && v !== undefined && v !== '') attrs[k] = String(v)
  }
  return Object.keys(attrs).length > 0 ? attrs : null
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch { return }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createAsset({
        kind: form.value.kind,
        parentId: detail.value.id,
        name: form.value.name,
        caption: form.value.caption,
        typeName: form.value.typeName || null,
        properties: buildProperties(),
        attributes: buildAttributes()
      })
      formMemory.saveDefaults(form.value, PERSIST_FIELDS)
      ElMessage.success('创建成功')
    } else {
      await updateAsset(detail.value.id, {
        name: form.value.name,
        caption: form.value.caption,
        properties: buildProperties(),
        attributes: buildAttributes()
      })
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    getTree()
  } catch (e) {
    showSystarError(e, '操作失败')
  } finally {
    submitting.value = false
  }
}

// === Delete ===

function handleDelete() {
  const name = detail.value.caption || detail.value.name
  pendingDeleteId.value = detail.value.id
  confirmDanger.confirm({
    title: '删除确认',
    message: `确认删除「${name}」吗？删除后无法恢复。`,
    requireInput: true,
    expectedInput: name.substring(0, 4),
    inputPlaceholder: `请输入「${name.substring(0, 4)}」以确认删除`,
  })
}

async function doDelete() {
  const targetId = pendingDeleteId.value
  pendingDeleteId.value = null
  if (!targetId || targetId !== detail.value?.id) {
    ElMessage.warning('资产已变更，操作已取消')
    return
  }
  try {
    await deleteAsset(targetId)
    ElMessage.success('已删除')
    getTree()
    selectedNode.value = null
    detail.value = {}
  } catch (e) {
    showSystarError(e, '删除失败')
  }
}

// === Runtime operations ===

async function handleOperate(action) {
  if (!detail.value?.id || opStatus.isLoading(detail.value.id)) return
  const apiMap = { start: startAsset, stop: stopAsset, enable: enableAsset, disable: disableAsset }
  const labelMap = { start: '启动', stop: '停止', enable: '启用', disable: '禁用' }
  try {
    await opStatus.execute(detail.value.id, () => apiMap[action](detail.value.id))
    ElMessage.success(`${labelMap[action]}成功`)
    getAsset(detail.value.id).then(res => { detail.value = res.data }).catch(e => showSystarError(e, '刷新资产详情失败'))
  } catch (e) {
    showSystarError(e, `${labelMap[action]}失败`)
  }
}

async function batchOperate(action) {
  const ids = selection.value.map(r => r.id)
  if (!ids.length) return
  const labelMap = { start: '启动', stop: '停止', delete: '删除', enable: '启用', disable: '禁用' }

  if (action === 'delete') {
    const names = selection.value.map(r => r.caption || r.name).join('、')
    pendingBatchIds.value = [...ids]
    pendingBatchAction.value = action
    confirmDanger.confirm({
      title: '批量删除确认',
      message: `确认批量删除选中的 ${ids.length} 个资产吗？`,
      impact: `将删除：${names}`,
      requireInput: true,
      expectedInput: `${ids.length}`,
      inputPlaceholder: `请输入「${ids.length}」以确认批量删除`,
    })
    return
  }

  try {
    const apiMap = { start: batchStart, stop: batchStop, enable: batchEnable, disable: batchDisable }
    const res = await apiMap[action](ids)
    const data = res.data
    const ok = data.success?.length || 0
    const fail = Object.keys(data.failed || {}).length
    ElMessage.success(`${labelMap[action]}: 成功 ${ok}${fail > 0 ? ', 失败 ' + fail : ''}`)
  } catch (e) {
    showSystarError(e, labelMap[action] + '失败')
  }
  selection.value = []
  getAsset(detail.value.id).then(res => { detail.value = res.data })
}

async function doBatchDelete() {
  const ids = pendingBatchIds.value
  pendingBatchIds.value = []
  pendingBatchAction.value = null
  if (!ids.length) return
  try {
    const res = await batchDelete(ids)
    const data = res.data
    const ok = data.success?.length || 0
    const fail = Object.keys(data.failed || {}).length
    ElMessage.success(`批量删除: 成功 ${ok}${fail > 0 ? ', 失败 ' + fail : ''}`)
  } catch (e) {
    showSystarError(e, '批量删除失败')
  }
  selection.value = []
  getAsset(detail.value.id).then(res => { detail.value = res.data }).catch(e => showSystarError(e, '刷新资产详情失败'))
}

async function onDangerConfirm() {
  confirmDanger.handleConfirm()
  if (pendingBatchAction.value === 'delete') {
    await doBatchDelete()
  } else {
    await doDelete()
  }
}

// === Inline edit ===

const TOP_LEVEL_FIELDS = ['name', 'caption']

async function updateField(field, value) {
  if (!detail.value?.id) return
  const payload = TOP_LEVEL_FIELDS.includes(field)
    ? { [field]: value }
    : { properties: { [field]: value } }
  try {
    await updateAsset(detail.value.id, payload)
    detail.value[field] = value
    ElMessage.success('已更新')
  } catch (e) {
    showSystarError(e, '更新失败')
  }
}

// === Tree ===

function getTree() {
  getAssetTree().then(res => {
    assetTree.value = res.data || {}
    const root = assetTree.value
    if (root && root.id !== undefined) {
      const targetId = Number(route.query.node) || root.id
      const targetNode = findNodeById(root, targetId)
      nextTick(() => {
        treeRef.value?.setCurrentKey(targetId)
        handleNodeClick(targetNode || root)
      })
    }
  }).catch(err => {
    showSystarError(err, '加载资产树失败')
  })
}

// Watch route changes (browser forward/back)
watch(() => route.query.node, (newId) => {
  if (!newId || !assetTree.value) return
  const id = Number(newId)
  if (isNaN(id) || selectedNode.value?.id === id) return
  const target = findNodeById(assetTree.value, id)
  if (target) {
    treeRef.value?.setCurrentKey(id)
    handleNodeClick(target)
  }
})

getTree()
</script>

<style scoped>
.breadcrumb-bar {
  padding: 8px 0;
  font-size: 13px;
  color: #666;
}
.breadcrumb-seg { display: inline-flex; align-items: center; }
.breadcrumb-seg.clickable { cursor: pointer; color: #409eff; }
.breadcrumb-seg.clickable:hover { text-decoration: underline; }
.breadcrumb-arrow { font-size: 12px; margin: 0 4px; color: #999; }
.breadcrumb-seg.dropdown-trigger { padding: 0 6px; }

.toolbar-bar {
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}

.card-dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
:deep(.el-table .row-changed td) {
  background-color: rgba(64, 158, 255, 0.12) !important;
  transition: background-color 0.3s;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.info-card :deep(.el-card__body) { padding: 12px 16px; }
.children-card :deep(.el-card__body) { padding: 8px 16px; }
.compact-card :deep(.el-card__body) { padding: 10px 16px; }

.stats-bar {
  font-size: 12px;
  color: #666;
  padding: 4px 0 8px;
  display: flex;
  gap: 16px;
}
.stat-normal { color: #67c23a; }
.stat-warning { color: #e6a23c; }
.stat-error { color: #f56c6c; }
.stat-offline { color: #909399; }

.monitor-snapshot {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.monitor-value { font-size: 28px; font-weight: bold; color: #303133; }
.monitor-unit { font-size: 18px; font-weight: normal; color: #909399; margin-left: 4px; }
.monitor-time { font-size: 12px; color: #999; }

.control-panel { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.summary-row {
  display: flex;
  gap: 24px;
  font-size: 13px;
}

.runtime-error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
}
.runtime-error-banner.error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  color: #f56c6c;
}
.runtime-error-banner.warning {
  background: #fdf6ec;
  border: 1px solid #faecd8;
  color: #e6a23c;
}

.monitor-error-display {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.monitor-error-text {
  font-size: 14px;
  color: #f56c6c;
  line-height: 1.4;
}
</style>
