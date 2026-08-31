<template>
  <div class="inline-edit" :class="{ 'is-disabled': disabled }">
    <!-- Switch type: always show switch, no click-to-edit -->
    <template v-if="type === 'switch'">
      <div class="inline-edit__switch">
        <span class="inline-edit__switch-tag" :class="value ? 'is-active' : 'is-inactive'">
          {{ value ? '是' : '否' }}
        </span>
        <el-switch
          :model-value="value"
          :disabled="disabled"
          @change="onSwitchChange"
        />
      </div>
    </template>

    <!-- Edit mode for text/number/select -->
    <template v-else-if="isEditing">
      <!-- Text input -->
      <el-input
        v-if="type === 'text'"
        ref="inputRef"
        v-model="editValue"
        size="small"
        @keydown="onKeyDown"
        @blur="onBlur"
      />

      <!-- Number input -->
      <el-input-number
        v-else-if="type === 'number'"
        ref="inputRef"
        v-model="editValue"
        size="small"
        :min="min"
        :max="max"
        controls-position="right"
        @keydown="onKeyDown"
        @blur="onBlur"
      />

      <!-- Select input -->
      <el-select
        v-else-if="type === 'select'"
        ref="inputRef"
        v-model="editValue"
        size="small"
        @change="onSelectChange"
        @visible-change="onSelectDropdownChange"
      >
        <el-option
          v-for="opt in options"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>

    <!-- Display mode -->
    <div
      v-else
      class="inline-edit__display"
      tabindex="0"
      role="button"
      @click="onDisplayClick"
      @keydown.enter="onDisplayClick"
    >
      <span class="inline-edit__text">{{ displayText }}</span>
      <el-icon class="inline-edit__icon"><Edit /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { Edit } from '@element-plus/icons-vue'

const BLUR_DEBOUNCE_MS = 150

const props = defineProps({
  value:       { type: [String, Number, Boolean], default: '' },
  type:        { type: String,   default: 'text' },
  options:     { type: Array,    default: () => [] },
  min:         { type: Number,   default: undefined },
  max:         { type: Number,   default: undefined },
  placeholder: { type: String,   default: '' },
  disabled:    { type: Boolean,  default: false },
})

const emit = defineEmits(['save'])

const isEditing = ref(false)
const editValue = ref(null)
const inputRef  = ref(null)

let blurTimer = null

// --- Computed ---

const displayText = computed(() => {
  if (props.type === 'select') {
    const match = props.options.find(opt => opt.value === props.value)
    if (match) return match.label
  }
  if (props.value === '' || props.value === null || props.value === undefined) {
    return props.placeholder || '—'
  }
  return String(props.value)
})

// --- Methods ---

function onDisplayClick() {
  if (props.disabled) return
  editValue.value = props.value
  isEditing.value = true
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.focus()
    }
  })
}

function onKeyDown(event) {
  if (event.key === 'Enter') {
    event.preventDefault()
    saveAndExit()
  } else if (event.key === 'Escape') {
    event.preventDefault()
    cancelEdit()
  }
}

function onBlur() {
  clearTimeout(blurTimer)
  blurTimer = setTimeout(() => {
    saveAndExit()
  }, BLUR_DEBOUNCE_MS)
}

function onSelectChange() {
  // For select, save immediately on change
  saveAndExit()
}

function onSelectDropdownChange(visible) {
  if (!visible && isEditing.value) cancelEdit()
}

function saveAndExit() {
  clearTimeout(blurTimer)
  const newValue = editValue.value
  if (newValue !== props.value) {
    emit('save', newValue)
  }
  isEditing.value = false
}

function cancelEdit() {
  clearTimeout(blurTimer)
  isEditing.value = false
}

function onSwitchChange(newValue) {
  if (props.disabled) return
  if (newValue !== props.value) {
    emit('save', newValue)
  }
}

// --- Cleanup ---
onBeforeUnmount(() => {
  clearTimeout(blurTimer)
})
</script>

<style scoped>
.inline-edit {
  display: inline-flex;
  align-items: center;
  min-width: 60px;
}

.inline-edit.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Display mode */
.inline-edit__display {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background-color 0.15s;
}

.inline-edit__display:hover {
  background-color: var(--el-fill-color-light);
}

.inline-edit__text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.inline-edit__icon {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  opacity: 0;
  transition: opacity 0.15s;
}

.inline-edit__display:hover .inline-edit__icon {
  opacity: 1;
}

/* Switch mode */
.inline-edit__switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.inline-edit__switch-tag {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 3px;
}

.inline-edit__switch-tag.is-active {
  color: var(--el-color-success);
  background-color: var(--el-color-success-light-9);
}

.inline-edit__switch-tag.is-inactive {
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-light);
}
</style>
