<template>
  <div class="comparison-chart">
    <div v-if="failedCount > 0" class="comparison-partial-failure">
      {{ failedCount }} 个监测器加载失败
    </div>
    <div v-if="series.length === 0 && !loading" class="comparison-empty">
      {{ failedCount > 0 ? '所有监测器加载失败' : '暂无数据' }}
    </div>
    <div ref="chartRef" :style="{ width: '100%', height: chartHeight }"></div>
    <div v-if="loading" class="comparison-loading">
      <el-skeleton :rows="3" animated />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useChart } from '../composables/useChart'
import { multiSeriesOption } from '../composables/trendOptions'
import { smallMultiplesOption } from '../composables/smallMultiples'
import { groupSeriesByUnit } from '../composables/palette'

const props = defineProps({
  /**
   * Loaded trend series — each item is a { id, caption, unit, color, dataPoints,
   * avg5/10/20, summary, thresholds, anomalies } object produced by
   * useTrendSeriesLoader. ComparisonChart is a pure rendering component;
   * data loading is the parent's responsibility.
   */
  series: { type: Array, default: () => [] },
  granularity: { type: String, default: 'HOUR' },
  rangeDays: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  failedCount: { type: Number, default: 0 },
  showThresholds: { type: Boolean, default: true },
  showAnomalies: { type: Boolean, default: true },
  chartHeight: { type: String, default: '500px' },
})

const { chartRef, initChart, dispose } = useChart()
let chartInstance = null

/**
 * Pick layout mode by unit group count:
 *   1 group  → single-axis (all monitors on shared Y)
 *   2 groups → dual-axis (left + right Y)
 *   3+ groups → small-multiples (one mini-chart per monitor)
 */
const layoutMode = computed(() => {
  const n = groupSeriesByUnit(props.series).size
  if (n <= 1) return 'single-axis'
  if (n === 2) return 'dual-axis'
  return 'small-multiples'
})

function buildOption() {
  if (!props.series || props.series.length === 0) return null
  if (layoutMode.value === 'small-multiples') {
    return smallMultiplesOption(props.series, props.granularity, props.rangeDays, props.showThresholds, props.showAnomalies)
  }
  return multiSeriesOption(props.series, props.granularity, props.rangeDays, props.showThresholds, props.showAnomalies)
}

function renderChart() {
  if (props.loading) return
  const option = buildOption()
  if (!option) return
  if (!chartInstance) {
    chartInstance = initChart()
  }
  if (chartInstance) {
    chartInstance.setOption(option, { notMerge: true })
  }
}

watch(
  () => [props.series, props.granularity, props.rangeDays, props.loading, props.showThresholds, props.showAnomalies],
  () => { nextTick(renderChart) },
  { deep: true },
)

onMounted(() => {
  nextTick(() => {
    chartInstance = initChart()
    renderChart()
  })
})

onUnmounted(() => {
  dispose()
  chartInstance = null
})
</script>

<style scoped>
.comparison-chart { position: relative; min-height: 300px; }
.comparison-partial-failure {
  position: absolute; top: 8px; left: 50%; transform: translateX(-50%);
  background: rgba(255, 171, 0, 0.12);
  border: 1px solid rgba(255, 171, 0, 0.3);
  color: #ffab00; padding: 4px 10px; border-radius: 4px;
  font-size: 11px; z-index: 2;
}
.comparison-empty {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  color: #8892b0; font-size: 13px;
}
.comparison-loading {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(10, 14, 23, 0.7); border-radius: 6px; z-index: 1;
}
</style>
