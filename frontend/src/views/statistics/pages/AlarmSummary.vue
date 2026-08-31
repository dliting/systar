<template>
  <div class="summary-page" v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无告警数据" />
    <template v-else-if="data">
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body"><span class="stat-value cyan">{{ data.totalAlarms }}</span><span class="stat-label">告警总数</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow red"></div>
            <div class="stat-body"><span class="stat-value red">{{ data.pendingAlarms }}</span><span class="stat-label">待处理</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow green"></div>
            <div class="stat-body"><span class="stat-value green">{{ ((data.handlingRate || 0) * 100).toFixed(1) }}%</span><span class="stat-label">处理率</span></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="stat-glow cyan"></div>
            <div class="stat-body">
              <span class="stat-value cyan">{{ data.trend?.length || 0 }}</span>
              <span class="stat-label">趋势点数</span>
              <span class="stat-change" :class="periodChange > 0 ? 'up' : 'down'" v-if="periodChange !== 0">
                {{ periodChange > 0 ? '↑' : '↓' }} {{ Math.abs(periodChange) }}%
              </span>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="chart-grid">
        <div class="chart-card" @click="$emit('drill-down', { key: 'alarm-level' })">
          <div class="chart-title">告警等级分布 <span class="drill-hint">›</span></div>
          <div ref="pieRef" style="height: 260px"></div>
        </div>
        <div class="chart-card" @click="$emit('drill-down', { key: 'alarm-trend' })">
          <div class="chart-title">告警趋势分析 <span class="drill-hint">›</span></div>
          <div ref="trendRef" style="height: 260px"></div>
        </div>
        <div class="chart-card" @click="$emit('drill-down', { key: 'alarm-top-devices' })">
          <div class="chart-title">Top告警设备 <span class="drill-hint">›</span></div>
          <div ref="topRef" style="height: 260px"></div>
        </div>
        <div class="chart-card">
          <div class="chart-title">环比对比</div>
          <div ref="compRef" style="height: 260px"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import { getAlarmStats } from '@/api/iot/statistics'
import { useChart } from '../composables/useChart'
import { pieOption, mixedBarLineOption, horizontalBarOption, dualBarOption } from '../composables/chartOptions'

const props = defineProps({ startDate: String, endDate: String, granularity: String, pageKey: String })
defineEmits(['drill-down'])

const loading = ref(false)
const data = ref(null)
const periodChange = ref(0)

const pie = useChart(); const trend = useChart(); const top = useChart(); const comp = useChart()

async function fetch() {
  if (!props.startDate) return
  loading.value = true
  try {
    data.value = await getAlarmStats({ startDate: props.startDate, endDate: props.endDate, granularity: props.granularity })
    if (data.value) {
      const cur = data.value.currentPeriodCount || 0
      const prev = data.value.prevPeriodCount || 0
      periodChange.value = prev > 0 ? Math.round(((cur - prev) / prev) * 100) : 0
    }
    await nextTick()
    render()
  } catch { data.value = null } finally { loading.value = false }
}

function render() {
  if (!data.value) return
  const d = data.value

  pie.initChart(); pie.setOption(pieOption(
    Object.entries(d.byLevel || {}).map(([k, v]) => ({ name: `等级${k}`, value: v }))
  ))

  trend.initChart(); trend.setOption(mixedBarLineOption(
    (d.trend || []).map(t => t.period),
    (d.trend || []).map(t => t.value),
    (d.trend || []).map(t => t.value),
    '告警数', '趋势'
  ))

  top.initChart(); top.setOption(horizontalBarOption(
    (d.topDevices || []).map(t => t.deviceName),
    (d.topDevices || []).map(t => t.alarmCount)
  ))

  comp.initChart(); comp.setOption(dualBarOption(
    ['告警'], [d.currentPeriodCount || 0], [d.prevPeriodCount || 0]
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
