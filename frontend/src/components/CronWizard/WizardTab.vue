<template>
  <div class="wizard-tab">
    <el-form-item label="频率">
      <el-radio-group v-model="state.frequency">
        <el-radio-button value="daily">每天</el-radio-button>
        <el-radio-button value="weekly">每周</el-radio-button>
        <el-radio-button value="monthly">每月</el-radio-button>
      </el-radio-group>
    </el-form-item>

    <el-form-item label="执行时间">
      <el-time-picker
        v-model="timeDate"
        format="HH:mm"
        placeholder="选择时间"
        :clearable="false"
      />
    </el-form-item>

    <!-- Weekly section -->
    <div v-show="state.frequency === 'weekly'" class="weekly-section">
      <el-form-item label="星期">
        <el-checkbox-group v-model="state.weekDays">
          <el-checkbox-button v-for="d in 7" :key="d" :value="d">
            {{ weekDayLabels[d - 1] }}
          </el-checkbox-button>
        </el-checkbox-group>
      </el-form-item>
      <el-alert
        v-if="state.weekDays.length === 0"
        type="warning"
        :closable="false"
        show-icon
      >
        请至少选择一个星期
      </el-alert>
    </div>

    <!-- Monthly section -->
    <div v-show="state.frequency === 'monthly'" class="monthly-section">
      <el-form-item label="日期">
        <el-select v-model="state.monthDay">
          <el-option v-for="d in 31" :key="d" :label="d" :value="d" />
          <el-option label="最后一天" value="LAST_DAY" />
        </el-select>
      </el-form-item>
      <el-text v-if="monthDayWarning" type="warning" size="small">
        部分月份无此日期，将自动跳过
      </el-text>
    </div>

    <!-- Preview -->
    <div class="cron-preview-section">
      <el-text type="info" size="small">表达式：{{ currentCron || '-' }}</el-text>
      <el-text v-if="previewNext" type="success" size="small">
        下次执行：{{ previewNext }}
      </el-text>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { wizardToCron, cronToWizard, defaultWizardState, LAST_DAY } from './cron-utils'

const props = defineProps({
  modelValue: { type: String, default: '' },
  previewNext: { type: [String, Object, Array], default: null }
})

const emit = defineEmits(['update:modelValue'])

const weekDayLabels = ['一', '二', '三', '四', '五', '六', '日']

const state = ref(defaultWizardState())

const timeDate = computed({
  get() {
    const [h, m] = state.value.time.split(':').map(Number)
    const d = new Date()
    d.setHours(h, m, 0, 0)
    return d
  },
  set(val) {
    if (val) {
      const h = String(val.getHours()).padStart(2, '0')
      const m = String(val.getMinutes()).padStart(2, '0')
      state.value.time = `${h}:${m}`
    }
  }
})

const currentCron = computed(() => wizardToCron(state.value))

const monthDayWarning = computed(() =>
  state.value.frequency === 'monthly' &&
  state.value.monthDay !== LAST_DAY &&
  [29, 30, 31].includes(state.value.monthDay)
)

// 300ms debounce emit
let debounceTimer = null
watch(currentCron, (cron) => {
  clearTimeout(debounceTimer)
  if (!cron) return
  debounceTimer = setTimeout(() => {
    emit('update:modelValue', cron)
  }, 300)
})
onUnmounted(() => clearTimeout(debounceTimer))

// Initialize from existing cron on mount
watch(() => props.modelValue, (cron) => {
  const parsed = cronToWizard(cron)
  if (parsed) state.value = { ...defaultWizardState(), ...parsed }
}, { immediate: true })
</script>

<style scoped>
.wizard-tab { padding-top: 4px; }
.weekly-section, .monthly-section { margin-top: 4px; }
.cron-preview-section { margin-top: 12px; display: flex; gap: 16px; }
</style>