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
      <!-- Granularity direct-select (D6) + relative presets + custom window -->
      <el-select v-model="granularityChoice" size="small" style="width: 96px" @change="onGranularityChoice">
        <el-option v-for="c in GRANULARITY_CHOICES" :key="c.value" :value="c.value" :label="c.label" />
      </el-select>
      <el-radio-group v-model="rangeChoice" size="small" @change="onPreset">
        <el-radio-button v-for="p in PRESETS" :key="p.key" :value="p.key">{{ p.label }}</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="customRange"
        type="datetimerange"
        size="small"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="开始"
        end-placeholder="结束"
        style="width: 320px"
        @change="onCustomRange"
      />
      <el-button size="small" @click="refresh">刷新</el-button>
      <el-button size="small" @click="resetView">重置视图</el-button>
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
      :points="infiniteTrend.points.value"
      :mode="infiniteTrend.mode.value"
      :bucket-seconds="infiniteTrend.bucketSeconds.value"
      :summary="infiniteTrend.summary.value"
      :granularity="infiniteTrend.granularity.value"
      :unit="unit"
      :detect-interval-seconds="detectIntervalSeconds"
      :loading="infiniteTrend.loading.value"
      :error="loadError"
      :date-range="dateRange"
      :thresholds="thresholds"
      :anomaly-points="anomalyPoints"
      :show-thresholds="showThresholds"
      :show-anomalies="showAnomalies"
      chart-height="calc(100vh - 300px)"
      @data-point-click="onCandleClick"
      @popout="onPopout"
      @data-zoom="onDataZoom"
      @retry="refresh"
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
import TrendChart from '@/components/TrendChart/index.vue'
import ComparisonChart from '../components/ComparisonChart.vue'
import { getDrillDownRange } from '@/components/TrendChart/composables/useTrendDrillDown'
import { useTrendSeriesLoader } from '../composables/useTrendSeriesLoader'
import { useInfiniteTrend } from '@/components/TrendChart/composables/useInfiniteTrend'
import {
  GRANULARITY_CHOICES,
  PRESETS,
  windowForChoice,
  windowTooShortFor,
} from '@/components/TrendChart/composables/useTrendView'

const DEFAULT_GRANULARITY = 'HOUR'

const props = defineProps({
  monitorKind: { type: String, default: 'PROBE' },
  defaultGranularity: { type: String, default: '' },
  pageKey: { type: String, default: '' }
})

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

// Toolbar state (D6 direct-select + relative presets + custom absolute window)
const granularityChoice = ref('AUTO')
const rangeChoice = ref('24h')
const customRange = ref(null)
const loadError = ref('')

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

// ---- Toolbar window helpers ----

function windowSeconds(win) {
  return (new Date(win.end.replace(' ', 'T')) - new Date(win.start.replace(' ', 'T'))) / 1000
}

/** AUTO resolves through the same ladder the backend uses for the window. */
function resolveGranularity(win) {
  return granularityChoice.value === 'AUTO'
    ? infiniteTrend._granularityForSeconds(windowSeconds(win))
    : granularityChoice.value
}

/**
 * Single-mode load for an explicit window. Sets granularity up-front so
 * loadInitial's onGranularityChange callback stays silent (avoids a second
 * anomaly fetch) and dateRange is authoritative for loadAnomalies.
 */
function loadSingleWindow(start, end, gran) {
  try {
    loadError.value = ''
    dateRange.value = [start, end]
    granularity.value = gran
    infiniteTrend.loadInitial(start, end, gran)
    loadAnomalies()
  } catch (e) {
    console.error('Failed to load trend', e)
    loadError.value = '加载失败，请重试'
  }
}

function onGranularityChoice(c) {
  if (mode.value === 'compare') {
    if (c !== 'AUTO') granularity.value = c
    loadCompare()
    return
  }
  if (c === 'AUTO') {
    loadDefaultView()
    return
  }
  let win = customRange.value
    ? { start: customRange.value[0], end: customRange.value[1] }
    : windowForChoice(c, rangeChoice.value)
  // Same guard as useTrendView: a window below the granularity's bucket floor
  // gets clamped by the backend, contradicting the toolbar selection.
  if (windowTooShortFor(c, win)) {
    customRange.value = null
    rangeChoice.value = 'default'
    win = windowForChoice(c, null)
  }
  loadSingleWindow(win.start, win.end, c)
}

function onPreset(k) {
  customRange.value = null
  const win = windowForChoice(granularityChoice.value, k)
  if (mode.value === 'compare') {
    dateRange.value = [win.start, win.end]
    loadCompare()
    return
  }
  loadSingleWindow(win.start, win.end, resolveGranularity(win))
}

function onCustomRange(v) {
  if (!v || v.length !== 2) return
  customRange.value = v
  if (mode.value === 'compare') {
    dateRange.value = [v[0], v[1]]
    loadCompare()
    return
  }
  const win = { start: v[0], end: v[1] }
  loadSingleWindow(win.start, win.end, resolveGranularity(win))
}

function refresh() {
  if (mode.value === 'compare') {
    loadCompare()
    return
  }
  if (!monitorId.value) return
  if (dateRange.value && dateRange.value.length === 2) {
    const win = { start: dateRange.value[0], end: dateRange.value[1] }
    loadSingleWindow(win.start, win.end, resolveGranularity(win))
  } else {
    loadDefaultView()
  }
}

function resetView() {
  trendChartRef.value?.getChartInstance()?.dispatchAction({ type: 'dataZoom', start: 0, end: 100 })
}

/**
 * Load the list of monitors (probes or controls) for the dropdown.
 */
async function loadMonitorList() {
  try {
    const res = await listAssets({ kind: props.monitorKind })
    // D5: backend asset-list order is unstable — sort by id so the default
    // pick is deterministic (and lands on a data-bearing monitor, not a
    // connectivity probe).
    monitorList.value = (res.data || res || []).slice().sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
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
 * Load adaptive default view for the selected monitor (AUTO granularity).
 * Only called in single mode — compare mode uses loadCompare().
 */
async function loadDefaultView() {
  if (!monitorId.value) return
  try {
    loadError.value = ''
    await infiniteTrend.loadDefault(props.monitorKind)
    if (infiniteTrend.granularity.value) {
      granularity.value = infiniteTrend.granularity.value
    }
    loadAnomalies()
  } catch (e) {
    console.error('Failed to load trend', e)
    loadError.value = '加载失败，请重试'
  }
}

function clearChartData() {
  anomalyPoints.value = []
  if (mode.value === 'single') {
    infiniteTrend.clearBuffer()
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
    const win = windowForChoice(granularity.value, rangeChoice.value)
    dateRange.value = [win.start, win.end]
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
.display-toggles { display: flex; gap: 12px; align-items: center; margin-left: auto; }
</style>
