<template>
  <div class="summary-page" v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无维护数据" />
    <template v-else-if="data">
      <el-row :gutter="16" class="stat-row">
        <el-col :span="8">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body"><span class="stat-value cyan">{{ data.totalRecords }}</span><span class="stat-label">维护次数</span></div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card"><div class="stat-glow orange"></div>
            <div class="stat-body"><span class="stat-value orange">{{ data.totalCost?.toFixed(2) || '0.00' }}</span><span class="stat-label">总费用(元)</span></div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card"><div class="stat-glow green"></div>
            <div class="stat-body"><span class="stat-value green">{{ typeCount }}</span><span class="stat-label">维护类型</span></div>
          </div>
        </el-col>
      </el-row>
      <div class="chart-grid">
        <div class="chart-card" @click="$emit('drill-down', { key: 'maintenance-cost' })">
          <div class="chart-title">维护费用趋势 <span class="drill-hint">›</span></div>
          <div :ref="cost.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card">
          <div class="chart-title">维护类型分布</div>
          <div :ref="type.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card full">
          <div class="chart-title">费用按类型</div>
          <div :ref="costType.bindChart" style="height: 260px"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { getMaintenanceStats } from '@/api/iot/statistics'
import { useChart } from '../composables/useChart'
import { lineOption, pieOption, horizontalBarOption } from '../composables/chartOptions'

const props = defineProps({ startDate: String, endDate: String, granularity: String, pageKey: String })
defineEmits(['drill-down'])
const loading = ref(false); const data = ref(null)
const cost = useChart(); const type = useChart(); const costType = useChart()

const typeCount = computed(() => data.value?.byType ? Object.keys(data.value.byType).length : 0)

async function fetch() {
  if (!props.startDate) return
  loading.value = true
  try {
    data.value = await getMaintenanceStats({ startDate: props.startDate, endDate: props.endDate, granularity: props.granularity })
    await nextTick(); render()
  } catch (e) { console.error('Failed to load maintenance statistics:', e); data.value = null } finally { loading.value = false }
}
function render() {
  if (!data.value) return
  const d = data.value
  cost.initChart(); cost.setOption(lineOption(
    (d.frequencyTrend || []).map(t => t.period), (d.frequencyTrend || []).map(t => t.value), '维护次数'
  ))
  type.initChart(); type.setOption(pieOption(
    Object.entries(d.byType || {}).map(([k, v]) => ({ name: k, value: v }))
  ))
  costType.initChart(); costType.setOption(horizontalBarOption(
    Object.keys(d.costByType || {}), Object.values(d.costByType || {}).map(v => Number(v) || 0), '费用(元)'
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
