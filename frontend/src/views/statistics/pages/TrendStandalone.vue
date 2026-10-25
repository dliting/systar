<template>
  <div class="trend-standalone">
    <div class="standalone-header">
      <span class="standalone-title">{{ caption || name }}</span>
      <span v-if="unit" class="standalone-unit">{{ unit }}</span>
      <el-button size="small" @click="window.close()">关闭</el-button>
    </div>
    <div class="standalone-toolbar">
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
      <el-button v-if="mode === 'single'" size="small" @click="resetView">重置视图</el-button>
      <div class="display-toggles">
        <el-switch v-model="showThresholds" active-text="阈值" size="small" />
        <el-switch v-model="showAnomalies" active-text="异常" size="small" />
      </div>
    </div>
    <!-- Single-monitor view -->
    <TrendChart
      v-if="mode === 'single'"
      ref="trendChartRef"
      :points="viewPoints"
      :mode="viewMode"
      :bucket-seconds="viewBucketSeconds"
      :summary="viewSummary"
      :granularity="viewGranularity"
      :unit="unit"
      :loading="viewState === 'loading'"
      :error="viewError"
      :thresholds="thresholds"
      :anomaly-points="anomalyPoints"
      :show-thresholds="showThresholds"
      :show-anomalies="showAnomalies"
      chart-height="calc(100vh - 130px)"
      hide-popout
      @data-point-click="onCandleClick"
      @retry="viewRetry()"
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
import TrendChart from '@/components/TrendChart/index.vue'
import ComparisonChart from '../components/ComparisonChart.vue'
import { getDrillDownRange } from '@/components/TrendChart/composables/useTrendDrillDown'
import { useTrendSeriesLoader } from '../composables/useTrendSeriesLoader'
import {
  useTrendView,
  GRANULARITY_CHOICES,
  PRESETS,
  windowForChoice,
} from '@/components/TrendChart/composables/useTrendView'

const route = useRoute()
const wsStore = useWebSocketStore()

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
const thresholds = ref(null)
const anomalyPoints = ref([])
const showThresholds = ref(true)
const showAnomalies = ref(true)
const trendChartRef = ref(null)

// One-shot orchestration (spec F4): presets, direct granularity select,
// custom window, error/retry. Replaces the old infinite-scroll buffer —
// the standalone window is a snapshot view, not a live-scrolling one.
// Destructured so template v-model bindings hit top-level refs (a plain
// object's ref properties are NOT auto-unwrapped in templates).
const {
  state           : viewState,
  error           : viewError,
  points          : viewPoints,
  mode            : viewMode,
  bucketSeconds   : viewBucketSeconds,
  summary         : viewSummary,
  granularity     : viewGranularity,
  granularityChoice,
  rangeChoice,
  customRange,
  setGranularityChoice,
  setPreset,
  setCustomRange,
  refresh         : viewRefresh,
  retry           : viewRetry,
  load            : viewLoad,
} = useTrendView({ monitorId, monitorKind })

// Compare-mode state
const {
  series      : compareSeries,
  loading     : compareLoading,
  failedCount : compareFailedCount,
  load        : loadCompareSeries,
} = useTrendSeriesLoader()

// Compare mode keeps its own explicit granularity + range (the loader needs
// concrete values; AUTO in compare just keeps the current granularity).
const granularity = ref('HOUR')
const dateRange = ref(null)
const compareRangeDays = computed(() => computeRangeDays(dateRange.value))

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

/**
 * Anomalies for the single view, derived from the loaded points' own time
 * span (useTrendView owns the window; points head/tail are authoritative).
 * HOUR/DAY only — INTRADAY too dense, WEEK/MONTH aggregates away singles.
 */
