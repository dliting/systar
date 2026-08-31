import { systarApi } from '@/api/request'

export function listLogs(params) {
  return systarApi({ url: '/api/sys/log', method: 'get', params })
}
export function getLog(id) {
  return systarApi({ url: '/api/sys/log/' + id, method: 'get' })
}
