<template>
  <div class="monitor-page">
    <el-row :gutter="16" v-loading="loading">
      <!-- CPU Card -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span>CPU</span></template>
          <div class="monitor-item" v-if="server.cpu">
            <div class="monitor-label">处理器</div>
            <div class="monitor-value">{{ server.cpu.name || '-' }}</div>
          </div>
          <div class="monitor-item" v-if="server.cpu">
            <div class="monitor-label">核心数</div>
            <div class="monitor-value">{{ server.cpu.cores || '-' }}</div>
          </div>
          <div class="monitor-item" v-if="server.cpu">
            <div class="monitor-label">系统负载</div>
            <div class="monitor-value">{{ server.cpu.load || '-' }}</div>
          </div>
          <el-empty v-if="!server.cpu && !loading" description="暂无数据" />
        </el-card>
      </el-col>

      <!-- Memory Card -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span>内存</span></template>
          <template v-if="server.memory">
            <div class="monitor-item">
              <div class="monitor-label">总内存</div>
              <div class="monitor-value">{{ server.memory.total || '-' }}</div>
            </div>
            <div class="monitor-item">
              <div class="monitor-label">已使用</div>
              <div class="monitor-value">{{ server.memory.used || '-' }}</div>
            </div>
            <div class="monitor-item">
              <div class="monitor-label">可用</div>
              <div class="monitor-value">{{ server.memory.available || '-' }}</div>
            </div>
            <el-progress
              :percentage="memoryPercent"
              :color="memoryPercent > 80 ? '#f56c6c' : '#409eff'"
              style="margin-top:12px" />
          </template>
          <el-empty v-if="!server.memory && !loading" description="暂无数据" />
        </el-card>
      </el-col>

      <!-- JVM Card -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span>JVM</span></template>
          <template v-if="server.jvm">
            <div class="monitor-item">
              <div class="monitor-label">最大内存</div>
              <div class="monitor-value">{{ server.jvm.max || '-' }}</div>
            </div>
            <div class="monitor-item">
              <div class="monitor-label">总内存</div>
              <div class="monitor-value">{{ server.jvm.total || '-' }}</div>
            </div>
            <div class="monitor-item">
              <div class="monitor-label">空闲内存</div>
              <div class="monitor-value">{{ server.jvm.free || '-' }}</div>
            </div>
            <div class="monitor-item">
              <div class="monitor-label">已使用</div>
              <div class="monitor-value">{{ server.jvm.used || '-' }}</div>
            </div>
          </template>
          <el-empty v-if="!server.jvm && !loading" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getServerInfo } from '@/api/sys/monitor'

const loading = ref(false)
const server = reactive({ cpu: null, memory: null, jvm: null })

const memoryPercent = computed(() => {
  if (!server.memory) return 0
  const used = parseMemoryBytes(server.memory.used)
  const total = parseMemoryBytes(server.memory.total)
  if (total === 0) return 0
  return Math.round((used / total) * 100)
})

function parseMemoryBytes(str) {
  if (!str || typeof str !== 'string') return 0
  const match = str.match(/([\d.]+)\s*(B|KB|MB|GB|TB)/)
  if (!match) return 0
  const val = parseFloat(match[1])
  const units = { B: 1, KB: 1024, MB: 1024 ** 2, GB: 1024 ** 3, TB: 1024 ** 4 }
  return val * (units[match[2]] || 1)
}

async function fetchServerInfo() {
  loading.value = true
  try {
    const res = await getServerInfo()
    const data = res.data || {}
    server.cpu = data.cpu || null
    server.memory = data.memory || null
    server.jvm = data.jvm || null
  } catch (e) {
    ElMessage.error('获取服务器信息失败: ' + e.message)
  } finally { loading.value = false }
}

onMounted(() => { fetchServerInfo() })
</script>

<style scoped>
.monitor-page { padding: 0; }
.monitor-item { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.monitor-label { color: #666; font-size: 13px; }
.monitor-value { color: #333; font-size: 13px; font-weight: 500; }
</style>
