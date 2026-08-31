<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="420px"
    :teleported="false"
    @close="onClose"
  >
    <div class="confirm-dialog__content">
      <p class="confirm-dialog__message">{{ message }}</p>
      <p v-if="impact" class="confirm-dialog__impact">{{ impact }}</p>
      <el-input
        v-if="requireInput"
        v-model="userInput"
        :placeholder="inputPlaceholder || `请输入「${expectedInput}」以确认`"
        size="default"
      />
    </div>
    <template #footer>
      <el-button @click="$emit('cancel')">取消</el-button>
      <el-button
        type="danger"
        :disabled="requireInput && userInput !== expectedInput"
        @click="onConfirm"
      >
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  visible:         { type: Boolean, default: false },
  title:           { type: String,  default: '确认操作' },
  message:         { type: String,  default: '' },
  impact:          { type: String,  default: '' },
  requireInput:    { type: Boolean, default: false },
  expectedInput:   { type: String,  default: '' },
  inputPlaceholder:{ type: String,  default: '' },
})

const emit = defineEmits(['confirm', 'cancel', 'update:visible'])

const userInput = ref('')

function onConfirm() {
  if (props.requireInput && userInput.value !== props.expectedInput) return
  userInput.value = ''
  emit('confirm')
}

function onClose() {
  userInput.value = ''
  emit('update:visible', false)
  emit('cancel')
}
</script>

<style scoped>
.confirm-dialog__message {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.confirm-dialog__impact {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: var(--el-color-warning-light-9);
  padding: 8px 12px;
  border-radius: 4px;
}
</style>
