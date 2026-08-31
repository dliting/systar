<template>
  <el-tabs v-model="activeTab" class="cron-wizard">
    <el-tab-pane label="向导" name="wizard">
      <WizardTab
        v-model="cronExpression"
        :preview-next="formatPreviewNext"
      />
    </el-tab-pane>
    <el-tab-pane label="专家" name="expert">
      <el-input v-model="cronExpression" placeholder="秒 分 时 日 月 周" />
      <div class="expert-preview">
        <el-text v-if="previewNext" type="success" size="small">
          下次执行：{{ formatPreviewNext }}
        </el-text>
      </div>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { cronToWizard } from './cron-utils'
import WizardTab from './WizardTab.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  previewNext: { type: [String, Object, Array], default: null }
})

const emit = defineEmits(['update:modelValue'])

const cronExpression = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('wizard')

// On mount, decide initial tab based on parseability
const parsed = cronToWizard(props.modelValue)
if (!parsed && props.modelValue) {
  activeTab.value = 'expert'
}

// Block switch from expert to wizard if expression is unparseable
watch(activeTab, (newTab, oldTab) => {
  if (newTab === 'wizard' && oldTab === 'expert') {
    const result = cronToWizard(cronExpression.value)
    if (!result && cronExpression.value) {
      ElMessage.warning('当前表达式无法用向导表示，请继续在专家模式编辑')
      activeTab.value = 'expert'
    }
  }
})

function formatTime(val) {
  if (!val) return ''
  if (Array.isArray(val)) {
    const [y, M, d, h, m, s] = val
    return `${y}/${M}/${d} ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s || 0).padStart(2, '0')}`
  }
  return new Date(val).toLocaleString()
}

const formatPreviewNext = computed(() => formatTime(props.previewNext))
</script>

<style scoped>
.expert-preview { margin-top: 4px; }
</style>