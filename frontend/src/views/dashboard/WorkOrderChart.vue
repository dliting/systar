<template>
  <PanelCard>
    <template #title>工单状态分布</template>
    <template #meta v-if="mttrHours > 0">MTTR {{ mttrHours.toFixed(1) }}h</template>
    <div ref="chartRef" class="chart-fill"></div>
  </PanelCard>
</template>

<script setup>
import { ref, watch, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import PanelCard from './PanelCard.vue'

const props = defineProps({
  workOrders: { type: Object, default: () => ({ open: 0, mttrHours: 0, slaCompliance: 0 }) },
  byStatus: { type: Object, default: () => ({}) }
})

const chartRef = ref(null)
let chart = null

const mttrHours = computed(() => props.workOrders.mttrHours || 0)

const STATUS_COLORS = {
  PENDING: '#ffab00', ASSIGNED: '#42a5f5', IN_PROGRESS: '#7c4dff',
  COMPLETED: '#00e676', CLOSED: '#546e7a', CANCELLED: '#78909c'
}
const STATUS_LABELS = {
  PENDING: '待派发', ASSIGNED: '已派发', IN_PROGRESS: '处理中',
  COMPLETED: '已完成', CLOSED: '已关闭', CANCELLED: '已取消'
}

const EMPTY_OPTION = {
  title: { text: '暂无工单数据', left: 'center', top: 'center', textStyle: { color: '#546e7a', fontSize: 14, fontWeight: 400 } },
  xAxis: { show: false }, yAxis: { show: false }, series: []
}

function render() {
  if (!chart || !chartRef.value) return
  const byStatus = props.byStatus || {}
  const entries = Object.entries(byStatus).filter(([, v]) => v > 0)

  if (entries.length === 0) { chart.setOption(EMPTY_OPTION, true); return }

  chart.setOption({
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(10,14,23,0.95)', borderColor: '#1e3a5f',
      textStyle: { color: '#e6f1ff', fontSize: 13 }
    },
    grid: { top: 8, right: 24, bottom: 8, left: 72, containLabel: false },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(0,240,255,0.06)', type: 'dashed' } },
      axisLabel: { color: '#8892b0', fontSize: 11 },
      axisLine: { show: false }, axisTick: { show: false }
    },
    yAxis: {
      type: 'category',
      data: entries.map(([k]) => STATUS_LABELS[k] || k),
      axisLine: { lineStyle: { color: 'rgba(0,240,255,0.1)' } },
      axisLabel: { color: '#ccd6f6', fontSize: 12 }, axisTick: { show: false }
    },
    series: [{
      type: 'bar', barWidth: 16,
      data: entries.map(([k, v]) => ({
        value: v,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: (STATUS_COLORS[k] || '#00f0ff') + '33' },
            { offset: 1, color: STATUS_COLORS[k] || '#00f0ff' }
          ]),
          borderRadius: [0, 4, 4, 0]
        }
      })),
      label: { show: true, position: 'right', color: '#e6f1ff', fontSize: 12, fontFamily: 'Consolas, monospace' }
    }]
  }, true)
}

watch(() => [props.workOrders, props.byStatus], () => nextTick(render), { deep: true })
onMounted(() => { if (chartRef.value) { chart = echarts.init(chartRef.value); render() } })
onUnmounted(() => { chart?.dispose(); chart = null })
defineExpose({ resize: () => chart?.resize() })
</script>

<style scoped>
.chart-fill { width: 100%; height: 100%; }
</style>
