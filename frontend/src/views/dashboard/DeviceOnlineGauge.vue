<template>
  <PanelCard>
    <template #title>设备在线率</template>
    <template #badge>AVAILABILITY</template>
    <div ref="chartRef" class="chart-fill"></div>
  </PanelCard>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import PanelCard from './PanelCard.vue'

const props = defineProps({
  availability: { type: Number, default: 0 }
})

const chartRef = ref(null)
let chart = null

function getColor(val) {
  if (val >= 0.95) return '#00e676'
  if (val >= 0.8) return '#ffab00'
  return '#ff5252'
}

function render() {
  if (!chart || !chartRef.value) return
  const pct = (props.availability * 100)
  const color = getColor(props.availability)

  chart.setOption({
    series: [{
      type: 'gauge',
      center: ['50%', '58%'], radius: '88%',
      startAngle: 200, endAngle: -20,
      min: 0, max: 100, splitNumber: 5,
      itemStyle: { color },
      progress: { show: true, width: 14, roundCap: true },
      pointer: { length: '55%', width: 4, itemStyle: { color: '#00f0ff' } },
      axisLine: { lineStyle: { width: 14, color: [[1, 'rgba(0,240,255,0.08)']] }, roundCap: true },
      axisTick: { distance: -18, length: 4, lineStyle: { color: 'rgba(0,240,255,0.2)', width: 1 } },
      splitLine: { distance: -22, length: 8, lineStyle: { color: 'rgba(0,240,255,0.25)', width: 1 } },
      axisLabel: { distance: -6, color: '#8892b0', fontSize: 10, fontFamily: 'Consolas, monospace' },
      anchor: { show: true, size: 10, itemStyle: { borderColor: '#00f0ff', borderWidth: 2, color: '#0d1321' } },
      title: { show: true, offsetCenter: [0, '72%'], color: '#8892b0', fontSize: 13 },
      detail: {
        valueAnimation: true, offsetCenter: [0, '46%'],
        fontSize: 28, fontWeight: 700, fontFamily: 'Consolas, monospace',
        color: '#e6f1ff', formatter: '{value}%',
        shadowBlur: 16, shadowColor: color + '66'
      },
      data: [{ value: Math.round(pct * 10) / 10, name: '在线率' }]
    }]
  }, true)
}

watch(() => props.availability, () => nextTick(render))
onMounted(() => { if (chartRef.value) { chart = echarts.init(chartRef.value); render() } })
onUnmounted(() => { chart?.dispose(); chart = null })
defineExpose({ resize: () => chart?.resize() })
</script>

<style scoped>
.chart-fill { width: 100%; height: 100%; }
</style>
