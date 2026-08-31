import { systarApi } from '@/api/request'

export function getAssetTree() {
  return systarApi({ url: '/api/monitor/tree', method: 'get' })
}
export function listAssets(query) {
  return systarApi({ url: '/api/monitor/assets', method: 'get', params: query })
}
export function getAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id, method: 'get' })
}
export function createAsset(data) {
  return systarApi({ url: '/api/monitor/assets', method: 'post', data })
}
export function updateAsset(id, data) {
  return systarApi({ url: '/api/monitor/assets/' + id, method: 'put', data })
}
export function deleteAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id, method: 'delete' })
}
export function startAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id + '/start', method: 'put' })
}
export function stopAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id + '/stop', method: 'put' })
}
export function disableAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id + '/disable', method: 'put' })
}
export function enableAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id + '/enable', method: 'put' })
}
export function getAssetTypes() {
  return systarApi({ url: '/api/monitor/asset-types', method: 'get' })
}
export function getTypeProperties(kind, typeName) {
  return systarApi({ url: '/api/monitor/asset-types/' + kind + '/' + typeName, method: 'get' })
}
export function detectAsset(id) {
  return systarApi({ url: '/api/monitor/assets/' + id + '/detect', method: 'post' })
}
export function executeControl(id, command) {
  return systarApi({ url: '/api/monitor/control/' + id + '/execute', method: 'post', data: { command } })
}
export function getProbeHistory(monitorId, params) {
  return systarApi({ url: '/api/monitor/probe-history', method: 'get', params: { monitorId, ...params } })
}

// Batch operations
function batchOp(action, ids) {
  return systarApi({ url: '/api/monitor/assets/batch/' + action, method: 'put', data: { ids } })
}
export function batchStart(ids) { return batchOp('start', ids) }
export function batchStop(ids) { return batchOp('stop', ids) }
export function batchEnable(ids) { return batchOp('enable', ids) }
export function batchDisable(ids) { return batchOp('disable', ids) }
export function batchDelete(ids) {
  return systarApi({ url: '/api/monitor/assets/batch', method: 'delete', data: { ids } })
}
