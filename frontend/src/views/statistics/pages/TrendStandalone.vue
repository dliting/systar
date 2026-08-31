<template>
  <div class="trend-standalone">
    <div class="standalone-header">
      <span class="standalone-title">{{ caption || name }}</span>
      <span v-if="unit" class="standalone-unit">{{ unit }}</span>
      <el-button size="small" @click="window.close()">关闭</el-button>
    </div>
    <div class="standalone-toolbar">
      <div class="period-tabs">
        <el-button v-for="p in periods" :key="p.key" size="small"
          :type="granularity === p.key ? 'primary' : ''"
          @click="switchGranularity(p.key)">{{ p.label }}</el-button>
      </div>
      <el-date-picker v-model="dateRange" type="datetimerange" size="small"
        start-placeholder="开始时间" end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss" @change="onDateRangeChange" />
      <div class="display-toggles">
        <el-switch v-model="showThresholds" active-text="阈值" size="small" />
        <el-switch v-model="showAnomalies" active-text="异常" size="small" />
      </div>
    </div>
    <!-- Single-monitor view -->
    <TrendChart
      v-if="mode === 'single'"
      :data-points="infiniteTrend.dataPoints.value"
      :intraday-points="infiniteTrend.intradayPoints.value"
      :avg5="infiniteTrend.avg5.value"
      :avg10="infiniteTrend.avg10.value"
      :avg20="infiniteTrend.avg20.value"
      :summary="infiniteTrend.summary.value"
      :granularity="infiniteTrend.granularity.value"
      :unit="unit"
      :loading="infiniteTrend.loading.value"
      :date-range="dateRange"
      :thresholds="thresholds"
      :anomaly-points="anomalyPoints"
      :show-thresholds="showThresholds"
      :show-anomalies="showAnomalies"
      :infinite-mode="true"
      chart-height="calc(100vh - 130px)"
      hide-popout
      @data-point-click="onCandleClick"
      @data-zoom="onDataZoom"
    />
    <!-- Multi-monitor compare view (?monitorIds=1,2,3) -->
    <ComparisonChart
      v-if="mode === 'compare'"
      :series="compareSeries"
      :granularity="granularity"
      :range-days="compareRangeDays"
      :loading="compareLoading"
      :failed-count="compareFailedCount"
      :show-thresholds="showThresholds"
      :show-anomalies="showAnomalies"
      chart-height="calc(100vh - 130px)"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getTrendMetadata } from '@/api/iot/trend'
import { detectAnomalies } from '@/api/iot/analysis'
import { useWebSocketStore } from '@/stores/websocket'
import { getToken } from '@/utils/auth'
import { formatMonitorDateTime, computeRangeDays } from '@/config/monitor'
import TrendChart from '../components/TrendChart.vue'
import ComparisonChart from '../components/ComparisonChart.vue'
import { getDrillDownRange } from '../composables/useTrendDrillDown'
import { useTrendSeriesLoader } from '../composables/useTrendSeriesLoader'
import { useInfiniteTrend } from '../composables/useInfiniteTrend'

const route = useRoute()
const wsStore = useWebSocketStore()

const periods = [
  { key: 'INTRADAY', label: '分时' },
  { key: 'HOUR', label: '时' },
  { key: 'DAY', label: '日' },
  { key: 'WEEK', label: '周' },
  { key: 'MONTH', label: '月' }
]

/**
 * URL parsing priority: monitorIds first (comma-separated), then legacy monitorId.
 * Empty / invalid values return [] so window.close() can fire.
 */
function parseMonitorIdsFromQuery() {
  const rawIds = String(route.query.monitorIds || '')
  if (rawIds) {
    const ids = rawIds.split(',')
      .map(s => Number(s.trim()))
      .filter(n => Number.isFinite(n) && n > 0)
    if (ids.length > 0) return Array.from(new Set(ids))
  }
  const singleId = Number(route.query.monitorId)
  if (Number.isFinite(singleId) && singleId > 0) return [singleId]
  return []
}

