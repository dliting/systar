<template>
  <div class="summary-page" v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无设备数据" />
    <template v-else-if="data">
      <el-row :gutter="16" class="stat-row">
        <el-col :span="8">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body"><span class="stat-value cyan">{{ data.totalDevices }}</span><span class="stat-label">设备总数</span></div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card"><div class="stat-glow green"></div>
            <div class="stat-body"><span class="stat-value green">{{ data.onlineDevices }}</span><span class="stat-label">在线设备</span></div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card"><div class="stat-glow orange"></div>
            <div class="stat-body"><span class="stat-value orange">{{ ((data.availabilityRate || 0) * 100).toFixed(1) }}%</span><span class="stat-label">可用率</span></div>
          </div>
        </el-col>
      </el-row>
      <div class="chart-grid">
        <div class="chart-card full" @click="$emit('drill-down', { key: 'device-ranking' })">
          <div class="chart-title">设备可用率排行 <span class="drill-hint">›</span></div>
          <div ref="rankRef" style="height: 320px"></div>
        </div>
        <div class="chart-card full">
          <div class="chart-title">设备在线率明细表</div>
          <el-table :data="data.details || []" style="margin-top: 8px" size="small" max-height="300">
            <el-table-column prop="deviceName" label="设备名称" />
            <el-table-column prop="onlineDays" label="在线天数" width="100" />
            <el-table-column prop="totalDays" label="统计天数" width="100" />
            <el-table-column label="在线率" width="100">
              <template #default="{ row }">{{ ((row.rate || 0) * 100).toFixed(1) }}%</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import { getDeviceRuntimeStats } from '@/api/iot/statistics'
import { useChart } from '../composables/useChart'
import { horizontalBarOption } from '../composables/chartOptions'

const props = defineProps({ startDate: String, endDate: String, granularity: String, pageKey: String })
defineEmits(['drill-down'])
const loading = ref(false); const data = ref(null)
const rank = useChart()

async function fetch() {
  if (!props.startDate) return
  loading.value = true
  try {
    data.value = await getDeviceRuntimeStats({ startDate: props.startDate, endDate: props.endDate, granularity: props.granularity })
    await nextTick(); render()
  } catch { data.value = null } finally { loading.value = false }
}
function render() {
  if (!data.value?.details?.length) return
  const sorted = [...data.value.details].sort((a, b) => b.rate - a.rate)
  rank.initChart(); rank.setOption(horizontalBarOption(
    sorted.map(d => d.deviceName), sorted.map(d => Number((d.rate * 100).toFixed(1))), '在线率 %'
  ))
}
watch(() => [props.startDate, props.endDate, props.granularity], fetch)
onMounted(fetch)
</script>

<style scoped>
.summary-page { min-height: 300px; }
.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 20px; }
.chart-card {
  background: rgba(0,212,255,0.02); border: 1px solid rgba(0,212,255,0.08);
  border-radius: 8px; padding: 12px; cursor: pointer; transition: border-color 0.2s;
}
.chart-card.full { grid-column: span 2; }
.chart-card:hover { border-color: rgba(0,212,255,0.3); background: rgba(0,212,255,0.04); }
.chart-title { font-size: 13px; color: #ccd6f6; font-weight: 500; margin-bottom: 8px; display: flex; justify-content: space-between; border-left: 3px solid rgba(0,212,255,0.4); padding-left: 8px; }
.drill-hint { color: #00d4ff; font-size: 14px; opacity: 0; transition: opacity 0.2s; }
.chart-card:hover .drill-hint { opacity: 1; }
</style>
