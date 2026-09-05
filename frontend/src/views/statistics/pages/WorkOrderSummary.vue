<template>
  <div class="summary-page" v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无工单数据" />
    <template v-else-if="data">
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body"><span class="stat-value cyan">{{ openCount }}</span><span class="stat-label">进行中</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow orange"></div>
            <div class="stat-body"><span class="stat-value orange">{{ data.mttrHours?.toFixed(1) || '0.0' }}h</span><span class="stat-label">MTTR</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow green"></div>
            <div class="stat-body"><span class="stat-value green">{{ ((data.slaComplianceRate || 0) * 100).toFixed(1) }}%</span><span class="stat-label">SLA达标率</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body"><span class="stat-value cyan">{{ data.currentPeriodTotal || 0 }}</span><span class="stat-label">本期工单</span></div>
          </div>
        </el-col>
      </el-row>
      <div class="chart-grid">
        <div class="chart-card" @click="$emit('drill-down', { key: 'workorder-aging' })">
          <div class="chart-title">工单老化分布 <span class="drill-hint">›</span></div>
          <div :ref="aging.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card">
          <div class="chart-title">工单状态分布 <span class="drill-hint">›</span></div>
          <div :ref="status.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card" @click="$emit('drill-down', { key: 'workorder-mttr' })">
          <div class="chart-title">MTTR/趋势 <span class="drill-hint">›</span></div>
          <div :ref="mttr.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card" @click="$emit('drill-down', { key: 'workorder-sla' })">
          <div class="chart-title">SLA达标率 <span class="drill-hint">›</span></div>
          <div :ref="sla.bindChart" style="height: 260px"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { getWorkOrderStats } from '@/api/iot/statistics'
import { useChart } from '../composables/useChart'
import { funnelOption, pieOption, lineOption, gaugeOption } from '../composables/chartOptions'

const props = defineProps({ startDate: String, endDate: String, granularity: String, pageKey: String })
defineEmits(['drill-down'])

const loading = ref(false); const data = ref(null)
const aging = useChart(); const status = useChart(); const mttr = useChart(); const sla = useChart()

const openCount = computed(() => {
  if (!data.value?.byStatus) return 0
  const s = data.value.byStatus
  return (s.CREATED || 0) + (s.ASSIGNED || 0) + (s.PROCESSING || 0)
})

async function fetch() {
  if (!props.startDate) return
  loading.value = true
  try {
    data.value = await getWorkOrderStats({ startDate: props.startDate, endDate: props.endDate, granularity: props.granularity })
    await nextTick(); render()
  } catch (e) { console.error('Failed to load work order statistics:', e); data.value = null } finally { loading.value = false }
}

function render() {
  if (!data.value) return
  const d = data.value
  const agingData = Object.entries(d.agingDistribution || {}).map(([k, v]) => ({ name: agingLabel(k), value: v }))
  aging.initChart(); aging.setOption(funnelOption(agingData))
  status.initChart(); status.setOption(pieOption(
    Object.entries(d.byStatus || {}).map(([k, v]) => ({ name: k, value: v }))
  ))
  mttr.initChart(); mttr.setOption(lineOption(
    (d.trend || []).map(t => t.period), (d.trend || []).map(t => t.value), '工单趋势'
  ))
  sla.initChart(); sla.setOption(gaugeOption(d.slaComplianceRate ? d.slaComplianceRate * 100 : 0, '达标率'))
}

function agingLabel(k) {
  return { within24h: '< 24h', '24h-72h': '24-72h', '72h-7d': '72h-7d', over7d: '> 7d' }[k] || k
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
.chart-card:hover { border-color: rgba(0,212,255,0.3); background: rgba(0,212,255,0.04); }
.chart-title { font-size: 13px; color: #ccd6f6; font-weight: 500; margin-bottom: 8px; display: flex; justify-content: space-between; border-left: 3px solid rgba(0,212,255,0.4); padding-left: 8px; }
.drill-hint { color: #00d4ff; font-size: 14px; opacity: 0; transition: opacity 0.2s; }
.chart-card:hover .drill-hint { opacity: 1; }
</style>
