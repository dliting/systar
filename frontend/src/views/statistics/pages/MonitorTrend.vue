<template>
  <div class="monitor-trend">
    <!-- Top control bar -->
    <div class="trend-toolbar">
      <el-radio-group v-model="mode" size="small" @change="onModeChange">
        <el-radio-button label="single">单图</el-radio-button>
        <el-radio-button label="compare">对比</el-radio-button>
      </el-radio-group>
      <!-- Single mode: number v-model -->
      <el-select
        v-if="mode === 'single'"
        v-model="monitorId"
        placeholder="选择监测点"
        filterable
        size="small"
        style="width: 200px"
        @change="onMonitorChange"
      >
        <el-option
          v-for="m in monitorList"
          :key="m.id"
          :label="m.caption || m.name"
          :value="m.id"
        />
      </el-select>
      <!-- Compare mode: number[] v-model, max=4 -->
      <el-select
        v-if="mode === 'compare'"
        v-model="monitorIds"
        multiple
        :multiple-limit="4"
        placeholder="选择监测点（最多 4 个）"
        filterable
        size="small"
        style="width: 280px"
        @change="onMonitorListChange"
      >
        <el-option
          v-for="m in monitorList"
          :key="m.id"
          :label="m.caption || m.name"
          :value="m.id"
        />
      </el-select>
      <div class="period-tabs">
        <el-button
          v-for="p in periods"
          :key="p.key"
          size="small"
          :type="granularity === p.key ? 'primary' : ''"
          @click="switchGranularity(p.key)"
        >{{ p.label }}</el-button>
      </div>
      <el-date-picker
        v-model="dateRange"
        type="datetimerange"
        size="small"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
        @change="onDateRangeChange"
      />
      <div class="display-toggles">
        <el-switch v-model="showThresholds" active-text="阈值" size="small" />
        <el-switch v-model="showAnomalies" active-text="异常" size="small" />
      </div>
    </div>
    <!-- Single mode: TrendChart -->
    <TrendChart
      v-if="mode === 'single'"
      ref="trendChartRef"
      :infinite-mode="true"
      :title="selectedCaption"
      :data-points="infiniteTrend.dataPoints.value"
      :intraday-points="infiniteTrend.intradayPoints.value"
      :avg5="infiniteTrend.avg5.value"
      :avg10="infiniteTrend.avg10.value"
      :avg20="infiniteTrend.avg20.value"
      :summary="infiniteTrend.summary.value"
      :granularity="infiniteTrend.granularity.value"
      :unit="unit"
      :detect-interval-seconds="detectIntervalSeconds"
      :loading="infiniteTrend.loading.value"
      :date-range="dateRange"
      :thresholds="thresholds"
      :anomaly-points="anomalyPoints"
      :show-thresholds="showThresholds"
      :show-anomalies="showAnomalies"
      @data-point-click="onCandleClick"
      @popout="onPopout"
      @data-zoom="onDataZoom"
    />
    <!-- Compare mode: ComparisonChart -->
    <ComparisonChart
      v-if="mode === 'compare'"
      :series="compareSeries"
      :granularity="granularity"
      :range-days="compareRangeDays"
      :loading="compareLoading"
      :failed-count="compareFailedCount"
      :show-thresholds="showThresholds"
      :show-anomalies="showAnomalies"
      chart-height="500px"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { getTrendMetadata } from '@/api/iot/trend'
import { detectAnomalies } from '@/api/iot/analysis'
import { listAssets } from '@/api/iot/asset'
import { useWebSocketStore } from '@/stores/websocket'
import { formatMonitorDateTime, computeRangeDays } from '@/config/monitor'
import TrendChart from '../components/TrendChart.vue'
import ComparisonChart from '../components/ComparisonChart.vue'
import { getDrillDownRange } from '../composables/useTrendDrillDown'
import { useTrendSeriesLoader } from '../composables/useTrendSeriesLoader'
import { useInfiniteTrend } from '../composables/useInfiniteTrend'

const DEFAULT_GRANULARITY = 'HOUR'

const props = defineProps({
  monitorKind: { type: String, default: 'PROBE' },
  defaultGranularity: { type: String, default: '' },
  pageKey: { type: String, default: '' }
})

const periods = [
  { key: 'INTRADAY', label: '分时' },
  { key: 'HOUR', label: '时' },
  { key: 'DAY', label: '日' },
  { key: 'WEEK', label: '周' },
  { key: 'MONTH', label: '月' }
]

