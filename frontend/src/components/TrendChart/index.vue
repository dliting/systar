<template>
  <div class="trend-chart">
    <!-- Summary indicators bar -->
    <div v-if="!compact" class="trend-summary">
      <div v-if="title" class="chart-title-label">
        {{ title }}<span v-if="unit" class="chart-title-unit">{{ unit }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">最新值</span>
        <span class="summary-value" style="color:#69f0ae">{{ formatMonitorValue(summary?.currentValue, unit) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">最高值</span>
        <span class="summary-value" style="color:#ff5252">{{ formatMonitorValue(summary?.periodMax, unit) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">最低值</span>
        <span class="summary-value" style="color:#00d4ff">{{ formatMonitorValue(summary?.periodMin, unit) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">采样数</span>
        <span class="summary-value">{{ summary?.totalSamples?.toLocaleString() || '0' }}</span>
      </div>
      <!-- Crosshair readout (populated on updateAxisPointer) -->
      <div v-if="readout" class="summary-item readout">
        <span class="readout-time">{{ readout.label }}</span>
        <template v-if="isOhlc">
          <span class="summary-value" :style="{ color: readout.rising ? '#ffab00' : readout.falling ? '#00d4ff' : '#8b9dc3' }">
            {{ readout.rising ? '上升' : readout.falling ? '下降' : '持平' }} {{ readout.changeText }}
          </span>
          <span class="summary-label">期初 {{ readout.openText }}</span>
          <span class="summary-label">期末 {{ readout.closeText }}</span>
        </template>
        <!-- RAW points carry no meaningful open/close (D2): show the value. -->
        <span v-else class="summary-value">实测值 {{ readout.valueText }}</span>
      </div>
      <div class="layer-toggles" style="margin-left:auto">
        <el-checkbox v-if="isOhlc" v-model="showCandles" size="small" label="箱体" />
        <el-checkbox v-if="isOhlc" v-model="showSamples" size="small" label="采样数" />
        <el-checkbox v-if="!isOhlc" v-model="showExtremes" size="small" label="极值带" />
      </div>
      <!-- Pop-out button -->
      <el-button v-if="!hidePopout" size="small" link @click="$emit('popout')">
        <el-icon><FullScreen /></el-icon>
      </el-button>
    </div>
    <!-- Chart container -->
    <div ref="chartRef" :style="{ width: '100%', height: chartHeight }"></div>
    <!-- Loading skeleton -->
    <div v-if="loading" class="trend-loading"><el-skeleton :rows="3" animated /></div>
    <!-- Error overlay with retry -->
    <div v-else-if="error" class="trend-error">
      <span class="trend-error-text">{{ error }}</span>
      <el-button size="small" @click="$emit('retry')">重试</el-button>
    </div>
    <!-- Empty state text -->
    <div v-else-if="isEmpty" class="trend-empty-text">暂无数据</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { FullScreen } from '@element-plus/icons-vue'
import { useChart } from '@/views/statistics/composables/useChart'
import { candlestickOption } from './options/candlestick'
import { intradayOption } from './options/intraday'
import { emptyFrameOption } from './options/empty'
import { useMovingAverage } from './composables/useMovingAverage'
import { formatMonitorValue } from '@/config/monitor'

const props = defineProps({
  title: { type: String, default: '' },
  points: { type: Array, default: () => [] },
  mode: { type: String, default: 'RAW' },
  bucketSeconds: { type: Number, default: null },
  summary: { type: Object, default: null },
  granularity: { type: String, default: 'INTRADAY' },
  unit: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
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
const emit = defineEmits(['data-point-click', 'popout', 'data-zoom', 'retry'])

const { chartRef, initChart, dispose } = useChart()
let chartInstance = null
let lastKey = null
let zrClickHandler = null

const showSamples = ref(false)
const showExtremes = ref(false)
// D1: OHLC granularities render as a line by default; 箱体 is an opt-in layer.
const showCandles = ref(false)
const readout = ref(null)

const isOhlc = computed(() => props.mode === 'OHLC')
const isEmpty = computed(() => !props.points || props.points.length === 0)
const ma = useMovingAverage(computed(() => props.points))

function buildOption() {
  if (isEmpty.value) return emptyFrameOption(props.granularity, props.unit, props.detectIntervalSeconds)
  if (isOhlc.value) {
    return candlestickOption(props.points, {
      unit: props.unit, granularity: props.granularity,
      ma: { ma5: ma.ma5.value, ma10: ma.ma10.value, ma20: ma.ma20.value },
      showSamples: showSamples.value, showThresholds: props.showThresholds,
      showAnomalies: props.showAnomalies, thresholds: props.thresholds,
      anomalyPoints: props.anomalyPoints, compact: props.compact, infiniteMode: props.infiniteMode,
      style: showCandles.value ? 'candle' : 'line',
    })
  }
  return intradayOption(props.points, {
    unit: props.unit, mode: props.mode, bucketSeconds: props.bucketSeconds,
    showExtremes: showExtremes.value, showThresholds: props.showThresholds,
    showAnomalies: props.showAnomalies, thresholds: props.thresholds,
    anomalyPoints: props.anomalyPoints, compact: props.compact, infiniteMode: props.infiniteMode,
    detectIntervalSeconds: props.detectIntervalSeconds,
  })
}

function emitPointClick(idx) {
  const p = props.points[idx]
  if (p) emit('data-point-click', { time: p.t, granularity: props.granularity })
}

function registerHandlers(instance) {
  instance.off('click')
  instance.on('click', (params) => {
    if (isOhlc.value && params.componentType === 'series') {
      emitPointClick(params.dataIndex)
    }
  })
  // D1 line default: ECharts maps series clicks only to symbols/candle bodies
  // — the polyline path carries no component info, so clicks on the line body
  // never reach the handler above. Snap plot-area clicks to the nearest
  // category instead, matching where the crosshair already points (spec F).
  const zr = instance.getZr()
  if (zr && zrClickHandler) zr.off('click', zrClickHandler)
  zrClickHandler = (e) => {
    if (!isOhlc.value) return
    if (e.target?.__ecComponentInfo) return // interactive item: series click already emitted
    if (!instance.containPixel({ gridIndex: 0 }, [e.offsetX, e.offsetY])) return
    const raw = instance.convertFromPixel({ xAxisIndex: 0 }, e.offsetX)
    const idx = Math.round(Array.isArray(raw) ? raw[0] : raw)
    if (!Number.isFinite(idx)) return
    emitPointClick(Math.min(Math.max(idx, 0), props.points.length - 1))
  }
  zr?.on?.('click', zrClickHandler)
  instance.off('datazoom')
  if (props.infiniteMode) {
    instance.on('datazoom', (event) => {
      const start = event.start ?? event.batch?.[0]?.start
      const end = event.end ?? event.batch?.[0]?.end
      if (start != null && end != null) emit('data-zoom', { start, end })
    })
  }
  instance.off('updateAxisPointer')
  instance.on('updateAxisPointer', (event) => {
    const idx = event.dataIndex ?? (Array.isArray(event.dataIndex) ? event.dataIndex[0] : null)
    const p = idx != null ? props.points[idx] : null
    if (!p || p.avg == null) { readout.value = null; return }
    const fmt = (v) => (v == null ? '--' : Number(v.toFixed(2)).toString())
    const label = event.seriesAxesParams?.axisLabel?.formattedValue ?? String(p.t)
    if (props.mode === 'OHLC') {
      // D2: 持平 when open==close (the old c>=o rule read "上升 0.0").
      const o = p.open ?? p.avg, c = p.close ?? p.avg
      readout.value = {
        label,
        rising: c > o, falling: c < o,
        changeText: `${fmt(Math.abs(c - o))}${props.unit}`,
        openText: `${fmt(o)}${props.unit}`,
        closeText: `${fmt(c)}${props.unit}`,
      }
    } else {
      // RAW points have open≡close — OHLC semantics would be noise (D2).
      readout.value = { label, valueText: `${fmt(p.avg)}${props.unit}` }
    }
  })
  instance.getZr()?.off?.('globalout')
  instance.getZr()?.on?.('globalout', () => { readout.value = null })
}

function renderChart() {
  if (props.loading || props.error) return
  // D2: drop the stale crosshair readout whenever the chart re-renders
  // (granularity switch, refresh, layer toggle) — old values mislead.
  readout.value = null
  const option = buildOption()
  if (!chartInstance) chartInstance = initChart()
  if (!chartInstance) return
  const key = `${props.granularity}|${props.mode}|${props.compact}|${showSamples.value}|${showExtremes.value}|${showCandles.value}`
  const structural = lastKey !== key
  chartInstance.setOption(option, { notMerge: structural || !props.infiniteMode })
  lastKey = key
  registerHandlers(chartInstance)
}

defineExpose({ getChartInstance: () => chartInstance })

watch(
  () => [props.points, props.mode, props.bucketSeconds, props.granularity, props.unit,
    props.compact, props.loading, props.error, props.dateRange, props.thresholds,
    props.anomalyPoints, props.showThresholds, props.showAnomalies,
    showSamples.value, showExtremes.value, showCandles.value],
  () => { nextTick(renderChart) }
)

onMounted(() => {
  nextTick(() => { chartInstance = initChart(); renderChart() })
  // E2E test seam: Playwright reads the live option through this hook
  // (production preview build — must stay unconditional).
  if (typeof window !== 'undefined') window.__trendChart = () => chartInstance
})
onUnmounted(() => { dispose(); chartInstance = null; lastKey = null })
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
.trend-error {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center; gap: 12px;
  background: rgba(10,14,23,0.7); border-radius: 6px; z-index: 1;
}
.trend-error-text { color: #ff5252; font-size: 12px; }
.readout { font-variant-numeric: tabular-nums; }
.readout-time { color: #8b9dc3; font-size: 11px; }
.layer-toggles { display: flex; gap: 8px; }
</style>
