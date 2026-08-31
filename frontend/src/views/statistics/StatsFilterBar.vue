<template>
  <div class="filter-bar">
    <el-date-picker
      v-model="dates"
      type="daterange"
      range-separator="-"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      :disabled-date="disabledDate"
      @change="emitChange"
      style="width: 260px"
    />
    <el-select v-model="gran" style="width: 120px; margin-left: 12px" @change="emitChange">
      <el-option label="按天" value="DAY" />
      <el-option label="按小时" value="HOUR" />
      <el-option label="按周" value="WEEK" />
      <el-option label="按月" value="MONTH" />
    </el-select>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import dayjs from 'dayjs'

const emit = defineEmits(['change'])

const dates = ref([dayjs().subtract(7, 'day').toDate(), dayjs().toDate()])
const gran = ref('DAY')

function disabledDate(time) {
  const now = Date.now()
  const year = 365 * 24 * 3600 * 1000
  return time.getTime() > now || time.getTime() < now - year
}

function emitChange() {
  emit('change', {
    startDate: dayjs(dates.value[0]).format('YYYY-MM-DD'),
    endDate: dayjs(dates.value[1]).format('YYYY-MM-DD'),
    granularity: gran.value
  })
}

onMounted(() => emitChange())
</script>

<style scoped>
.filter-bar { display: flex; align-items: center; }
</style>
