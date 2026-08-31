<template>
  <PanelCard>
    <template #title>告警趋势 (7日)</template>
    <template #meta v-if="handlingRate !== null">处置率 <strong>{{ (handlingRate * 100).toFixed(1) }}%</strong></template>
    <div v-if="hasData" ref="chartRef" class="chart-fill"></div>
    <div v-else class="chart-empty">
      <span>暂无趋势数据</span>
    </div>
  </PanelCard>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import PanelCard from './PanelCard.vue'

const props = defineProps({
  trend: { type: Array, default: () => [] },
  handlingRate: { type: Number, default: null }
})

const chartRef = ref(null)
const hasData = computed(() => (props.trend || []).length > 0)
let chart = null

function render() {
  if (!chart || !chartRef.value) return
  const data = props.trend || []
  const dates = data.map(d => (d.date || d.period || '').substring(5))
  const values = data.map(d => d.count ?? d.value ?? 0)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,14,23,0.95)',
      borderColor: '#1e3a5f',
      textStyle: { color: '#e6f1ff', fontSize: 13 }
    },
    grid: { top: 16, right: 16, bottom: 28, left: 44 },
    xAxis: {
      type: 'category', data: dates,
      axisLine: { lineStyle: { color: 'rgba(0,240,255,0.15)' } },
      axisLabel: { color: '#8892b0', fontSize: 11 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(0,240,255,0.06)', type: 'dashed' } },
      axisLabel: { color: '#8892b0', fontSize: 11 },
      axisLine: { show: false }, axisTick: { show: false }
    },
    series: [{
      type: 'line', data: values, smooth: true,
      symbol: 'circle', symbolSize: 6,
      lineStyle: { width: 2, color: '#00f0ff', shadowBlur: 8, shadowColor: 'rgba(0,240,255,0.3)' },
      itemStyle: { color: '#00f0ff', borderWidth: 2, borderColor: '#0d1321' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,240,255,0.25)' },
          { offset: 1, color: 'rgba(0,240,255,0.02)' }
        ])
      }
    }]
  }, true)
}

watch(() => props.trend, () => {
  nextTick(() => {
    if (hasData.value && chartRef.value && !chart) {
      chart = echarts.init(chartRef.value)
    }
    render()
  })
}, { deep: true })
onMounted(() => { if (chartRef.value) { chart = echarts.init(chartRef.value); render() } })
onUnmounted(() => { chart?.dispose(); chart = null })
defineExpose({ resize: () => chart?.resize() })
</script>

<style scoped>
.chart-fill { width: 100%; height: 100%; }
.chart-empty {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: #546e7a; font-size: 13px; letter-spacing: 1px;
}
</style>