const monitorId = ref(null)
const monitorIds = ref([])   // compare mode — kept separate from monitorId to avoid type confusion
const mode = ref('single')
const granularity = ref(props.defaultGranularity || DEFAULT_GRANULARITY)
const dateRange = ref(null)
const monitorList = ref([])
const unit = ref('')
const detectIntervalSeconds = ref(10)
const thresholds = ref(null)
const anomalyPoints = ref([])
const showThresholds = ref(true)
const showAnomalies = ref(true)

const trendChartRef = ref(null)

// Compare mode state — driven by useTrendSeriesLoader composable
const {
  series      : compareSeries,
  loading     : compareLoading,
  failedCount : compareFailedCount,
  load        : loadCompareSeries,
  clear       : clearCompareSeries,
} = useTrendSeriesLoader()

const compareRangeDays = computed(() => computeRangeDays(dateRange.value))

const wsStore = useWebSocketStore()

const infiniteTrend = useInfiniteTrend({
  monitorId,
  monitorKind: computed(() => props.monitorKind),
  unit,
  detectIntervalSeconds,
  onGranularityChange(newGran) {
    granularity.value = newGran
    loadAnomalies()
  },
  onDateRangeChange(newRange) {
    dateRange.value = newRange
  },
})

const selectedCaption = computed(() => {
  if (!monitorId.value) return ''
  const m = monitorList.value.find(item => item.id === monitorId.value)
  return m ? (m.caption || m.name || '') : ''
})

/**
 * Load the list of monitors (probes or controls) for the dropdown.
 */
async function loadMonitorList() {
  try {
    const res = await listAssets({ kind: props.monitorKind })
    monitorList.value = res.data || res || []
  } catch (e) {
    console.error('Failed to load monitor list:', e)
    monitorList.value = []
  }
}

/**
 * Load metadata for a specific monitor (unit, name, etc).
 * Also extracts min/max/warnCond into thresholds for markLine rendering.
 * Thresholds are independent of granularity/time-range, so they persist
 * across granularity switches until the next onMonitorChange.
 */
async function loadMetadata() {
  if (!monitorId.value) return
  try {
    const res = await getTrendMetadata({
      monitorId: monitorId.value,
      monitorKind: props.monitorKind
    })
    const meta = res.data || res
    unit.value = meta.unit || ''
    const di = parseInt((meta.detectInterval || '10s').replace(/[^0-9]/g, ''), 10)
    detectIntervalSeconds.value = Number.isFinite(di) && di > 0 ? di : 10
    thresholds.value = {
      min     : meta.minValue ?? null,
      max     : meta.maxValue ?? null,
      warnCond: meta.warnCond ?? null,
    }
  } catch (e) {
    console.error('Failed to load metadata:', e)
    unit.value = ''
    thresholds.value = null
  }
}

/**
 * Load anomaly points for the current monitor + time range.
 * Called only when granularity is HOUR or DAY — INTRADAY produces too-dense
 * points (markPoints would overlap) and WEEK/MONTH aggregate away single
 * anomalies. Time range comes from dateRange (ISO 8601 for backend @DateTimeFormat).
 */
async function loadAnomalies() {
  if (!monitorId.value) return
  if (granularity.value !== 'HOUR' && granularity.value !== 'DAY') {
    anomalyPoints.value = []
    return
  }
  if (!dateRange.value || dateRange.value.length !== 2) {
    anomalyPoints.value = []
    return
  }
  const [startStr, endStr] = dateRange.value
  const startIso = startStr.replace(' ', 'T')
  const endIso   = endStr.replace(' ', 'T')
  try {
    const points = await detectAnomalies(monitorId.value, startIso, endIso)
    anomalyPoints.value = Array.isArray(points) ? points : []
  } catch (e) {
    // Anomaly detection is best-effort — if it fails, the chart still renders
    // with main curves only; markPoint stays empty.
    console.warn('Failed to load anomalies:', e)
    anomalyPoints.value = []
  }
}

/**
 * Load adaptive default view for the selected monitor.
 * Only called in single mode — compare mode uses loadCompare().
 */
async function loadDefaultView() {
  if (!monitorId.value) return
  await infiniteTrend.loadDefault(props.monitorKind)
  if (infiniteTrend.granularity.value) {
    granularity.value = infiniteTrend.granularity.value
  }
  loadAnomalies()
}

