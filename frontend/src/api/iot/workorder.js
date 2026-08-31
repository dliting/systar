import { systarApi } from '@/api/request'

const BASE = '/ops/work-orders'

export function listWorkOrders(query) {
  return systarApi({ url: BASE, method: 'get', params: query })
}

export function getWorkOrderStats() {
  return systarApi({ url: `${BASE}/stats`, method: 'get' })
}

export function getWorkOrder(id) {
  return systarApi({ url: `${BASE}/${id}`, method: 'get' })
}

export function createWorkOrder(data) {
  return systarApi({ url: BASE, method: 'post', data })
}

export function assignWorkOrder(id, data) {
  return systarApi({ url: `${BASE}/${id}/assign`, method: 'put', data })
}

export function processWorkOrder(id, data) {
  return systarApi({ url: `${BASE}/${id}/process`, method: 'put', data })
}

export function closeWorkOrder(id, data) {
  return systarApi({ url: `${BASE}/${id}/close`, method: 'put', data })
}

export function cancelWorkOrder(id, data) {
  return systarApi({ url: `${BASE}/${id}/cancel`, method: 'put', data })
}

export function uploadAttachment(id, formData) {
  return systarApi({ url: `${BASE}/${id}/attachments`, method: 'post', data: formData })
}

export function deleteAttachment(id, attachmentId) {
  return systarApi({ url: `${BASE}/${id}/attachments/${attachmentId}`, method: 'delete' })
}
