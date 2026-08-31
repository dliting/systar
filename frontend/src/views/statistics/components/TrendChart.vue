<template>
  <div class="trend-chart">
    <!-- Summary indicators bar -->
    <div v-if="!compact" class="trend-summary">
      <div v-if="title" class="chart-title-label">
        {{ title }}
        <span v-if="unit" class="chart-title-unit">{{ unit }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">最新值</span>
        <span class="summary-value" :style="{ color: '#00e676' }">{{ formatMonitorValue(summary?.currentValue, unit) }}</span>
        <span class="summary-unit">{{ unit }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">最高</span>
        <span class="summary-value" style="color: #ff5252">{{ formatMonitorValue(summary?.periodMax, unit) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">最低</span>
        <span class="summary-value" style="color: #00d4ff">{{ formatMonitorValue(summary?.periodMin, unit) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">采样数</span>
        <span class="summary-value">{{ summary?.totalSamples?.toLocaleString() || '0' }}</span>
      </div>
      <div v-if="dataPoints?.length > 0" class="summary-item" style="margin-left: auto">
        <span class="summary-label">均线</span>
        <span style="font-size: 11px">
          <span style="color: #ffab00">{{ formatMA(avg5, unit) }}</span> /
          <span style="color: #ce93d8">{{ formatMA(avg10, unit) }}</span> /
          <span style="color: rgba(255,82,82,0.7)">{{ formatMA(avg20, unit) }}</span>
        </span>
      </div>
      <!-- Pop-out button -->
      <el-button v-if="!compact && !hidePopout" size="small" link @click="$emit('popout')" style="margin-left: 8px">
        <el-icon><FullScreen /></el-icon>
      </el-button>
    </div>
    <!-- Chart container -->
    <div ref="chartRef" :style="{ width: '100%', height: chartHeight }"></div>
    <!-- Loading skeleton -->
    <div v-if="loading" class="trend-loading">
      <el-skeleton :rows="3" animated />
    </div>
    <!-- Empty state text -->
    <div v-if="!loading && isEmpty" class="trend-empty-text">暂无数据</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { FullScreen } from '@element-plus/icons-vue'
import { useChart } from '../composables/useChart'
import { intradayOption, lineOption, emptyFrameOption } from '../composables/trendOptions'
import { formatMonitorValue, computeRangeDays, DEFAULT_PERIOD_WINDOW } from '@/config/monitor'

const props = defineProps({
  title: { type: String, default: '' },
  dataPoints: { type: Array, default: () => [] },
  intradayPoints: { type: Array, default: () => [] },
  avg5: { type: Array, default: () => [] },
  avg10: { type: Array, default: () => [] },
  avg20: { type: Array, default: () => [] },
  summary: { type: Object, default: null },
  granularity: { type: String, default: 'HOUR' },
  unit: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  hidePopout: { type: Boolean, default: false },
  chartHeight: { type: String, default: '400px' },
  detectIntervalSeconds: { type: Number, default: 10 },
  dateRange: { type: Array, default: null },
  thresholds: { type: Object, default: null },
  anomalyPoints: { type: Array, default: () => [] },
  showThresholds: { type: Boolean, default: true },
  showAnomalies: { type: Boolean, default: true },
  infiniteMode: { type: Boolean, default: false },
})

const emit = defineEmits(['data-point-click', 'popout', 'data-zoom'])

const { chartRef, initChart, dispose } = useChart()

let chartInstance = null
let lastRenderedGranularity = null

const isEmpty = computed(() => {
  return (!props.dataPoints || props.dataPoints.length === 0) &&
    (!props.intradayPoints || props.intradayPoints.length === 0)
})

function formatMA(arr, unit) {
  if (!arr || arr.length === 0) return '--'
  for (let i = arr.length - 1; i >= 0; i--) {
    if (arr[i] !== null && arr[i] !== undefined) return formatMonitorValue(arr[i], unit)
  }
  return '--'
}

function hasData() {
  return (props.dataPoints && props.dataPoints.length > 0) ||
    (props.intradayPoints && props.intradayPoints.length > 0)
}

function buildOption() {
  if (!hasData()) {
    return emptyFrameOption(props.granularity, props.unit, props.detectIntervalSeconds)
  }
  if (props.granularity === 'INTRADAY') {
    // INTRADAY never receives detectAnomalies (MonitorTrend only queries at HOUR/DAY),
    // so intradayOption's 5-parameter signature does not include thresholds/anomalyPoints.
    // The props are accepted by TrendChart for symmetry with lineOption but ignored here.
    return intradayOption(
      props.intradayPoints,
      props.unit,
      DEFAULT_PERIOD_WINDOW,
      props.detectIntervalSeconds,
      props.compact,
      props.infiniteMode,
    )
  }
  return lineOption(
    props.dataPoints,
    props.avg5,
    props.avg10,
    props.avg20,
    props.unit,
    props.granularity,
    props.compact,
    computeRangeDays(props.dateRange),
    props.thresholds,
    props.anomalyPoints,
    props.showThresholds,
    props.showAnomalies,
    props.infiniteMode,
  )
}

function registerClickHandler(instance) {
  if (!instance) return
  instance.off('click')
  instance.on('click', (params) => {
    if (props.granularity !== 'INTRADAY' && params.componentType === 'series') {
      emit('data-point-click', { time: params.name, granularity: props.granularity })
    }
  })
}

function registerDataZoomHandler(instance) {
  if (!instance || !props.infiniteMode) return
  instance.off('datazoom')
  instance.on('datazoom', (event) => {
    const start = event.start ?? event.batch?.[0]?.start
    const end   = event.end   ?? event.batch?.[0]?.end
    if (start == null || end == null) return
    emit('data-zoom', { start, end })
  })
}

function renderChart() {
  if (props.loading) return
  const option = buildOption()
  if (!option) return
  if (!chartInstance) {
    chartInstance = initChart()
  }
  if (chartInstance) {
    const granularityChanged = lastRenderedGranularity !== null && lastRenderedGranularity !== props.granularity
    const useNotMerge = !props.infiniteMode || granularityChanged
    chartInstance.setOption(option, { notMerge: useNotMerge })
    lastRenderedGranularity = props.granularity
    registerClickHandler(chartInstance)
    registerDataZoomHandler(chartInstance)
  }
}

/** Incremental data update — preserves current zoom position */
function updateDataIncremental(patch) {
  if (!chartInstance) return
  chartInstance.setOption(patch, { notMerge: false })
}

defineExpose({ updateDataIncremental, getChartInstance: () => chartInstance })

watch(
  // watch deps must include thresholds/anomalyPoints/showThresholds/showAnomalies
  // so toggling the toolbar switches triggers renderChart (phase 2 fix).
  () => [props.dataPoints, props.intradayPoints, props.granularity, props.avg5, props.avg10, props.avg20, props.unit, props.compact, props.loading, props.dateRange, props.thresholds, props.anomalyPoints, props.showThresholds, props.showAnomalies],
  () => { nextTick(renderChart) }
)

onMounted(() => {
  nextTick(() => {
    chartInstance = initChart()
    renderChart()
  })
})

onUnmounted(() => {
  // Replace direct chartInstance.dispose() with useChart.dispose so ResizeObserver
  // is disconnected properly. useChart's own onUnmounted also calls dispose —
  // both calls are idempotent (null-safe) so the redundancy is safe.
  dispose()
  chartInstance = null
  lastRenderedGranularity = null
})
</script>

<style scoped>
.trend-chart { position: relative; }
.trend-summary {
  display: flex; align-items: center; gap: 16px;
  padding: 8px 12px; background: rgba(0,212,255,0.02);
  border: 1px solid rgba(0,212,255,0.08); border-radius: 6px;
  margin-bottom: 10px; font-size: 12px;
}
.chart-title-label {
  color: #ccd6f6; font-weight: 600; font-size: 13px;
  margin-right: 8px; white-space: nowrap;
  border-right: 1px solid rgba(0,212,255,0.15);
  padding-right: 16px;
}
.chart-title-unit { color: #8892b0; font-weight: 400; font-size: 11px; margin-left: 4px; }
.summary-item { display: flex; align-items: center; gap: 6px; }
.summary-label { color: #8892b0; font-size: 11px; }
.summary-value { font-weight: 600; font-variant-numeric: tabular-nums; }
.summary-unit { color: #8892b0; font-size: 11px; }
.trend-loading {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(10,14,23,0.7); border-radius: 6px; z-index: 1;
}
.trend-empty-text {
  text-align: center; color: #8892b0; font-size: 12px;
  padding: 4px 0; opacity: 0.6;
}
</style>
