<template>
  <div class="dashboard">
    <div class="bg-grid"></div>
    <div class="bg-scanline"></div>
    <div class="bg-vignette"></div>

    <!-- Error banner -->
    <div v-if="errorMsg" class="error-banner">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ff5252" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <span>数据加载失败：{{ errorMsg }}</span>
      <button class="retry-btn" @click="refresh">重试</button>
    </div>

    <!-- Title bar -->
    <DashboardTitleBar :loading="loading" @refresh="refresh">
      <template #views>
        <div class="view-switcher">
          <button
            v-for="v in views" :key="v.key"
            class="view-btn"
            :class="{ active: currentView === v.key }"
            @click="switchView(v.key)"
          >{{ v.label }}</button>
        </div>
      </template>
    </DashboardTitleBar>

    <!-- KPI Strip -->
    <div class="kpi-section">
      <KpiStrip ref="kpiRef"
        :devices="dashboard.devices"
        :alarms="dashboard.alarms"
        :pending-alarms="monitorStats.pendingAlarms"
        :work-orders="dashboard.workOrders"
        :inspections="dashboard.inspections"
      />
    </div>

    <!-- View: Overview (default) -->
    <template v-if="currentView === 'overview'">
      <div class="charts-row-1">
        <div class="chart-cell span-4">
          <AssetStateChart :data="monitorStats.assetsByState" ref="assetChartRef" />
        </div>
        <div class="chart-cell span-8">
          <AlarmTrendChart :trend="dashboard.alarms?.trend || []" :handling-rate="dashboard.alarms?.handlingRate" ref="alarmTrendRef" />
        </div>
      </div>
      <div class="charts-row-2">
        <div class="chart-cell span-4">
          <WorkOrderChart :work-orders="dashboard.workOrders" :by-status="workOrderByStatus" ref="workOrderChartRef" />
        </div>
        <div class="chart-cell span-4">
          <TopAlarmDevices :devices="dashboard.topAlarmDevices || []" ref="topAlarmRef" />
        </div>
        <div class="chart-cell span-4">
          <DeviceOnlineGauge :availability="dashboard.devices?.availability || 0" ref="gaugeRef" />
        </div>
      </div>
    </template>

    <!-- View: Alarm Focus -->
    <template v-else-if="currentView === 'alarm'">
      <div class="charts-full">
        <div class="chart-cell">
          <AlarmTrendChart :trend="dashboard.alarms?.trend || []" :handling-rate="dashboard.alarms?.handlingRate" ref="alarmTrendRef" />
        </div>
      </div>
      <div class="charts-half">
        <div class="chart-cell">
          <TopAlarmDevices :devices="dashboard.topAlarmDevices || []" ref="topAlarmRef" />
        </div>
        <div class="chart-cell">
          <AssetStateChart :data="monitorStats.assetsByState" ref="assetChartRef" />
        </div>
      </div>
    </template>

    <!-- View: Device Focus -->
    <template v-else-if="currentView === 'device'">
      <div class="charts-row-2">
        <div class="chart-cell span-4">
          <DeviceOnlineGauge :availability="dashboard.devices?.availability || 0" ref="gaugeRef" />
        </div>
        <div class="chart-cell span-8">
          <AssetStateChart :data="monitorStats.assetsByState" ref="assetChartRef" />
        </div>
      </div>
      <div class="charts-half">
        <div class="chart-cell">
          <AlarmTrendChart :trend="dashboard.alarms?.trend || []" :handling-rate="dashboard.alarms?.handlingRate" ref="alarmTrendRef" />
        </div>
        <div class="chart-cell">
          <WorkOrderChart :work-orders="dashboard.workOrders" :by-status="workOrderByStatus" ref="workOrderChartRef" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import DashboardTitleBar from './DashboardTitleBar.vue'
import KpiStrip from './KpiStrip.vue'
import AssetStateChart from './AssetStateChart.vue'
import AlarmTrendChart from './AlarmTrendChart.vue'
import WorkOrderChart from './WorkOrderChart.vue'
import TopAlarmDevices from './TopAlarmDevices.vue'
import DeviceOnlineGauge from './DeviceOnlineGauge.vue'
import { getDashboardStats } from '@/api/iot/dashboard'
import { getDashboardStats as getOpsDashboard, getWorkOrderStats } from '@/api/iot/statistics'

defineOptions({ name: 'Dashboard' })

const REFRESH_INTERVAL_MS = 30000

const dashboard = reactive({
  alarms: { today: 0, trend: [], handlingRate: 0 },
  workOrders: { open: 0, mttrHours: 0, slaCompliance: 0 },
  inspections: { todayTotal: 0, completed: 0, completionRate: 0 },
  devices: { total: 0, online: 0, availability: 0 },
  topAlarmDevices: []
})

const monitorStats = reactive({
  totalDevices: 0, onlineDevices: 0, totalProbes: 0,
  totalAlarms: 0, pendingAlarms: 0, assetsByState: {}
})

const workOrderByStatus = ref({})
const loading = ref(false)
const errorMsg = ref('')
const currentView = ref('overview')

const views = [
  { key: 'overview', label: '综合概览' },
  { key: 'alarm', label: '告警聚焦' },
  { key: 'device', label: '设备概览' }
]

function switchView(key) {
  currentView.value = key
  nextTick(resizeAll)
}

const kpiRef = ref(null)
const assetChartRef = ref(null)
const alarmTrendRef = ref(null)
const workOrderChartRef = ref(null)
const topAlarmRef = ref(null)
const gaugeRef = ref(null)

let refreshTimer = null
let resizeTimer = null
const RESIZE_DEBOUNCE_MS = 150