const monitorIds = ref(parseMonitorIdsFromQuery())
const monitorKind = ref(route.query.monitorKind || 'PROBE')
// Mode is derived from URL — independent window does not expose mode toggle UI.
// Single-ID URL (?monitorId=X) auto-wraps to [X] and forces single mode.
// Multi-ID URL (?monitorIds=1,2,3) forces compare mode.
const mode = ref(monitorIds.value.length > 1 ? 'compare' : 'single')

if (monitorIds.value.length === 0) {
  window.close()
}

// Single-mode state — only meaningful when mode === 'single'
const monitorId = ref(monitorIds.value[0] || null)
const name = ref('')
const caption = ref('')
const unit = ref('')
const granularity = ref('HOUR')
const dateRange = ref(null)
const thresholds = ref(null)
const anomalyPoints = ref([])
const showThresholds = ref(true)
const showAnomalies = ref(true)

// Compare-mode state
const {
  series      : compareSeries,
  loading     : compareLoading,
  failedCount : compareFailedCount,
  load        : loadCompareSeries,
  clear       : clearCompareSeries,
} = useTrendSeriesLoader()

const compareRangeDays = computed(() => computeRangeDays(dateRange.value))

const infiniteTrend = useInfiniteTrend({
  monitorId,
  monitorKind: computed(() => monitorKind.value),
  unit,
  detectIntervalSeconds: ref(10),
  onGranularityChange(newGran) {
    granularity.value = newGran
    loadAnomalies()
  },
  onDateRangeChange(newRange) {
    dateRange.value = newRange
  },
})

async function loadMetadata() {
  if (mode.value !== 'single') return
  try {
    const res = await getTrendMetadata({ monitorId: monitorId.value, monitorKind: monitorKind.value })
    const meta = res.data || res
    name.value = meta.name || ''
    caption.value = meta.caption || ''
    unit.value = meta.unit || ''
    thresholds.value = {
      min     : meta.minValue ?? null,
      max     : meta.maxValue ?? null,
      warnCond: meta.warnCond ?? null,
    }
    document.title = `${caption.value || name.value} — 趋势图`
  } catch (e) {
    console.error('Failed to load metadata:', e)
    ElMessage.warning('加载监测器信息失败')
  }
}

async function loadAnomalies() {
  if (mode.value !== 'single') return
  if (granularity.value !== 'HOUR' && granularity.value !== 'DAY') {
    anomalyPoints.value = []
    return
  }
  if (!dateRange.value || dateRange.value.length !== 2) {
    anomalyPoints.value = []
    return
  }
  const [startStr, endStr] = dateRange.value
  try {
    const points = await detectAnomalies(monitorId.value, startStr.replace(' ', 'T'), endStr.replace(' ', 'T'))
    anomalyPoints.value = Array.isArray(points) ? points : []
  } catch (e) {
    console.warn('Failed to load anomalies:', e)
    anomalyPoints.value = []
  }
}

async function loadDefaultView() {
  if (mode.value !== 'single') return
  await infiniteTrend.loadDefault(monitorKind.value)
  if (infiniteTrend.granularity.value) {
    granularity.value = infiniteTrend.granularity.value
  }
  loadAnomalies()
}

async function loadTrendData() {
  if (mode.value !== 'single') return
  if (!dateRange.value || dateRange.value.length !== 2) {
    const now = new Date()
    const start = new Date(now)
    switch (granularity.value) {
      case 'INTRADAY': start.setDate(start.getDate() - 1); break
      case 'HOUR': start.setDate(start.getDate() - 7); break
      case 'DAY': start.setDate(start.getDate() - 30); break
      case 'WEEK': start.setMonth(start.getMonth() - 6); break
      case 'MONTH': start.setFullYear(start.getFullYear() - 2); break
    }
    dateRange.value = [formatMonitorDateTime(start), formatMonitorDateTime(now)]
  }
  const [startTime, endTime] = dateRange.value
  await infiniteTrend.loadInitial(startTime, endTime, granularity.value)
  loadAnomalies()
}

