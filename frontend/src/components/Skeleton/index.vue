<template>
  <div class="skeleton" :class="{ 'skeleton--animated': animated }">
    <!-- Table variant -->
    <div v-if="variant === 'table'" class="skeleton-table">
      <div v-for="r in rows" :key="r" class="skeleton-table__row">
        <div v-for="c in columns" :key="c" class="skeleton-table__cell">
          <div class="skeleton-bar" />
        </div>
      </div>
    </div>

    <!-- Card variant -->
    <div v-else-if="variant === 'card'" class="skeleton-card">
      <div class="skeleton-bar skeleton-card__header" />
      <div class="skeleton-bar skeleton-card__line" />
      <div class="skeleton-bar skeleton-card__line skeleton-card__line--short" />
    </div>

    <!-- Chart variant -->
    <div v-else-if="variant === 'chart'" class="skeleton-chart">
      <div class="skeleton-bar skeleton-chart__area" />
      <div class="skeleton-chart__axis">
        <div class="skeleton-bar skeleton-chart__tick" />
        <div class="skeleton-bar skeleton-chart__tick" />
        <div class="skeleton-bar skeleton-chart__tick" />
        <div class="skeleton-bar skeleton-chart__tick" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  variant:  { type: String,  default: 'table' },
  rows:     { type: Number,  default: 5 },
  columns:  { type: Number,  default: 4 },
  animated: { type: Boolean, default: false },
})
</script>

<style scoped>
.skeleton-bar {
  height: 14px;
  border-radius: 4px;
  background-color: var(--el-fill-color);
}

.skeleton--animated .skeleton-bar {
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.4; }
}

/* Table */
.skeleton-table__row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}

.skeleton-table__cell {
  flex: 1;
}

.skeleton-table__row:nth-child(odd) .skeleton-bar {
  width: 70%;
}

.skeleton-table__row:nth-child(even) .skeleton-bar {
  width: 55%;
}

/* Card */
.skeleton-card {
  padding: 16px;
}

.skeleton-card__header {
  width: 40%;
  height: 18px;
  margin-bottom: 16px;
}

.skeleton-card__line {
  width: 100%;
  margin-bottom: 10px;
}

.skeleton-card__line--short {
  width: 60%;
}

/* Chart */
.skeleton-chart {
  padding: 16px;
}

.skeleton-chart__area {
  width: 100%;
  height: 150px;
  margin-bottom: 12px;
}

.skeleton-chart__axis {
  display: flex;
  gap: 24px;
}

.skeleton-chart__tick {
  width: 30px;
  height: 10px;
}
</style>
