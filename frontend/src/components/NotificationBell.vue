<template>
  <el-popover placement="bottom-end" :width="360" trigger="click">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell-badge">
        <el-icon :size="20" class="bell-icon"><Bell /></el-icon>
      </el-badge>
    </template>
    <div class="notification-panel">
      <div class="notification-header">
        <span class="notification-title">告警通知</span>
        <el-button v-if="alarmMessages.length > 0" link type="primary" size="small" @click="markAllRead">全部已读</el-button>
      </div>
      <div class="notification-list" v-if="alarmMessages.length > 0">
        <div v-for="msg in displayMessages" :key="msg.alarmMessageId" class="notification-item" @click="goToAlarmPage">
          <el-icon color="#f56c6c"><WarningFilled /></el-icon>
          <div class="notification-content">
            <span class="notification-text">资产 #{{ msg.assetId }} 告警 (级别 {{ msg.eventRankId }})</span>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无告警" :image-size="60" />
      <div class="notification-footer" v-if="alarmMessages.length > 0">
        <el-button link type="primary" @click="goToAlarmPage">查看全部告警</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useWebSocketStore } from '@/stores/websocket'
import { Bell, WarningFilled } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'

const router  = useRouter()
const wsStore = useWebSocketStore()

const unreadCount     = computed(() => wsStore.unreadAlarmCount)
const alarmMessages   = computed(() => wsStore.alarmMessages)
const displayMessages = computed(() => alarmMessages.value.slice(-20).reverse())

const MAX_NOTIFICATIONS = 3
const notifiedIds       = new Set()

watch(alarmMessages, (msgs) => {
  const recent = msgs.slice(-MAX_NOTIFICATIONS)
  for (const msg of recent) {
    if (!notifiedIds.has(msg.alarmMessageId)) {
      notifiedIds.add(msg.alarmMessageId)
      ElNotification({
        title: '新告警',
        message: `资产 #${msg.assetId} 告警 (级别 ${msg.eventRankId})`,
        type: 'warning',
        duration: 5000,
      })
    }
  }
})

let pollTimer = null

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    if (!wsStore.connected) {
      import('@/api/iot/alarm').then(({ getAlarmMessages }) => {
        getAlarmMessages({ state: 1, size: 20 }).then(res => {
          const msgs = res.data?.records || []
          for (const msg of msgs) {
            wsStore._parseMessage(JSON.stringify({ type: 'alarm', alarmMessageId: msg.id, eventRankId: msg.warnId, assetId: msg.monitorId }))
          }
        }).catch(() => {})
      })
    }
  }, 30000)
}

startPolling()
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })

function markAllRead() {
  wsStore.clearUnreadAlarms()
}

function goToAlarmPage() {
  wsStore.clearUnreadAlarms()
  router.push({ path: '/alarm', query: { tab: 'messages' } })
}
</script>

<style scoped>
.bell-badge { line-height: 1; }
.bell-icon { color: rgba(255,255,255,0.85); cursor: pointer; }
.bell-icon:hover { color: #fff; }
.notification-panel { margin: -12px; }
.notification-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-bottom: 1px solid #ebeef5; }
.notification-title { font-weight: 600; font-size: 14px; }
.notification-list { max-height: 300px; overflow-y: auto; }
.notification-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer; border-bottom: 1px solid #f0f0f0; }
.notification-item:hover { background: #f5f7fa; }
.notification-text { font-size: 13px; color: #303133; }
.notification-footer { text-align: center; padding: 8px; border-top: 1px solid #ebeef5; }
</style>
