<template>
  <PanelCard>
    <template #title>Top 告警设备</template>
    <template #badge>TOP 5</template>
    <div ref="chartRef" class="chart-fill"></div>
  </PanelCard>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import PanelCard from './PanelCard.vue'

const TOP_N = 5

const props = defineProps({
  devices: { type: Array, default: () => [] }
})

const chartRef = ref(null)
let chart = null

function render() {
  if (!chart || !chartRef.value) return
  const list = (props.devices || []).slice(0, TOP_N)

  if (list.length === 0) {
    chart.setOption({
      title: { text: '暂无告警设备数据', left: 'center', top: 'center', textStyle: { color: '#546e7a', fontSize: 14, fontWeight: 400 } },
      xAxis: { show: false }, yAxis: { show: false }, series: []
    }, true)
    return
  }

  const names = list.map(d => d.deviceName || `Device-${d.deviceId}`)
  const values = list.map(d => d.alarmCount)

  chart.setOption({
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(10,14,23,0.95)', borderColor: '#1e3a5f',
      textStyle: { color: '#e6f1ff', fontSize: 13 }
    },
    grid: { top: 8, right: 24, bottom: 8, left: 90, containLabel: false },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(0,240,255,0.06)', type: 'dashed' } },
      axisLabel: { color: '#8892b0', fontSize: 11 },
      axisLine: { show: false }, axisTick: { show: false }
    },
    yAxis: {
      type: 'category', data: names, inverse: true,
      axisLine: { lineStyle: { color: 'rgba(0,240,255,0.1)' } },
      axisLabel: { color: '#ccd6f6', fontSize: 11, width: 80, overflow: 'truncate' },
      axisTick: { show: false }
    },
    series: [{
      type: 'bar', barWidth: 14,
      data: values.map((v, i) => ({
        value: v,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: i === 0 ? 'rgba(255,82,82,0.3)' : 'rgba(0,240,255,0.15)' },
            { offset: 1, color: i === 0 ? '#ff5252' : '#00f0ff' }
          ]),
          borderRadius: [0, 4, 4, 0],
          shadowBlur: i === 0 ? 8 : 4,
          shadowColor: i === 0 ? 'rgba(255,82,82,0.3)' : 'rgba(0,240,255,0.15)'
        }
      })),
      label: { show: true, position: 'right', color: '#e6f1ff', fontSize: 12, fontFamily: 'Consolas, monospace' }
    }]
  }, true)
}

watch(() => props.devices, () => nextTick(render), { deep: true })
onMounted(() => { if (chartRef.value) { chart = echarts.init(chartRef.value); render() } })
onUnmounted(() => { chart?.dispose(); chart = null })
defineExpose({ resize: () => chart?.resize() })
</script>

<style scoped>
.chart-fill { width: 100%; height: 100%; }
</style>
