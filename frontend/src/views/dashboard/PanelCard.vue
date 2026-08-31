<template>
  <div class="panel">
    <div class="panel-header">
      <span class="panel-title"><slot name="title" /></span>
      <span v-if="$slots.badge" class="panel-badge"><slot name="badge" /></span>
      <span v-if="$slots.meta" class="panel-meta"><slot name="meta" /></span>
    </div>
    <div class="chart-area"><slot /></div>
  </div>
</template>

<style scoped>
.panel {
  height: 100%;
  box-sizing: border-box;
  background: linear-gradient(135deg, rgba(10,25,47,0.95), rgba(17,34,56,0.9));
  border: 1px solid rgba(0, 240, 255, 0.1);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 240, 255, 0.08);
}

.panel-title {
  font-size: 14px;
  color: #ccd6f6;
  font-weight: 600;
}

.panel-badge {
  font-family: 'Consolas', monospace;
  font-size: 10px;
  color: #00f0ff;
  letter-spacing: 1px;
  padding: 2px 8px;
  border: 1px solid rgba(0, 240, 255, 0.25);
  border-radius: 8px;
}

.panel-meta {
  font-size: 12px;
  color: #8892b0;
}
.panel-meta :deep(strong) {
  color: #00e676;
  font-family: 'Consolas', monospace;
}

.chart-area {
  flex: 1;
  min-height: 0;
}
</style>
