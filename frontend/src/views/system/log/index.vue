<template>
  <div class="log-page">
    <el-form :model="searchForm" inline class="search-bar">
      <el-form-item label="用户名">
        <el-input v-model="searchForm.username" placeholder="输入用户名" clearable style="width:160px" />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker v-model="searchForm.dateRange" type="daterange"
          range-separator="-" start-placeholder="开始" end-placeholder="结束"
          value-format="YYYY-MM-DD" style="width:260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="logList" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="操作用户" />
      <el-table-column prop="operation" label="操作描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="method" label="方法" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP地址" width="130" />
      <el-table-column prop="costTime" label="耗时(ms)" width="90" />
      <el-table-column prop="operTime" label="操作时间" width="160" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page="pageNum" v-model:limit="pageSize"
      :total="total" @pagination="fetchLogs" />

    <!-- Detail Dialog -->
    <el-dialog title="操作日志详情" v-model="detailVisible" width="640px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="操作描述">{{ detailData.operation }}</el-descriptions-item>
        <el-descriptions-item label="操作用户">{{ detailData.username }}</el-descriptions-item>
        <el-descriptions-item label="方法">{{ detailData.method }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ detailData.ip }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detailData.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detailData.operTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre style="margin:0;max-height:200px;overflow:auto">{{ formatJson(detailData.params) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="返回结果">
          <pre style="margin:0;max-height:200px;overflow:auto">{{ formatJson(detailData.result) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listLogs, getLog } from '@/api/sys/log'

const logList = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchForm = reactive({
  username: '',
  dateRange: null
})

const detailVisible = ref(false)
const detailData = reactive({
  operation: '', username: '', method: '', ip: '', costTime: '',
  operTime: '', params: '', result: ''
})

function formatJson(str) {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

async function fetchLogs() {
  loading.value = true
  try {
    const params = { page: pageNum.value, size: pageSize.value }
    if (searchForm.username) params.username = searchForm.username
    if (searchForm.dateRange) {
      params.startTime = searchForm.dateRange[0]
      params.endTime = searchForm.dateRange[1]
    }
    const res = await listLogs(params)
    logList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('获取日志列表失败: ' + e.message)
  } finally { loading.value = false }
}

function handleSearch() {
  pageNum.value = 1
  fetchLogs()
}
function resetSearch() {
  searchForm.username = ''
  searchForm.dateRange = null
  pageNum.value = 1
  fetchLogs()
}

function openDetail(row) {
  getLog(row.id).then(res => {
    const d = res.data || {}
    Object.assign(detailData, {
      operation: d.operation || '',
      username: d.username || '',
      method: d.method || '',
      ip: d.ip || '',
      costTime: d.costTime || '',
      operTime: d.operTime || '',
      params: d.params || '',
      result: d.result || ''
    })
    detailVisible.value = true
  }).catch(e => ElMessage.error('获取日志详情失败: ' + e.message))
}

onMounted(() => { fetchLogs() })
</script>

<style scoped>
.log-page { padding: 0; }
.search-bar { background: #fff; padding: 16px; border-radius: 4px; margin-bottom: 16px; }
pre { font-size: 12px; color: #333; white-space: pre-wrap; word-break: break-all; }
</style>
