<template>
  <div class="nav-tree">
    <el-input v-model="searchText" placeholder="搜索统计..." size="small" clearable class="nav-search" />
    <div v-for="cat in filteredCategories" :key="cat.key" class="nav-category">
      <div class="nav-cat-header" @click="toggle(cat)">
        <span class="nav-cat-arrow">{{ cat.expanded ? '▼' : '▶' }}</span>
        <span>{{ cat.label }}</span>
      </div>
      <div v-show="cat.expanded" class="nav-sub-items">
        <div v-for="sub in cat.children" :key="sub.key"
          class="nav-sub-item"
          :class="{ active: activeSubKey === sub.key && activeCategory === cat.key }"
          @click="select(cat.key, sub.key)">
          {{ sub.label }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({ activeCategory: String, activeSubKey: String })
const emit = defineEmits(['navigate'])

const searchText = ref('')

const categories = ref([
  { key: 'alarm', label: '告警分析', expanded: true, children: [
    { key: 'summary', label: '告警概览' },
    { key: 'level', label: '等级分布' },
    { key: 'trend', label: '趋势分析' },
    { key: 'top-devices', label: 'Top告警设备' }
  ]},
  { key: 'workorder', label: '工单分析', expanded: false, children: [
    { key: 'summary', label: '工单概览' },
    { key: 'aging', label: '老化分布' },
    { key: 'mttr', label: 'MTTR趋势' },
    { key: 'sla', label: 'SLA达标率' }
  ]},
  { key: 'inspection', label: '巡检分析', expanded: false, children: [
    { key: 'summary', label: '巡检概览' },
    { key: 'anomaly', label: '异常分析' }
  ]},
  { key: 'device', label: '设备运行', expanded: false, children: [
    { key: 'summary', label: '在线率概览' },
    { key: 'ranking', label: '可用率排行' },
    { key: 'detail', label: '设备详情' }
  ]},
  { key: 'maintenance', label: '维护分析', expanded: false, children: [
    { key: 'summary', label: '维护概览' },
    { key: 'cost', label: '费用趋势' }
  ]},
  { key: 'dashboard', label: '综合看板', expanded: false, children: [
    { key: 'summary', label: 'KPI总览' }
  ]},
  { key: 'trend', label: '监测趋势', expanded: false, children: [
    { key: 'probe-trend', label: '探针趋势' },
    { key: 'control-trend', label: '控制器趋势' },
    { key: 'intraday', label: '日内走势' }
  ]}
])

function toggle(cat) {
  cat.expanded = !cat.expanded
}

function select(catKey, subKey) {
  emit('navigate', { category: catKey, subKey })
}

const filteredCategories = computed(() => {
  if (!searchText.value) return categories.value
  const q = searchText.value.toLowerCase()
  return categories.value
    .map(c => ({ ...c, children: c.children.filter(s => s.label.toLowerCase().includes(q)) }))
    .filter(c => c.label.toLowerCase().includes(q) || c.children.length > 0)
})
</script>

<style scoped>
.nav-tree {
  padding: 12px; background: #111827; min-height: 100%; user-select: none;
}
.nav-search { margin-bottom: 12px; }
.nav-category { margin-bottom: 2px; }
.nav-cat-header {
  padding: 7px 8px; cursor: pointer; font-size: 12px; font-weight: 600;
  color: #8892b0; border-radius: 5px; display: flex; align-items: center; gap: 6px;
  transition: all 0.15s;
}
.nav-cat-header:hover { background: rgba(0,212,255,0.06); color: #00d4ff; }
.nav-cat-arrow { font-size: 8px; width: 12px; text-align: center; }
.nav-sub-items { margin-left: 8px; margin-top: 1px; margin-bottom: 6px; }
.nav-sub-item {
  padding: 5px 8px 5px 16px; font-size: 11px; color: #8892b0; cursor: pointer;
  border-radius: 3px; border-left: 2px solid transparent; transition: all 0.12s;
}
.nav-sub-item:hover { color: #00d4ff; background: rgba(0,212,255,0.04); }
.nav-sub-item.active {
  color: #00d4ff; background: rgba(0,212,255,0.08);
  border-left-color: #00d4ff; font-weight: 600;
}
</style>
