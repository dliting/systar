<template>
  <div class="control-command-input">
    <!-- YESNO: boolean on/off toggle -->
    <template v-if="effectiveViewType === 'YESNO'">
      <el-radio-group
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :size="size"
      >
        <el-radio-button value="true">开启</el-radio-button>
        <el-radio-button value="false">关闭</el-radio-button>
      </el-radio-group>
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="!modelValue"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- LIST: dropdown select -->
    <template v-else-if="effectiveViewType === 'LIST'">
      <el-select
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :size="size"
        :placeholder="placeholder || '请选择'"
        style="min-width:160px"
      >
        <el-option
          v-for="opt in options"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="!modelValue"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- SLIDER: numeric slider -->
    <template v-else-if="effectiveViewType === 'SLIDER'">
      <el-slider
        :model-value="numericValue"
        @update:model-value="onNumericChange"
        :min="sliderMin"
        :max="sliderMax"
        :step="dataType === 'INT' ? 1 : 0.1"
        :size="size"
        show-input
        style="min-width:180px"
      />
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="modelValue == null"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- PERCENT: percentage slider -->
    <template v-else-if="effectiveViewType === 'PERCENT'">
      <el-slider
        :model-value="numericValue"
        @update:model-value="onNumericChange"
        :min="0" :max="100" :step="1"
        :size="size"
        show-input
        style="min-width:180px"
      />
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="modelValue == null"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- INT / FLOAT: numeric input -->
    <template v-else-if="dataType === 'INT' || dataType === 'FLOAT'">
      <el-input-number
        :model-value="numericValue"
        @update:model-value="onNumericChange"
        :precision="dataType === 'INT' ? 0 : 2"
        :step="dataType === 'INT' ? 1 : 0.1"
        :size="size"
        :placeholder="placeholder || '输入数值'"
      />
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="modelValue == null || modelValue === ''"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- TEXTAREA: multi-line text -->
    <template v-else-if="effectiveViewType === 'TEXTAREA'">
      <el-input
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        type="textarea"
        :rows="3"
        :size="size"
        :placeholder="placeholder || '输入内容'"
        style="min-width:200px"
      />
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="!modelValue"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- PASSWORD: masked input -->
    <template v-else-if="effectiveViewType === 'PASSWORD'">
      <el-input
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        type="password"
        :size="size"
        :placeholder="placeholder || '输入密码'"
        show-password
        style="min-width:160px"
      />
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="!modelValue"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>

    <!-- TEXTFIELD / fallback: text input -->
    <template v-else>
      <el-input
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :size="size"
        :placeholder="placeholder || '输入命令值'"
        style="min-width:160px"
      />
      <el-button
        v-if="showExecute"
        type="primary" :size="size" :loading="loading"
        :disabled="!modelValue"
        @click="$emit('execute', modelValue)"
        style="margin-left:8px"
      >执行</el-button>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  dataType: { type: String, default: 'STRING' },
  viewType: { type: String, default: null },
  size: { type: String, default: 'default' },
  placeholder: { type: String, default: null },
  showExecute: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  options: { type: Array, default: () => [] },
  sliderMin: { type: Number, default: 0 },
  sliderMax: { type: Number, default: 100 }
})

const emit = defineEmits(['update:modelValue', 'execute'])

const effectiveViewType = computed(() => {
  if (props.viewType) return props.viewType
  if (props.dataType === 'BOOLEAN') return 'YESNO'
  return 'TEXTFIELD'
})

const numericValue = computed(() => {
  if (props.modelValue == null || props.modelValue === '') return undefined
  const n = Number(props.modelValue)
  return isNaN(n) ? undefined : n
})

function onNumericChange(val) {
  if (val == null || val === '') {
    emit('update:modelValue', '')
  } else {
    emit('update:modelValue', String(val))
  }
}
</script>

<style scoped>
.control-command-input {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
