import { systarApi } from '@/api/request'

export function getServerInfo() {
  return systarApi({ url: '/api/sys/monitor/server', method: 'get' })
}
export function getCacheInfo() {
  return systarApi({ url: '/api/sys/monitor/cache', method: 'get' })
}
export function getOnlineUsers() {
  return systarApi({ url: '/api/sys/monitor/online', method: 'get' })
}
