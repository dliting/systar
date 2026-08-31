import { systarApi } from '@/api/request'

export function listLinkageRules() {
  return systarApi({ url: '/api/monitor/linkage-rules/detail', method: 'get' })
}

export function getLinkageRule(id) {
  return systarApi({ url: '/api/monitor/linkage-rules/' + id, method: 'get' })
}

export function createLinkageRule(data) {
  return systarApi({ url: '/api/monitor/linkage-rules', method: 'post', data })
}

export function updateLinkageRule(id, data) {
  return systarApi({ url: '/api/monitor/linkage-rules/' + id, method: 'put', data })
}

export function deleteLinkageRule(id) {
  return systarApi({ url: '/api/monitor/linkage-rules/' + id, method: 'delete' })
}

export function toggleLinkageRule(id) {
  return systarApi({ url: '/api/monitor/linkage-rules/' + id + '/toggle', method: 'put' })
}

export function getAssetTree() {
  return systarApi({ url: '/api/monitor/tree', method: 'get' })
}