function resizeAll() {
  assetChartRef.value?.resize()
  alarmTrendRef.value?.resize()
  workOrderChartRef.value?.resize()
  topAlarmRef.value?.resize()
  gaugeRef.value?.resize()
}

async function refresh() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [opsRes, monRes] = await Promise.allSettled([
      getOpsDashboard(),
      getDashboardStats()
    ])

    if (opsRes.status === 'fulfilled' && opsRes.value) {
      const d = opsRes.value
      if (d.devices) Object.assign(dashboard.devices, d.devices)
      if (d.alarms) Object.assign(dashboard.alarms, d.alarms)
      if (d.workOrders) Object.assign(dashboard.workOrders, d.workOrders)
      if (d.inspections) Object.assign(dashboard.inspections, d.inspections)
      if (d.topAlarmDevices) dashboard.topAlarmDevices = d.topAlarmDevices
    }

    if (monRes.status === 'fulfilled' && monRes.value?.data) {
      Object.assign(monitorStats, monRes.value.data)
    }

    // Fetch work-order by-status for the chart (not in DashboardVO)
    try {
      const today = new Date()
      const weekAgo = new Date(today.getTime() - 7 * 24 * 3600 * 1000)
      const fmt = d => d.toISOString().substring(0, 10)
      const woRes = await getWorkOrderStats({ startDate: fmt(weekAgo), endDate: fmt(today) })
      if (woRes?.byStatus) {
        workOrderByStatus.value = woRes.byStatus
      }
    } catch { /* non-critical */ }

    // Show error if both primary sources failed
    if (opsRes.status === 'rejected' && monRes.status === 'rejected') {
      errorMsg.value = opsRes.reason?.message || monRes.reason?.message || '数据加载失败'
    }

    kpiRef.value?.animateValues()
    nextTick(resizeAll)
  } catch (e) {
    errorMsg.value = e.message || '未知错误'
  } finally {
    loading.value = false
  }
}

function handleResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => nextTick(resizeAll), RESIZE_DEBOUNCE_MS)
}

onMounted(() => {
  refresh()
  refreshTimer = setInterval(refresh, REFRESH_INTERVAL_MS)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  clearTimeout(resizeTimer)
  window.removeEventListener('resize', handleResize)
})

onActivated(() => {
  nextTick(resizeAll)
  if (!refreshTimer) {
    refresh()
    refreshTimer = setInterval(refresh, REFRESH_INTERVAL_MS)
  }
})

onDeactivated(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.dashboard {
  position: relative;
  margin: -20px;
  height: calc(100vh - 56px);
  padding: 0 20px 20px;
  background: #080c14;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-family: 'Consolas', 'Courier New', 'Source Code Pro', monospace;
}

/* ---- Background effects ---- */
.bg-grid {
  position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(0, 240, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 240, 255, 0.025) 1px, transparent 1px);
  background-size: 48px 48px;
  animation: gridDrift 30s linear infinite;
}
@keyframes gridDrift {
  0% { transform: translate(0, 0); }
  100% { transform: translate(48px, 48px); }
}

.bg-scanline {
  position: absolute; inset: 0; pointer-events: none;
  background: repeating-linear-gradient(
    0deg, transparent, transparent 2px, rgba(0,0,0,0.06) 2px, rgba(0,0,0,0.06) 4px
  );
}

.bg-vignette {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(ellipse at 50% 50%, transparent 50%, rgba(0,0,0,0.4) 100%);
}

/* ---- Error banner ---- */
.error-banner {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  background: rgba(255,82,82,0.08); border: 1px solid rgba(255,82,82,0.2);
  border-radius: 8px; color: #ff5252; font-size: 13px;
  position: relative; z-index: 2;
}
.retry-btn {
  background: transparent; border: 1px solid rgba(255,82,82,0.3);
  border-radius: 4px; color: #ff5252; font-size: 12px;
  padding: 2px 10px; cursor: pointer;
  transition: all 0.2s;
}
.retry-btn:hover { background: rgba(255,82,82,0.1); }

/* ---- Sections ---- */
.kpi-section { position: relative; z-index: 1; }

.charts-row-1 {
  display: grid;
  grid-template-columns: 4fr 8fr;
  gap: 16px;
  flex: 1;
  min-height: 0;
  position: relative;
  z-index: 1;
}

.charts-row-2 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
  position: relative;
  z-index: 1;
}

.chart-cell {
  min-height: 0;
  height: 100%;
}

/* ---- View switcher ---- */
.view-switcher {
  display: flex; gap: 2px; margin-right: 16px;
}
.view-btn {
  background: transparent; border: 1px solid rgba(0,240,255,0.15);
  border-radius: 4px; color: #8892b0; font-size: 11px;
  padding: 3px 12px; cursor: pointer; transition: all 0.2s;
  font-family: inherit; letter-spacing: 1px;
}
.view-btn:hover { border-color: rgba(0,240,255,0.4); color: #ccd6f6; }
.view-btn.active {
  background: rgba(0,240,255,0.1); border-color: rgba(0,240,255,0.4);
  color: #00f0ff; font-weight: 600;
}

/* ---- View: Alarm / Device layouts ---- */
.charts-full {
  display: grid;
  grid-template-columns: 1fr;
  flex: 1;
  min-height: 0;
  position: relative; z-index: 1;
}
.charts-half {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  flex: 1;
  min-height: 0;
  position: relative; z-index: 1;
}

@media (max-width: 1280px) {
  .charts-row-1 {
    grid-template-columns: 1fr;
  }
  .charts-row-1 .chart-cell { min-height: 200px; }
  .charts-row-2 {
    grid-template-columns: 1fr;
  }
  .charts-row-2 .chart-cell { min-height: 200px; }
  .charts-half {
    grid-template-columns: 1fr;
  }
  .charts-half .chart-cell { min-height: 200px; }
}
</style>
