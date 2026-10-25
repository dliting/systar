/**
 * Shared formatting utilities for IoT asset display.
 */

const STATE_TAG_MAP = { NORMAL: 'success', WARNING: 'warning', ERROR: 'danger', OFFLINE: 'info' }
const KIND_LABEL_MAP = { DEVICE: '设备', PROBE: '监测器', CONTROL: '控制器', SERVICE: '服务', GROUP: '分组' }
const KIND_TAG_MAP = { DEVICE: 'primary', PROBE: 'success', CONTROL: 'warning', SERVICE: 'info', GROUP: 'info' }

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

// Operators read whole numbers and a couple of decimals, not raw doubles
// (12.77434613895595 on a humidity card is developer noise). Two decimals
// at or above one; four below so small readings do not collapse to 0.
// Booleans and non-numeric states pass through — their operator labels are
// mapped at the display site, not here.
export function formatMetricValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return String(value)
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n)) return String(value)
  if (Number.isInteger(n)) return String(n)
  const decimals = Math.abs(n) >= 1 ? 2 : 4
  return n.toFixed(decimals).replace(/\.?0+$/, '')
}

// Asset paths arrive from the backend as '->'-joined segments (caption-aware
// pathCaption preferred, name-based path as fallback). Breadcrumbs need the
// segments; the detail panel needs them arrow-joined.
export function splitPathSegments(path) {
  if (!path) return []
  return String(path).split('->').filter(s => s)
}

export function formatDisplayPath(path) {
  return splitPathSegments(path).join(' → ')
}

export function parseTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
