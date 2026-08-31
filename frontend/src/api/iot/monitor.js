import { systarApi } from '@/api/request'

export function getProbeValues(ids) {
  return systarApi({ url: '/api/monitor/probe-values', method: 'get', params: { ids } })
}
export function getProbeHistory(query) {
  return systarApi({ url: '/api/monitor/probe-history', method: 'get', params: query })
}

// Data retention
export function executeRetention() {
  return systarApi({ url: '/api/monitor/data/retention/execute', method: 'post' })
}
export function getRetentionConfig() {
  return systarApi({ url: '/api/monitor/data/retention/config', method: 'get' })
}
export function updateRetentionConfig(data) {
  return systarApi({ url: '/api/monitor/data/retention/config', method: 'put', data })
}
