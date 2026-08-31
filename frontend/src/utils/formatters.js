/**
 * Shared formatting utilities for IoT asset display.
 */

const STATE_TAG_MAP = { NORMAL: 'success', WARNING: 'warning', ERROR: 'danger', OFFLINE: 'info' }
const KIND_LABEL_MAP = { SPACE: '空间', DEVICE: '设备', PROBE: '监测器', CONTROL: '控制器', SERVICE: '服务' }
const KIND_TAG_MAP = { SPACE: 'info', DEVICE: 'primary', PROBE: 'success', CONTROL: 'warning', SERVICE: 'info' }

export function stateTagType(state) {
  return STATE_TAG_MAP[state] || 'info'
}

export function kindLabel(kind) {
  return KIND_LABEL_MAP[kind] || kind
}

export function kindTagType(kind) {
  return KIND_TAG_MAP[kind] || 'info'
}

export function formatTimestamp(ts) {
  if (!ts) return ''
  if (typeof ts === 'number') {
    return new Date(ts).toLocaleString()
  }
  return String(ts).replace('T', ' ').substring(0, 19)
}

export function parseTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
