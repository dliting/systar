<template>
  <div class="duration-input">
    <el-input-number
      v-model="amount"
      :min="amountMin"
      :max="amountMax"
      :precision="allowDecimal ? 1 : 0"
      :step="1"
      :disabled="disabled"
      controls-position="right"
      class="duration-input__number"
    />
    <el-select
      v-model="unit"
      :disabled="disabled"
      class="duration-input__unit"
    >
      <el-option
        v-for="opt in UNIT_OPTIONS"
        :key="opt.value"
        :value="opt.value"
        :label="opt.label"
      />
    </el-select>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { parseDuration, formatDuration, convertUnit, toSeconds, smartUnit, UNIT_OPTIONS } from './duration-utils'

const props = defineProps({
  modelValue:   { type: String,  default: '' },
  allowDecimal: { type: Boolean, default: false },
  min:          { type: Number,  default: 1 },
  max:          { type: Number,  default: 86400 },
  disabled:     { type: Boolean, default: false },
})

const emit  = defineEmits(['update:modelValue'])
const amount = ref(null)
const unit   = ref('s')

// Guards to prevent watch feedback loops
let skipAmountWatch = false
let skipUnitWatch   = false

// Compute min/max for the current unit
const amountMin = computed(() => {
  if (props.min == null) return 0
  const converted = convertUnit(props.min, 's', unit.value)
  return props.allowDecimal ? Math.ceil(converted * 10) / 10 : Math.ceil(converted)
})

const amountMax = computed(() => {
  if (props.max == null) return Infinity
  const converted = convertUnit(props.max, 's', unit.value)
  return props.allowDecimal ? Math.floor(converted * 10) / 10 : Math.floor(converted)
})

// Normalize total seconds: clamp to min/max + pick best unit
function normalize(totalSeconds) {
  if (totalSeconds <= 0) return { amount: 0, unit: 's' }
  const clamped = Math.max(props.min, Math.min(totalSeconds, props.max))
  return smartUnit(clamped)
}

// Parse modelValue → internal state, then emit normalized value
function syncFromModel(val) {
  skipAmountWatch = true
  skipUnitWatch   = true
  try {
    if (!val) {
      amount.value = null
      unit.value   = 's'
      emit('update:modelValue', '')
      return
    }
    const parsed = parseDuration(val)
    if (!parsed) {
      amount.value = null
      unit.value   = 's'
      emit('update:modelValue', '')
      return
    }

    const totalSec   = toSeconds(parsed.amount, parsed.unit)
    const normalized = normalize(totalSec)

    amount.value = normalized.amount
    unit.value   = normalized.unit

    const formatted = formatDuration(normalized.amount, normalized.unit)
    emit('update:modelValue', formatted)
  } finally {
    // Reset guards on next tick so the queued watchers see the flag
    Promise.resolve().then(() => {
      skipAmountWatch = false
      skipUnitWatch   = false
    })
  }
}

// Initialize
syncFromModel(props.modelValue)

// Watch external modelValue changes
watch(() => props.modelValue, (val) => {
  const current  = amount.value != null ? formatDuration(amount.value, unit.value) : ''
  const incoming = val || ''
  if (current !== incoming) {
    syncFromModel(val)
  }
})

// When unit changes via user interaction, convert the amount and emit
watch(unit, (newUnit, oldUnit) => {
  if (skipUnitWatch) return
  if (amount.value == null || !oldUnit) return
  const converted = convertUnit(amount.value, oldUnit, newUnit)
  skipAmountWatch = true
  amount.value = props.allowDecimal
    ? Math.round(converted * 10) / 10
    : Math.round(converted)
  skipAmountWatch = false
  emitDuration()
})

// When amount changes via user interaction, emit
watch(amount, () => {
  if (skipAmountWatch) return
  emitDuration()
})

function emitDuration() {
  if (amount.value == null || amount.value === 0) {
    emit('update:modelValue', '')
    return
  }
  const totalSec = toSeconds(amount.value, unit.value)

  if (totalSec < props.min) {
    const norm = normalize(props.min)
    skipAmountWatch = true
    skipUnitWatch   = true
    amount.value = norm.amount
    unit.value   = norm.unit
    Promise.resolve().then(() => {
      skipAmountWatch = false
      skipUnitWatch   = false
    })
    emit('update:modelValue', formatDuration(norm.amount, norm.unit))
    return
  }
  if (totalSec > props.max) {
    const norm = normalize(props.max)
    skipAmountWatch = true
    skipUnitWatch   = true
    amount.value = norm.amount
    unit.value   = norm.unit
    Promise.resolve().then(() => {
      skipAmountWatch = false
      skipUnitWatch   = false
    })
    emit('update:modelValue', formatDuration(norm.amount, norm.unit))
    return
  }
  emit('update:modelValue', formatDuration(amount.value, unit.value))
}
</script>

<style scoped>
.duration-input {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.duration-input__number {
  flex: 1;
  min-width: 100px;
}
.duration-input__unit {
  width: 90px;
  flex-shrink: 0;
}
</style>
