import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { noAuth: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/layout/index.vue'),
    redirect: '/operations',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '监控大屏' } },
      { path: 'operations', name: 'Operations', component: () => import('@/views/operations/index.vue'), meta: { title: '资产运维' } },
      { path: 'alarm', name: 'Alarm', component: () => import('@/views/alarm/index.vue'), meta: { title: '告警管理' } },
      { path: 'linkage', name: 'Linkage', component: () => import('@/views/linkage/index.vue'), meta: { title: '联动规则' } },
      { path: 'schedule', name: 'ScheduledTask', component: () => import('@/views/schedule/index.vue'), meta: { title: '计划任务' } },
      {
        path: 'system',
        component: () => import('@/views/system/index.vue'),
        redirect: '/system/user',
        meta: { title: '系统管理' },
        children: [
          { path: 'user', name: 'SysUser', component: () => import('@/views/system/user/index.vue'), meta: { title: '用户管理' } },
          { path: 'role', name: 'SysRole', component: () => import('@/views/system/role/index.vue'), meta: { title: '角色管理' } },
          { path: 'menu', name: 'SysMenu', component: () => import('@/views/system/menu/index.vue'), meta: { title: '菜单管理' } },
          { path: 'dept', name: 'SysDept', component: () => import('@/views/system/dept/index.vue'), meta: { title: '部门管理' } },
          { path: 'notice', name: 'SysNotice', component: () => import('@/views/system/notice/index.vue'), meta: { title: '通知公告' } },
          { path: 'log', name: 'SysLog', component: () => import('@/views/system/log/index.vue'), meta: { title: '日志管理' } },
          { path: 'monitor', name: 'SysMonitor', component: () => import('@/views/system/monitor/index.vue'), meta: { title: '在线用户' } },
          { path: 'retention', name: 'DataRetention', component: () => import('@/views/system/retention.vue'), meta: { title: '数据保留' } }
        ]
      },
      { path: 'workorder', name: 'WorkOrder', component: () => import('@/views/workorder/index.vue'), meta: { title: '工单管理' } },
      { path: 'ledger', name: 'Ledger', component: () => import('@/views/ledger/index.vue'), meta: { title: '设备台账' } },
      { path: 'inspection', name: 'Inspection', component: () => import('@/views/inspection/index.vue'), meta: { title: '巡检管理' } },
      { path: 'statistics', name: 'Statistics', component: () => import('@/views/statistics/index.vue'), meta: { title: '统计报表' } }
    ]
  },
  { path: '/trend-standalone', name: 'TrendStandalone', meta: { noAuth: true, title: '独立趋势图' },
    component: () => import('@/views/statistics/pages/TrendStandalone.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.noAuth || getToken()) { next() } else { next('/login') }
})

export default router
