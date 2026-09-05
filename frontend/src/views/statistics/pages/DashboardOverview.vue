<template>
  <div class="summary-page" v-loading="loading">
    <el-empty v-if="!loading && !data" description="暂无看板数据" />
    <template v-else-if="data">
      <div class="kpi-grid">
        <div class="kpi-gauge" :ref="gauges[0].bindChart" style="height: 220px"></div>
        <div class="kpi-gauge" :ref="gauges[1].bindChart" style="height: 220px"></div>
        <div class="kpi-gauge" :ref="gauges[2].bindChart" style="height: 220px"></div>
        <div class="kpi-gauge" :ref="gauges[3].bindChart" style="height: 220px"></div>
        <div class="kpi-gauge" :ref="gauges[4].bindChart" style="height: 220px"></div>
        <div class="kpi-gauge" :ref="gauges[5].bindChart" style="height: 220px"></div>
      </div>
      <el-row :gutter="16" style="margin-top:20px">
        <el-col :span="12">
          <div class="chart-card">
            <div class="chart-title">Top告警设备</div>
            <div :ref="top.bindChart" style="height: 300px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="chart-card">
            <div class="chart-title">告警趋势(近7天)</div>
            <div :ref="trend.bindChart" style="height: 300px"></div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { getDashboardStats } from '@/api/iot/statistics'
import { useChart } from '../composables/useChart'
import { gaugeOption, horizontalBarOption, lineOption } from '../composables/chartOptions'

const props = defineProps({ startDate: String, endDate: String, granularity: String, pageKey: String })
const loading = ref(false); const data = ref(null)
const gauges = Array.from({ length: 6 }, () => useChart())
const top = useChart(); const trend = useChart()

async function fetch() {
  loading.value = true
  try {
    data.value = await getDashboardStats()
    await nextTick(); render()
  } catch (e) { console.error('Failed to load dashboard statistics:', e); data.value = null } finally { loading.value = false }
}
function render() {
  if (!data.value) return
  const d = data.value
  const alarms = d.alarms || {}
  const wo = d.workOrders || {}
  const ins = d.inspections || {}
  const dev = d.devices || {}
  const kpis = [
    { value: alarms.today || 0, title: '今日告警', max: 50 },
    { value: (alarms.handlingRate || 0) * 100, title: '处理率', max: 100 },
    { value: (wo.slaCompliance || 0) * 100, title: 'SLA达标', max: 100 },
    { value: wo.open || 0, title: '进行中工单', max: 50 },
    { value: (ins.completionRate || 0) * 100, title: '巡检完成', max: 100 },
    { value: (dev.availability || 0) * 100, title: '设备可用', max: 100 }
  ]
  kpis.forEach((kpi, i) => {
    gauges[i].initChart(); gauges[i].setOption(gaugeOption(kpi.value, kpi.title, kpi.max))
  })
  top.initChart(); top.setOption(horizontalBarOption(
    (d.topAlarmDevices || []).map(t => t.deviceName),
    (d.topAlarmDevices || []).map(t => t.alarmCount)
  ))
  trend.initChart(); trend.setOption(lineOption(
    (alarms.trend || []).map(t => t.period), (alarms.trend || []).map(t => t.value)
  ))
}
onMounted(fetch)
</script>

<style scoped>
.summary-page { min-height: 300px; }
.kpi-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.kpi-gauge { padding: 0; }
.chart-card {
  background: rgba(0,212,255,0.02); border: 1px solid rgba(0,212,255,0.08);
  border-radius: 8px; padding: 12px;
}
.chart-title { font-size: 13px; color: #ccd6f6; font-weight: 500; margin-bottom: 8px; border-left: 3px solid rgba(0,212,255,0.4); padding-left: 8px; }
</style>
