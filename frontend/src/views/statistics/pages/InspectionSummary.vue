<template>
  <div class="summary-page" v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无巡检数据" />
    <template v-else-if="data">
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body"><span class="stat-value cyan">{{ data.totalTasks }}</span><span class="stat-label">任务总数</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow green"></div>
            <div class="stat-body"><span class="stat-value green">{{ ((data.completionRate || 0) * 100).toFixed(1) }}%</span><span class="stat-label">完成率</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow red"></div>
            <div class="stat-body"><span class="stat-value red">{{ data.anomalyCount }}</span><span class="stat-label">异常数</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow orange"></div>
            <div class="stat-body"><span class="stat-value orange">{{ ((data.anomalyRate || 0) * 100).toFixed(1) }}%</span><span class="stat-label">异常率</span></div>
          </div>
        </el-col>
      </el-row>
      <div class="chart-grid">
        <div class="chart-card">
          <div class="chart-title">巡检完成率</div>
          <div :ref="gauge.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card" @click="$emit('drill-down', { key: 'inspection-anomaly' })">
          <div class="chart-title">异常趋势 <span class="drill-hint">›</span></div>
          <div :ref="trend.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card">
          <div class="chart-title">巡检完成趋势</div>
          <div :ref="completion.bindChart" style="height: 260px"></div>
        </div>
        <div class="chart-card">
          <div class="chart-title">环比对比</div>
          <div :ref="comp.bindChart" style="height: 260px"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import { getInspectionStats } from '@/api/iot/statistics'
import { useChart } from '../composables/useChart'
import { gaugeOption, lineOption, barOption, dualBarOption } from '../composables/chartOptions'

const props = defineProps({ startDate: String, endDate: String, granularity: String, pageKey: String })
defineEmits(['drill-down'])
const loading = ref(false); const data = ref(null)
const gauge = useChart(); const trend = useChart(); const completion = useChart(); const comp = useChart()

async function fetch() {
  if (!props.startDate) return
  loading.value = true
  try {
    data.value = await getInspectionStats({ startDate: props.startDate, endDate: props.endDate, granularity: props.granularity })
    await nextTick(); render()
  } catch (e) { console.error('Failed to load inspection statistics:', e); data.value = null } finally { loading.value = false }
}
function render() {
  if (!data.value) return
  const d = data.value
  gauge.initChart(); gauge.setOption(gaugeOption((d.completionRate || 0) * 100, '完成率'))
  trend.initChart(); trend.setOption(lineOption(
    (d.anomalyTrend || []).map(t => t.period), (d.anomalyTrend || []).map(t => t.value), '异常数'
  ))
  completion.initChart(); completion.setOption(barOption(
    (d.completionTrend || []).map(t => t.period), (d.completionTrend || []).map(t => t.value)
  ))
  comp.initChart(); comp.setOption(dualBarOption(
    ['巡检任务'], [d.currentPeriodTotal || 0], [d.prevPeriodTotal || 0]
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
.chart-card:hover { border-color: rgba(0,212,255,0.3); background: rgba(0,212,255,0.04); }
.chart-title { font-size: 13px; color: #ccd6f6; font-weight: 500; margin-bottom: 8px; display: flex; justify-content: space-between; border-left: 3px solid rgba(0,212,255,0.4); padding-left: 8px; }
.drill-hint { color: #00d4ff; font-size: 14px; opacity: 0; transition: opacity 0.2s; }
.chart-card:hover .drill-hint { opacity: 1; }
</style>
