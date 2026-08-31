import { systarApi } from '@/api/request'

export function getAlarmRules() {
  return systarApi({ url: '/api/monitor/alarm-rules', method: 'get' })
}
export function getAlarmMessages(query) {
  return systarApi({ url: '/api/monitor/alarm-messages', method: 'get', params: query })
}

// --- correlation rules ---
export function listCorrelationRules(query) {
  return systarApi({ url: '/api/monitor/correlation-rules', method: 'get', params: query })
}
export function addCorrelationRule(data) {
  return systarApi({ url: '/api/monitor/correlation-rules', method: 'post', data })
}
export function updateCorrelationRule(id, data) {
  return systarApi({ url: `/api/monitor/correlation-rules/${id}`, method: 'put', data })
}
export function deleteCorrelationRule(id) {
  return systarApi({ url: `/api/monitor/correlation-rules/${id}`, method: 'delete' })
}

// --- escalation policies ---
export function listEscalationPolicies(query) {
  return systarApi({ url: '/api/monitor/escalation-policies', method: 'get', params: query })
}
export function addEscalationPolicy(data) {
  return systarApi({ url: '/api/monitor/escalation-policies', method: 'post', data })
}
export function updateEscalationPolicy(id, data) {
  return systarApi({ url: `/api/monitor/escalation-policies/${id}`, method: 'put', data })
}
export function deleteEscalationPolicy(id) {
  return systarApi({ url: `/api/monitor/escalation-policies/${id}`, method: 'delete' })
}

// --- silence windows ---
export function listSilenceWindows(query) {
  return systarApi({ url: '/api/monitor/silence-windows', method: 'get', params: query })
}
export function addSilenceWindow(data) {
  return systarApi({ url: '/api/monitor/silence-windows', method: 'post', data })
}
export function updateSilenceWindow(id, data) {
  return systarApi({ url: `/api/monitor/silence-windows/${id}`, method: 'put', data })
}
export function deleteSilenceWindow(id) {
  return systarApi({ url: `/api/monitor/silence-windows/${id}`, method: 'delete' })
}
