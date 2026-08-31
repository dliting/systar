import { systarApi } from '@/api/request'

const BASE = '/api/ops/statistics'

export function getAlarmStats(params) {
  return systarApi({ url: `${BASE}/alarm`, method: 'get', params })
}

export function getWorkOrderStats(params) {
  return systarApi({ url: `${BASE}/work-order`, method: 'get', params })
}

export function getInspectionStats(params) {
  return systarApi({ url: `${BASE}/inspection`, method: 'get', params })
}

export function getDeviceRuntimeStats(params) {
  return systarApi({ url: `${BASE}/device-runtime`, method: 'get', params })
}

export function getMaintenanceStats(params) {
  return systarApi({ url: `${BASE}/maintenance`, method: 'get', params })
}

export function getDashboardStats() {
  return systarApi({ url: `${BASE}/dashboard`, method: 'get' })
}
