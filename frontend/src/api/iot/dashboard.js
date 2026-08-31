import { systarApi } from '@/api/request'

export function getDashboardStats() {
  return systarApi({ url: '/api/monitor/dashboard', method: 'get' })
}
