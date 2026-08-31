<template>
  <div class="kpi-strip">
    <div
      v-for="(card, i) in cards"
      :key="card.key"
      class="kpi-card kpi-card--clickable"
      :style="{ animationDelay: `${i * 0.08}s` }"
      @click="navigate(card)"
    >
      <div class="kpi-glow"></div>
      <div class="kpi-icon-wrap" :style="{ background: card.iconBg }">
        <el-icon :size="22"><component :is="card.icon" /></el-icon>
      </div>
      <div class="kpi-data">
        <span class="kpi-value" :style="{ color: card.valueColor || '#e6f1ff' }">
          {{ formatValue(card) }}
        </span>
        <span class="kpi-label">{{ card.label }}</span>
      </div>
      <svg v-if="card.corner" class="kpi-corner" width="12" height="12" viewBox="0 0 12 12">
        <path d="M0 0 L12 0 L12 4 L4 4 L4 12 L0 12 Z" :fill="card.corner" opacity="0.15"/>
      </svg>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Bell, Tickets, Checked, Cpu } from '@element-plus/icons-vue'

const router = useRouter()

const props = defineProps({
  devices: { type: Object, default: () => ({ total: 0, online: 0, availability: 0 }) },
  alarms: { type: Object, default: () => ({ today: 0, handlingRate: 0 }) },
  pendingAlarms: { type: Number, default: 0 },
  workOrders: { type: Object, default: () => ({ open: 0 }) },
  inspections: { type: Object, default: () => ({ completionRate: 0 }) }
})

const animated = ref({})

const cards = [
  {
    key: 'devices', label: '设备总数', icon: Cpu,
    iconBg: 'linear-gradient(135deg, rgba(0,240,255,0.12), rgba(0,180,216,0.06))',
    corner: '#00f0ff',
    getValue: () => props.devices.total,
    format: v => v,
    route: '/ledger'
  },
  {
    key: 'availability', label: '设备在线率', icon: Monitor,
    iconBg: 'linear-gradient(135deg, rgba(0,230,118,0.12), rgba(0,200,83,0.06))',
    corner: '#00e676',
    getValue: () => props.devices.availability,
    format: v => (v * 100).toFixed(1) + '%',
    valueColor: () => {
      const r = props.devices.availability
      if (r >= 0.95) return '#00e676'
      if (r >= 0.8) return '#ffab00'
      return '#ff5252'
    },
    route: '/ledger'
  },
  {
    key: 'alarmToday', label: '今日告警', icon: Bell,
    iconBg: 'linear-gradient(135deg, rgba(255,171,0,0.12), rgba(255,143,0,0.06))',
    corner: '#ffab00',
    getValue: () => props.alarms.today,
    format: v => v,
    route: '/alarm?tab=messages'
  },
  {
    key: 'pendingAlarm', label: '待处理告警', icon: Tickets,
    iconBg: 'linear-gradient(135deg, rgba(255,82,82,0.12), rgba(244,67,54,0.06))',
    corner: '#ff5252',
    getValue: () => props.pendingAlarms,
    format: v => v,
    valueColor: () => props.pendingAlarms > 0 ? '#ff5252' : '#e6f1ff',
    route: '/alarm?tab=messages&state=1'
  },
  {
    key: 'openWork', label: '待办工单', icon: Tickets,
    iconBg: 'linear-gradient(135deg, rgba(124,77,255,0.12), rgba(101,31,255,0.06))',
    corner: '#7c4dff',
    getValue: () => props.workOrders.open,
    format: v => v,
    route: '/workorder'
  },
  {
    key: 'inspection', label: '巡检完成率', icon: Checked,
    iconBg: 'linear-gradient(135deg, rgba(0,230,118,0.12), rgba(105,240,174,0.06))',
    corner: '#69f0ae',
    getValue: () => props.inspections.completionRate,
    format: v => (v * 100).toFixed(1) + '%',
    route: '/inspection'
  }
]

function navigate(card) {
  if (card.route) router.push(card.route)
}

function formatValue(card) {
  const raw = card.getValue()
  const anim = animated.value[card.key]
  const v = anim !== undefined ? anim : raw
  return card.format(v)
}

function animateValues() {
  const duration = 800
  const start = performance.now()
  const from = {}
  const to = {}
  for (const card of cards) {
    from[card.key] = animated.value[card.key] ?? 0
    to[card.key] = card.getValue()
  }
  function step(now) {
    const elapsed = now - start
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    const current = {}
    for (const card of cards) {
      current[card.key] = from[card.key] + (to[card.key] - from[card.key]) * eased
    }
    animated.value = current
    if (progress < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

defineExpose({ animateValues })

onMounted(() => animateValues())
</script>

<style scoped>
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  position: relative;
  z-index: 1;
}

.kpi-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  box-sizing: border-box;
  background: linear-gradient(135deg, rgba(10,25,47,0.95), rgba(17,34,56,0.9));
  border: 1px solid rgba(0, 240, 255, 0.1);
  border-radius: 10px;
  overflow: hidden;
  animation: kpiEnter 0.5s ease-out both;
  transition: all 0.3s ease;
}

.kpi-card:hover {
  border-color: rgba(0, 240, 255, 0.3);
  box-shadow: 0 0 24px rgba(0, 240, 255, 0.06);
  transform: translateY(-1px);
}

.kpi-card--clickable {
  cursor: pointer;
}
.kpi-card--clickable:hover {
  border-color: rgba(0, 240, 255, 0.5);
  box-shadow: 0 0 32px rgba(0, 240, 255, 0.1);
  transform: translateY(-2px);
}

.kpi-glow {
  position: absolute;
  top: -40%;
  right: -15%;
  width: 80px;
  height: 80px;
  background: radial-gradient(circle, rgba(0, 240, 255, 0.04) 0%, transparent 70%);
  pointer-events: none;
}

.kpi-icon-wrap {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #00f0ff;
  flex-shrink: 0;
}

.kpi-data {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.kpi-value {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 24px;
  font-weight: 700;
  color: #e6f1ff;
  line-height: 1.1;
}

.kpi-label {
  font-size: 12px;
  color: #8892b0;
  margin-top: 4px;
  letter-spacing: 0.5px;
}

.kpi-corner {
  position: absolute;
  top: 0;
  right: 0;
  pointer-events: none;
}

@keyframes kpiEnter {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
