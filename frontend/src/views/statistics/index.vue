<template>
  <div class="ops-container">
    <div class="bg-grid"></div>
    <div class="bg-scanline"></div>

    <div class="page-header">
      <h1 class="page-title">
        <span class="title-bar"></span> 统计报表
        <span class="title-sub">实时数据分析</span>
      </h1>
      <div class="header-line"></div>
    </div>
    <StatsFilterBar @change="onFilterChange" />
    <div class="stats-layout">
      <div class="stats-left">
        <StatsNavTree :activeCategory="activeCategory" :activeSubKey="activeSubKey" @navigate="onNavigate" />
      </div>
      <div class="stats-right">
        <StatsContentArea ref="contentArea" :filters="filters" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import StatsFilterBar from './StatsFilterBar.vue'
import StatsNavTree from './StatsNavTree.vue'
import StatsContentArea from './StatsContentArea.vue'


const contentArea = ref(null)
const activeCategory = ref('alarm')
const activeSubKey = ref('summary')
const filters = reactive({ startDate: null, endDate: null, granularity: 'DAY' })

function onFilterChange(f) { Object.assign(filters, f) }
function onNavigate({ category, subKey }) {
  activeCategory.value = category; activeSubKey.value = subKey
  contentArea.value?.navigateTo(category, subKey)
}
</script>

<style scoped>
.ops-container {
  position: relative; margin: -20px; min-height: calc(100vh - 56px); padding: 24px;
  background: #0a0e17; overflow: hidden;
  font-family: 'Courier New', 'Source Code Pro', 'Consolas', monospace;
}
.bg-grid {
  position: absolute; inset: 0; pointer-events: none;
  background-image: linear-gradient(rgba(79,195,247,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79,195,247,0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}
.bg-scanline {
  position: absolute; inset: 0; pointer-events: none;
  background: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(79,195,247,0.008) 2px, rgba(79,195,247,0.008) 4px);
}
.page-header { position: relative; z-index: 1; margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 600; color: #e6f1ff; letter-spacing: 2px; margin: 0; }
.title-bar { display: inline-block; width: 4px; height: 22px; background: linear-gradient(180deg, #00f0ff, #0080ff); border-radius: 2px; vertical-align: middle; margin-right: 10px; }
.title-sub { font-size: 12px; color: #546e7a; margin-left: 12px; letter-spacing: 1px; }
.header-line { margin-top: 8px; height: 1px; background: linear-gradient(90deg, #4fc3f7 0%, transparent 100%); }
.stats-layout { display: flex; margin-top: 16px; min-height: calc(100vh - 180px); gap: 0; }
.stats-left { width: 220px; flex-shrink: 0; border-radius: 8px 0 0 8px; overflow: hidden; border: 1px solid #1e3a5f; border-right: none; }
.stats-right { flex: 1; border-radius: 0 8px 8px 0; overflow: hidden; border: 1px solid #1e3a5f; background: #0a0e17; }
</style>