async function loadCompare() {
  if (mode.value !== 'compare') return
  if (!dateRange.value || dateRange.value.length !== 2) {
    const now = new Date()
    const start = new Date(now)
    switch (granularity.value) {
      case 'INTRADAY': start.setDate(start.getDate() - 1); break
      case 'HOUR': start.setDate(start.getDate() - 7); break
      case 'DAY': start.setDate(start.getDate() - 30); break
      case 'WEEK': start.setMonth(start.getMonth() - 6); break
      case 'MONTH': start.setFullYear(start.getFullYear() - 2); break
    }
    dateRange.value = [formatMonitorDateTime(start), formatMonitorDateTime(now)]
  }
  await loadCompareSeries(
    monitorIds.value,
    monitorKind.value,
    dateRange.value,
    granularity.value,
    showThresholds.value,
    showAnomalies.value,
  )
}

function switchGranularity(key) {
  granularity.value = key
  if (mode.value === 'single') {
    loadTrendData()
    loadAnomalies()
  } else {
    loadCompare()
  }
}

function onDateRangeChange() {
  if (mode.value === 'single') {
    loadTrendData()
    loadAnomalies()
  } else {
    loadCompare()
  }
}

function onCandleClick({ time, granularity: clickedGranularity }) {
  if (mode.value !== 'single') return
  const range = getDrillDownRange(time, clickedGranularity)
  if (!range) return
  granularity.value = range.granularity
  infiniteTrend.loadInitial(range.startTime, range.endTime, range.granularity)
}

function onDataZoom(event) {
  infiniteTrend.handleDataZoomEvent(event)
}

/** Re-load compare when threshold/anomaly toggles change. */
watch([showThresholds, showAnomalies], () => {
  if (mode.value === 'compare') loadCompare()
  // single-mode toggles are picked up by TrendChart's own watch
})

/**
 * Watch WebSocket probe values for real-time intraday updates (single mode only).
 */
watch(() => wsStore.probeValues[monitorId.value], (msg) => {
  if (mode.value !== 'single') return
  if (!msg || granularity.value !== 'INTRADAY') return
  infiniteTrend.appendRealtimePoint({ time: msg.time || msg.timestamp, value: msg.value })
})

watch([granularity], (newVal, oldVal) => {
  if (mode.value !== 'single') return
  const [oldGran] = oldVal
  if (oldGran === 'INTRADAY') {
    wsStore.unsubscribe([monitorId.value])
  }
  if (granularity.value === 'INTRADAY' && wsStore.connected) {
    wsStore.subscribe([monitorId.value])
  }
})

onMounted(async () => {
  if (!getToken()) { window.close(); return }
  wsStore.connect()
  if (mode.value === 'single') {
    await loadMetadata()
    await loadDefaultView()
    if (granularity.value === 'INTRADAY' && wsStore.connected) wsStore.subscribe([monitorId.value])
  } else {
    await loadCompare()
  }
})

onUnmounted(() => {
  // Batch unsubscribe all monitor IDs (works for both single and compare)
  if (monitorIds.value.length > 0) {
    wsStore.unsubscribe(monitorIds.value)
  }
})
</script>

<style scoped>
.trend-standalone {
  width: 100vw; height: 100vh; background: #0a0e17;
  padding: 10px 16px; box-sizing: border-box;
  display: flex; flex-direction: column;
}
.standalone-header {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 8px;
}
.standalone-title { color: #00d4ff; font-size: 18px; font-weight: 600; }
.standalone-unit { color: #8892b0; font-size: 13px; }
.standalone-toolbar {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 8px;
}
.period-tabs { display: flex; gap: 4px; }
.display-toggles { display: flex; gap: 12px; align-items: center; margin-left: auto; }
</style>
