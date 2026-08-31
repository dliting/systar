<template>
  <div class="content-area">
    <div class="content-header">
      <div class="breadcrumb">
        <span v-for="(crumb, i) in breadcrumbs" :key="i"
          :class="{ link: i < breadcrumbs.length - 1 }"
          @click="i < breadcrumbs.length - 1 && popToIndex(i)">
          {{ crumb }}
          <span v-if="i < breadcrumbs.length - 1" class="sep"> › </span>
        </span>
      </div>
      <el-button v-if="pageStack.length > 1" text size="small" @click="goBack" class="back-btn">
        ← 返回
      </el-button>
    </div>
    <div class="content-body">
      <component :is="currentComponent" v-bind="pageProps" @drill-down="handleDrillDown" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, shallowRef } from 'vue'
import AlarmSummary from './pages/AlarmSummary.vue'
import WorkOrderSummary from './pages/WorkOrderSummary.vue'
import InspectionSummary from './pages/InspectionSummary.vue'
import DeviceSummary from './pages/DeviceSummary.vue'
import MaintenanceSummary from './pages/MaintenanceSummary.vue'
import DashboardOverview from './pages/DashboardOverview.vue'
import MonitorTrend from './pages/MonitorTrend.vue'

const componentMap = {
  'alarm-summary': AlarmSummary,
  'alarm-level': { template: '<div class="detail-page"><h3>等级分布详情</h3><p>开发中...</p></div>' },
  'alarm-trend': { template: '<div class="detail-page"><h3>趋势分析详情</h3><p>开发中...</p></div>' },
  'alarm-top-devices': { template: '<div class="detail-page"><h3>Top告警设备详情</h3><p>开发中...</p></div>' },
  'workorder-summary': WorkOrderSummary,
  'workorder-aging': { template: '<div class="detail-page"><h3>老化分布详情</h3><p>开发中...</p></div>' },
  'workorder-mttr': { template: '<div class="detail-page"><h3>MTTR趋势详情</h3><p>开发中...</p></div>' },
  'workorder-sla': { template: '<div class="detail-page"><h3>SLA达标率详情</h3><p>开发中...</p></div>' },
  'inspection-summary': InspectionSummary,
  'inspection-anomaly': { template: '<div class="detail-page"><h3>异常分析详情</h3><p>开发中...</p></div>' },
  'device-summary': DeviceSummary,
  'device-ranking': { template: '<div class="detail-page"><h3>可用率排行详情</h3><p>开发中...</p></div>' },
  'device-detail': { template: '<div class="detail-page"><h3>设备详情</h3><p>开发中...</p></div>' },
  'maintenance-summary': MaintenanceSummary,
  'maintenance-cost': { template: '<div class="detail-page"><h3>费用趋势详情</h3><p>开发中...</p></div>' },
  'dashboard-summary': DashboardOverview,
  'trend-probe-trend': MonitorTrend,
  'trend-control-trend': MonitorTrend,
  'trend-intraday': MonitorTrend
}

const pageNames = {
  'alarm': { summary: '告警概览', level: '等级分布', trend: '趋势分析', 'top-devices': 'Top告警设备' },
  'workorder': { summary: '工单概览', aging: '老化分布', mttr: 'MTTR趋势', sla: 'SLA达标率' },
  'inspection': { summary: '巡检概览', anomaly: '异常分析' },
  'device': { summary: '在线率概览', ranking: '可用率排行', detail: '设备详情' },
  'maintenance': { summary: '维护概览', cost: '费用趋势' },
  'dashboard': { summary: 'KPI总览' },
  'trend': { 'probe-trend': '探针趋势', 'control-trend': '控制器趋势', 'intraday': '日内走势' }
}

const props = defineProps({ filters: Object })
const pageStack = ref([{ key: 'alarm-summary', params: {} }])

const currentPage = computed(() => pageStack.value[pageStack.value.length - 1])
const currentComponent = computed(() => componentMap[currentPage.value.key])
const pageProps = computed(() => ({ ...props.filters, ...currentPage.value.props, pageKey: currentPage.value.key }))

const breadcrumbs = computed(() => {
  return pageStack.value.map(p => {
    const key = p.key
    for (const [cat, subMap] of Object.entries(pageNames)) {
      for (const [sub, label] of Object.entries(subMap)) {
        if (`${cat}-${sub}` === key) return label
      }
    }
    return key
  })
})

function handleDrillDown({ key, params }) {
  pageStack.value.push({ key, params: params || {} })
}

function goBack() {
  if (pageStack.value.length > 1) pageStack.value.pop()
}

function popToIndex(i) {
  pageStack.value = pageStack.value.slice(0, i + 1)
}

/** Props to pass to MonitorTrend for each trend page key. */
const trendPageProps = {
  'trend-probe-trend': { monitorKind: 'PROBE' },
  'trend-control-trend': { monitorKind: 'CONTROL' },
  'trend-intraday': { monitorKind: 'PROBE', defaultGranularity: 'INTRADAY' }
}

function navigateTo(category, subKey) {
  const key = `${category}-${subKey}`
  if (componentMap[key]) {
    pageStack.value = [{ key, params: {}, props: trendPageProps[key] || {} }]
  }
}

defineExpose({ navigateTo })
</script>

<style scoped>
.content-area { padding: 20px; min-height: 100%; }
.content-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.breadcrumb { font-size: 12px; color: #8892b0; }
.breadcrumb .link { color: #00d4ff; cursor: pointer; }
.breadcrumb .link:hover { text-decoration: underline; }
.breadcrumb .sep { margin: 0 4px; color: #4a5568; }
.detail-page { padding: 40px; text-align: center; color: #8892b0; }
.detail-page h3 { color: #00d4ff; margin-bottom: 12px; }
</style>
