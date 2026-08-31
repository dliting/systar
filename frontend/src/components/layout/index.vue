<template>
  <div class="systar-layout">
    <header class="systar-header">
      <div class="header-left">
        <h1 class="logo">Systar 监控运维</h1>
      </div>
      <el-menu
        mode="horizontal"
        :default-active="activeMenu"
        :ellipsis="false"
        class="header-nav"
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#fff"
        router
      >
        <el-menu-item index="/dashboard">监控大屏</el-menu-item>
        <el-menu-item index="/operations">资产运维</el-menu-item>
        <el-sub-menu index="alarm-group">
          <template #title>告警中心</template>
          <el-menu-item index="/alarm?tab=messages">告警消息</el-menu-item>
          <el-menu-item index="/alarm?tab=rules">告警规则</el-menu-item>
          <el-menu-item index="/alarm?tab=correlation">关联规则</el-menu-item>
          <el-menu-item index="/alarm?tab=escalation">升级策略</el-menu-item>
          <el-menu-item index="/alarm?tab=silence">静默窗口</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/linkage">联动规则</el-menu-item>
        <el-sub-menu index="ops-group">
          <template #title>运维管理</template>
          <el-menu-item index="/schedule">计划任务</el-menu-item>
          <el-menu-item index="/workorder">工单管理</el-menu-item>
          <el-menu-item index="/inspection">巡检管理</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/ledger">设备台账</el-menu-item>
        <el-menu-item index="/statistics">统计报表</el-menu-item>
        <el-menu-item index="/system">系统管理</el-menu-item>
      </el-menu>
      <div class="header-right">
        <NotificationBell />
        <span class="user-name">{{ auth.username || '用户' }}</span>
        <el-button text @click="handleLogout">退出</el-button>
      </div>
    </header>
    <main class="systar-main" :class="{ 'systar-main--dashboard': isDashboard }">
      <router-view v-slot="{ Component }">
        <keep-alive :include="['Dashboard']">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useKeyboard } from '@/composables/useKeyboard'
import NotificationBell from '@/components/NotificationBell.vue'

const router = useRouter()
const route  = useRoute()
const auth   = useAuthStore()

useKeyboard({
  onEsc: () => {
    const dialogs = document.querySelectorAll('.el-dialog:not([style*="display: none"])')
    const topDialog = dialogs[dialogs.length - 1]
    if (topDialog) {
      const closeBtn = topDialog.querySelector('.el-dialog__headerbtn')
      closeBtn?.click()
    }
  }
})

onUnmounted(() => auth.stopRenewer())

const isDashboard = computed(() => route.path === '/dashboard')

const activeMenu = computed(() => {
  const p = route.path
  if (p === '/alarm' && !route.query.tab) return '/alarm?tab=rules'
  if (p.startsWith('/alarm')) return route.fullPath
  if (p.startsWith('/system')) return '/system'
  return p
})

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.systar-layout { display: flex; flex-direction: column; height: 100vh; }
.systar-header { display: flex; align-items: center; padding: 0 20px; height: 56px;
  background: #001529; color: #fff; }
.header-left { flex: 0 0 200px; }
.logo { font-size: 18px; margin: 0; }

/* el-menu horizontal overrides for dark header */
.header-nav {
  flex: 1;
  border-bottom: none !important;
  height: 56px;
}
.header-nav :deep(.el-menu-item),
.header-nav :deep(.el-sub-menu .el-sub-menu__title) {
  height: 56px;
  line-height: 56px;
  border-bottom: none !important;
}
.header-nav :deep(.el-menu-item:hover),
.header-nav :deep(.el-sub-menu .el-sub-menu__title:hover) {
  background-color: rgba(255,255,255,0.15) !important;
  color: #fff !important;
}
.header-nav :deep(.el-menu-item.is-active) {
  background-color: rgba(255,255,255,0.2) !important;
  color: #fff !important;
  border-bottom: 2px solid #fff !important;
}

.header-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.user-name { font-size: 14px; opacity: 0.85; }
.systar-main { flex: 1; overflow: auto; padding: 20px; background: #f0f2f5; }
.systar-main--dashboard { overflow: hidden; }
</style>
