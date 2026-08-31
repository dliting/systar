import { systarApi } from '@/api/request'

const BASE = '/api/monitor/scheduled-tasks'

export function listTasks(controlId, keyword) {
  const params = {}
  if (controlId != null) params.controlId = controlId
  if (keyword != null && keyword !== '') params.keyword = keyword
  return systarApi({ url: BASE, method: 'get', params })
}

export function getTask(id) {
  return systarApi({ url: BASE + '/' + id, method: 'get' })
}

export function createTask(data) {
  return systarApi({ url: BASE, method: 'post', data })
}

export function updateTask(id, data) {
  return systarApi({ url: BASE + '/' + id, method: 'put', data })
}

export function deleteTask(id) {
  return systarApi({ url: BASE + '/' + id, method: 'delete' })
}

export function enableTask(id) {
  return systarApi({ url: BASE + '/' + id + '/enable', method: 'put' })
}

export function disableTask(id) {
  return systarApi({ url: BASE + '/' + id + '/disable', method: 'put' })
}

export function getTaskLogs(id, limit) {
  const params = limit != null ? { limit } : {}
  return systarApi({ url: BASE + '/' + id + '/logs', method: 'get', params })
}

export function previewCron(expression) {
  return systarApi({ url: BASE + '/cron-preview', method: 'get', params: { expression } })
}