/**
 * Load trend data for single mode — delegates to infiniteTrend.
 */
async function loadTrendData() {
  if (!monitorId.value) return
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
  infiniteTrend.loadInitial(startTime, endTime, granularity.value)
  loadAnomalies()
}

function clearChartData() {
  anomalyPoints.value = []
  if (mode.value === 'single') {
    infiniteTrend.clearBuffer()
  }
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

function onMonitorChange() {
  clearChartData()
  thresholds.value = null
  loadMetadata()
  loadDefaultView()
}

function onMonitorListChange() {
  loadCompare()
}

/**
 * Mode switch handler. Doesn't take newMode — both refs are cleared
 * unconditionally (avoids ESLint unused-param warning and keeps the two
 * el-select v-models strictly typed).
 */
function onModeChange() {
  monitorId.value = null
  monitorIds.value = []
  clearChartData()
  clearCompareSeries()
  thresholds.value = null
}

function onDateRangeChange() {
  if (mode.value === 'single') {
    loadTrendData()
    loadAnomalies()
  } else {
    loadCompare()
  }
}

/**
 * Compare-mode data loader — wraps useTrendSeriesLoader with current selection.
 * Silently no-ops if monitorIds is empty or dateRange missing.
 */
async function loadCompare() {
  if (mode.value !== 'compare') return
  if (!monitorIds.value || monitorIds.value.length === 0) {
    clearCompareSeries()
    return
  }
  if (!dateRange.value || dateRange.value.length !== 2) {
    // Synthesize a default range matching single-mode logic so compare mode
    // works out-of-the-box without forcing user to pick a range first.
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
    props.monitorKind,
    dateRange.value,
    granularity.value,
    showThresholds.value,
    showAnomalies.value,
  )
}

function onCandleClick({ time, granularity: clickedGranularity }) {
  const range = getDrillDownRange(time, clickedGranularity)
  if (!range) return
  granularity.value = range.granularity
  infiniteTrend.loadInitial(
    formatMonitorDateTime(range.startTime),
    formatMonitorDateTime(range.endTime),
    range.granularity,
  )
  loadAnomalies()
}

function onDataZoom(event) {
  infiniteTrend.handleDataZoomEvent(event)
}

function onPopout() {
  if (!monitorId.value) return
  const q = new URLSearchParams({ monitorId: monitorId.value, monitorKind: props.monitorKind })
  window.open(`/trend-standalone?${q}`, '_blank', 'width=1200,height=800')
}

/**
 * Watch showThresholds / showAnomalies in compare mode — re-load so each
 * series re-fetches anomalies (or skips the call) per the toggle.
 */
watch([showThresholds, showAnomalies], () => {
  if (mode.value === 'compare' && monitorIds.value.length > 0) loadCompare()
})

/**
 * Watch WebSocket probe values for real-time intraday updates.
 */
watch(() => wsStore.probeValues[monitorId.value], (msg) => {
  if (!msg || granularity.value !== 'INTRADAY') return
  infiniteTrend.appendRealtimePoint({ time: msg.time || msg.timestamp, value: msg.value })
})

// Subscribe/unsubscribe when monitor or granularity changes
watch([monitorId, granularity], (newVal, oldVal) => {
  const [oldId, oldGran] = oldVal
  if (oldId && oldGran === 'INTRADAY') {
    wsStore.unsubscribe([oldId])
  }
  if (monitorId.value && granularity.value === 'INTRADAY' && wsStore.connected) {
    wsStore.subscribe([monitorId.value])
  }
})

onMounted(async () => {
  await loadMonitorList()
  if (monitorList.value.length > 0 && !monitorId.value) {
    monitorId.value = monitorList.value[0].id
  }
  if (monitorId.value) {
    await loadMetadata()
    await loadDefaultView()
  }
})

onUnmounted(() => {
  if (monitorId.value) {
    wsStore.unsubscribe([monitorId.value])
  }
})
</script>

<style scoped>
.monitor-trend { min-height: 300px; }
.trend-toolbar {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 14px; margin-bottom: 12px;
  background: rgba(0,212,255,0.02);
  border: 1px solid rgba(0,212,255,0.08);
  border-radius: 6px; flex-wrap: wrap;
}
.period-tabs { display: flex; gap: 4px; }
.period-tabs .el-button { padding: 5px 10px; font-size: 12px; }
.display-toggles { display: flex; gap: 12px; align-items: center; margin-left: auto; }
</style>
