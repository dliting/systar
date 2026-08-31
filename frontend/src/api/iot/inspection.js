import { systarApi } from '@/api/request'

const BASE = '/ops/inspection'

// Plans
export function listPlans(query) {
  return systarApi({ url: `${BASE}/plans`, method: 'get', params: query })
}

export function getPlan(id) {
  return systarApi({ url: `${BASE}/plans/${id}`, method: 'get' })
}

export function createPlan(data) {
  return systarApi({ url: `${BASE}/plans`, method: 'post', data })
}

export function updatePlan(id, data) {
  return systarApi({ url: `${BASE}/plans/${id}`, method: 'put', data })
}

export function deletePlan(id) {
  return systarApi({ url: `${BASE}/plans/${id}`, method: 'delete' })
}

// Plan devices
export function getPlanDevices(planId) {
  return systarApi({ url: `${BASE}/plans/${planId}/devices`, method: 'get' })
}

export function addPlanDevice(planId, deviceId) {
  return systarApi({ url: `${BASE}/plans/${planId}/devices`, method: 'post', data: { deviceId } })
}

export function removePlanDevice(planId, deviceId) {
  return systarApi({ url: `${BASE}/plans/${planId}/devices/${deviceId}`, method: 'delete' })
}

// Plan items
export function getPlanItems(planId) {
  return systarApi({ url: `${BASE}/plans/${planId}/items`, method: 'get' })
}

export function addPlanItem(planId, data) {
  return systarApi({ url: `${BASE}/plans/${planId}/items`, method: 'post', data })
}

export function updatePlanItem(planId, itemId, data) {
  return systarApi({ url: `${BASE}/plans/${planId}/items/${itemId}`, method: 'put', data })
}

export function deletePlanItem(planId, itemId) {
  return systarApi({ url: `${BASE}/plans/${planId}/items/${itemId}`, method: 'delete' })
}

// Tasks
export function listTasks(query) {
  return systarApi({ url: `${BASE}/tasks`, method: 'get', params: query })
}

export function getTask(id) {
  return systarApi({ url: `${BASE}/tasks/${id}`, method: 'get' })
}

export function getTaskStats() {
  return systarApi({ url: `${BASE}/task-stats`, method: 'get' })
}

export function startTask(id) {
  return systarApi({ url: `${BASE}/tasks/${id}/start`, method: 'put' })
}

export function submitResults(id, results) {
  return systarApi({ url: `${BASE}/tasks/${id}/results`, method: 'post', data: results })
}

export function completeTask(id, remark) {
  return systarApi({ url: `${BASE}/tasks/${id}/complete`, method: 'put', data: { remark } })
}

export function cancelTask(id, remark) {
  return systarApi({ url: `${BASE}/tasks/${id}/cancel`, method: 'put', data: { remark } })
}

export function reassignTask(id, assigneeId) {
  return systarApi({ url: `${BASE}/tasks/${id}/reassign`, method: 'put', data: { assigneeId } })
}