async function loadAnomalies() {
  if (mode.value !== 'single') return
  if (viewState.value !== 'ready') { anomalyPoints.value = []; return }
  if (viewGranularity.value !== 'HOUR' && viewGranularity.value !== 'DAY') {
    anomalyPoints.value = []
    return
  }
  const pts = viewPoints.value
  if (pts.length === 0) { anomalyPoints.value = []; return }
  const startIso = pts[0].t.replace(' ', 'T')
  const endIso   = pts[pts.length - 1].t.replace(' ', 'T')
  try {
    const points = await detectAnomalies(monitorId.value, startIso, endIso)
    anomalyPoints.value = Array.isArray(points) ? points : []
  } catch (e) {
    console.warn('Failed to load anomalies:', e)
    anomalyPoints.value = []
  }
}

// ---- Toolbar handlers (mode is fixed per URL, so handlers branch once) ----

function onGranularityChoice(c) {
  if (mode.value === 'single') {
    setGranularityChoice(c)
    return
  }
  if (c !== 'AUTO') granularity.value = c
  loadCompare()
}

function onPreset(k) {
  if (mode.value === 'single') {
    setPreset(k)
    return
  }
  const win = windowForChoice(granularity.value, k)
  dateRange.value = [win.start, win.end]
  loadCompare()
}

function onCustomRange(v) {
  if (!v || v.length !== 2) return
  if (mode.value === 'single') {
    setCustomRange(v[0], v[1])
    return
  }
  dateRange.value = [v[0], v[1]]
  loadCompare()
}

function refresh() {
  if (mode.value === 'single') viewRefresh()
  else loadCompare()
}

function resetView() {
  trendChartRef.value?.getChartInstance()?.dispatchAction({ type: 'dataZoom', start: 0, end: 100 })
}

async function loadCompare() {
  if (mode.value !== 'compare') return
  if (!dateRange.value || dateRange.value.length !== 2) {
    const win = windowForChoice(granularity.value, rangeChoice.value)
    dateRange.value = [win.start, win.end]
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

/**
 * Drill-down: keep the explicit target granularity (AUTO would let the
 * server ladder pick a different one for the narrower window), then load
 * the drilled window in a single request.
 */
function onCandleClick({ time, granularity: clickedGranularity }) {
  if (mode.value !== 'single') return
  const range = getDrillDownRange(time, clickedGranularity)
  if (!range) return
  granularityChoice.value = range.granularity
  setCustomRange(formatMonitorDateTime(range.startTime), formatMonitorDateTime(range.endTime))
}

/** Re-load compare when threshold/anomaly toggles change. */
watch([showThresholds, showAnomalies], () => {
  if (mode.value === 'compare') loadCompare()
  // single-mode toggles are picked up by TrendChart's own watch
})

/** Re-fetch anomalies whenever a fresh single-mode view lands. */
watch(() => [viewState.value, viewGranularity.value], () => {
  loadAnomalies()
})

/**
 * WebSocket real-time append for the single view (INTRADAY only): push a
 * synthetic TrendPointVO into the loaded points array.
 */
watch(() => wsStore.probeValues[monitorId.value], (msg) => {
  if (mode.value !== 'single') return
  if (!msg || viewGranularity.value !== 'INTRADAY') return
  const v = msg.value
  viewPoints.value.push({ t: msg.time || msg.timestamp, avg: v, max: v, min: v, open: v, close: v, count: 1 })
})

watch(viewGranularity, (newVal, oldVal) => {
  if (mode.value !== 'single') return
  const oldGran = oldVal
  if (oldGran === 'INTRADAY') {
    wsStore.unsubscribe([monitorId.value])
  }
  if (newVal === 'INTRADAY' && wsStore.connected) {
    wsStore.subscribe([monitorId.value])
  }
})

onMounted(async () => {
  if (!getToken()) { window.close(); return }
  wsStore.connect()
  if (mode.value === 'single') {
    await loadMetadata()
    await viewLoad()
    if (viewGranularity.value === 'INTRADAY' && wsStore.connected) wsStore.subscribe([monitorId.value])
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
  margin-bottom: 8px; flex-wrap: wrap;
}
.display-toggles { display: flex; gap: 12px; align-items: center; margin-left: auto; }
</style>
