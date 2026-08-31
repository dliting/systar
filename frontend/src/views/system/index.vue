<template>
  <div class="sys-layout">
    <div class="sys-sidebar">
      <router-link v-for="item in menuItems" :key="item.path"
        :to="item.path" class="sys-menu-item"
        :class="{ active: route.path === item.path }">
        {{ item.label }}
      </router-link>
    </div>
    <div class="sys-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()

const menuItems = [
  { path: '/system/user', label: '用户管理' },
  { path: '/system/role', label: '角色管理' },
  { path: '/system/menu', label: '菜单管理' },
  { path: '/system/dept', label: '部门管理' },
  { path: '/system/notice', label: '通知公告' },
  { path: '/system/log', label: '操作日志' },
  { path: '/system/monitor', label: '系统监控' },
  { path: '/system/retention', label: '数据保留' }
]
</script>

<style scoped>
.sys-layout { display: flex; height: 100%; }
.sys-sidebar { width: 180px; background: #fff; border-right: 1px solid #e8e8e8;
  padding: 12px 0; flex-shrink: 0; }
.sys-menu-item { display: block; padding: 10px 20px; color: #333;
  text-decoration: none; font-size: 14px; border-left: 3px solid transparent; }
.sys-menu-item.active { color: #1890ff; border-left-color: #1890ff; background: #e6f7ff; }
.sys-menu-item:hover { background: #f5f5f5; }
.sys-content { flex: 1; padding: 16px; overflow: auto; }
</style>
