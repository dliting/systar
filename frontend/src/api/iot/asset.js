import { systarApi } from '@/api/request'

export function getAssetTree(tree = 'kind') {
  return systarApi({ url: '/api/monitor/asset-tree', method: 'get', params: { tree } })
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

// Group trees and asset groups
export function listGroupTrees() {
  return systarApi({ url: '/api/monitor/group-trees', method: 'get' })
}
export function createGroupTree(data) {
  return systarApi({ url: '/api/monitor/group-trees', method: 'post', data })
}
export function updateGroupTree(id, data) {
  return systarApi({ url: '/api/monitor/group-trees/' + id, method: 'put', data })
}
export function deleteGroupTree(id) {
  return systarApi({ url: '/api/monitor/group-trees/' + id, method: 'delete' })
}

export function listGroups(treeId) {
  return systarApi({ url: '/api/monitor/groups', method: 'get', params: { treeId } })
}
export function createGroup(data) {
  return systarApi({ url: '/api/monitor/groups', method: 'post', data })
}
export function updateGroup(id, data) {
  return systarApi({ url: '/api/monitor/groups/' + id, method: 'put', data })
}
export function deleteGroup(id) {
  return systarApi({ url: '/api/monitor/groups/' + id, method: 'delete' })
}
export function replaceGroupAssets(id, assetIds) {
  return systarApi({ url: '/api/monitor/groups/' + id + '/assets', method: 'put', data: { assetIds } })
}
export function reorderGroups(treeId, data) {
  return systarApi({ url: '/api/monitor/group-trees/' + treeId + '/groups/order', method: 'put', data })
}
