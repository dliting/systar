<template>
  <PanelCard>
    <template #title>资产状态分布</template>
    <template #badge>REAL-TIME</template>
    <div ref="chartRef" class="chart-fill"></div>
  </PanelCard>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import PanelCard from './PanelCard.vue'

const props = defineProps({
  data: { type: Object, default: () => ({}) }
})

const chartRef = ref(null)
let chart = null

const STATE_COLORS = { NORMAL: '#00e676', WARNING: '#ffab00', ERROR: '#ff5252', OFFLINE: '#546e7a' }
const STATE_LABELS = { NORMAL: '正常', WARNING: '警告', ERROR: '错误', OFFLINE: '离线' }

function render() {
  if (!chart || !chartRef.value) return
  const states = props.data || {}
  const total = Object.values(states).reduce((s, v) => s + (v || 0), 0)

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(10,14,23,0.95)',
      borderColor: '#1e3a5f',
      textStyle: { color: '#e6f1ff', fontSize: 13 }
    },
    legend: {
      bottom: 8, itemWidth: 12, itemHeight: 12, itemGap: 20,
      textStyle: { color: '#8892b0', fontSize: 12 }
    },
    graphic: [{
      type: 'text', left: 'center', top: '38%',
      style: { text: String(total), fill: '#e6f1ff', fontSize: 28, fontWeight: 700, fontFamily: 'Consolas, monospace', textAlign: 'center' }
    }, {
      type: 'text', left: 'center', top: '52%',
      style: { text: '资产总数', fill: '#8892b0', fontSize: 12, textAlign: 'center' }
    }],
    series: [{
      type: 'pie', radius: ['58%', '78%'], center: ['50%', '46%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 5, borderColor: '#0d1321', borderWidth: 3 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 15, fontWeight: 'bold', color: '#e6f1ff' },
        itemStyle: { shadowBlur: 20, shadowColor: 'rgba(0,240,255,0.3)' }
      },
      animationType: 'scale', animationEasing: 'elasticOut',
      data: Object.entries(STATE_COLORS).map(([key, color]) => ({
        value: states[key] || 0, name: STATE_LABELS[key], itemStyle: { color }
      }))
    }]
  }, true)
}

watch(() => props.data, () => nextTick(render), { deep: true })
onMounted(() => { if (chartRef.value) { chart = echarts.init(chartRef.value); render() } })
onUnmounted(() => { chart?.dispose(); chart = null })
defineExpose({ resize: () => chart?.resize() })
</script>

<style scoped>
.chart-fill { width: 100%; height: 100%; }
</style>
