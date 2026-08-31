import { systarApi } from '@/api/request'

const BASE = '/ops/device-ledger'

export function listDevices(query) {
  return systarApi({ url: BASE, method: 'get', params: query })
}

export function getDeviceStats() {
  return systarApi({ url: `${BASE}/stats`, method: 'get' })
}

export function getDeviceDetail(deviceId) {
  return systarApi({ url: `${BASE}/${deviceId}`, method: 'get' })
}

export function getDeviceAttributes(deviceId) {
  return systarApi({ url: `${BASE}/${deviceId}/attributes`, method: 'get' })
}

export function setDeviceAttributes(deviceId, attributes) {
  return systarApi({ url: `${BASE}/${deviceId}/attributes`, method: 'post', data: attributes })
}

export function deleteDeviceAttribute(deviceId, attrKey) {
  return systarApi({ url: `${BASE}/${deviceId}/attributes/${attrKey}`, method: 'delete' })
}

export function getWarrantyExpiring(before) {
  return systarApi({ url: `${BASE}/warranty-expiring`, method: 'get', params: { before } })
}

export function listMaintenanceRecords(deviceId, query) {
  return systarApi({ url: `${BASE}/${deviceId}/maintenance-records`, method: 'get', params: query })
}

export function createMaintenanceRecord(deviceId, data) {
  return systarApi({ url: `${BASE}/${deviceId}/maintenance-records`, method: 'post', data })
}

export function getMaintenanceRecord(id) {
  return systarApi({ url: `${BASE}/maintenance-records/${id}`, method: 'get' })
}
