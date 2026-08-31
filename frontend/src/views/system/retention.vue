<template>
  <div class="data-retention-page">
    <el-card header="数据保留策略">
      <el-form label-width="140px" style="max-width: 600px">
        <el-form-item label="采样数据保留天数">
          <el-input-number v-model="form.sampleDays" :min="1" :max="3650" :step="30" />
          <span class="form-hint">超过保留天数的采样数据将被自动清理</span>
        </el-form-item>
        <el-form-item label="告警日志保留天数">
          <el-input-number v-model="form.alarmLogDays" :min="1" :max="3650" :step="30" />
          <span class="form-hint">超过保留天数的告警消息和日志将被自动清理</span>
        </el-form-item>
        <el-form-item label="联动日志保留天数">
          <el-input-number v-model="form.linkageLogDays" :min="1" :max="3650" :step="30" />
          <span class="form-hint">超过保留天数的联动日志将被自动清理</span>
        </el-form-item>
        <el-form-item label="启用自动清理">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveConfig" :loading="saving">保存配置</el-button>
          <el-button type="danger" @click="handleExecute" :loading="executing">立即清理</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="lastResult" header="清理结果" style="margin-top: 16px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="浮点采样">{{ lastResult.sampleFloat?.deletedCount ?? 0 }} 行</el-descriptions-item>
        <el-descriptions-item label="整数采样">{{ lastResult.sampleInt?.deletedCount ?? 0 }} 行</el-descriptions-item>
        <el-descriptions-item label="布尔采样">{{ lastResult.sampleBool?.deletedCount ?? 0 }} 行</el-descriptions-item>
        <el-descriptions-item label="异常采样">{{ lastResult.sampleException?.deletedCount ?? 0 }} 行</el-descriptions-item>
        <el-descriptions-item label="告警消息">{{ lastResult.alarmMessage?.deletedCount ?? 0 }} 行</el-descriptions-item>
        <el-descriptions-item label="告警日志">{{ lastResult.alarmLog?.deletedCount ?? 0 }} 行</el-descriptions-item>
        <el-descriptions-item label="联动日志">{{ lastResult.linkageLog?.deletedCount ?? 0 }} 行</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRetentionConfig, updateRetentionConfig, executeRetention } from '@/api/iot/monitor'

const form = ref({
  sampleDays: 90,
  alarmLogDays: 180,
  linkageLogDays: 180,
  enabled: true
})
const saving = ref(false)
const executing = ref(false)
const lastResult = ref(null)

async function loadConfig() {
  try {
    const res = await getRetentionConfig()
    if (res.data) {
      form.value.sampleDays = res.data.sampleDays ?? 90
      form.value.alarmLogDays = res.data.alarmLogDays ?? 180
      form.value.linkageLogDays = res.data.linkageLogDays ?? 180
      form.value.enabled = res.data.enabled ?? true
    }
  } catch (e) {
    console.warn('Failed to load retention config:', e)
    ElMessage.warning('加载配置失败，显示默认值')
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await updateRetentionConfig(form.value)
    ElMessage.success('配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function handleExecute() {
  try {
    await ElMessageBox.confirm('确认立即执行数据清理？此操作不可撤销。', '数据清理确认', {
      confirmButtonText: '确认清理',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  executing.value = true
  try {
    const res = await executeRetention()
    lastResult.value = res.data
    ElMessage.success('数据清理完成')
  } catch (e) {
    ElMessage.error('清理失败: ' + (e.message || e))
  } finally {
    executing.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.data-retention-page { padding: 16px; }
.form-hint { display: block; font-size: 12px; color: #999; margin-top: 4px; }
</style>
