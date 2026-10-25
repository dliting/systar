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

// --- Operator label maps -------------------------------------------------
// Raw enum codes and numeric ids (event rank, work order type, priority,
// escalation count, notify channel, control command) are meaningless to
// operations staff; every display site maps them through these labels.

// t_event_rank seed rows: 1提示 / 2警告 / 3重要 / 4紧急.
const RANK_LABEL_MAP = { 1: '提示', 2: '警告', 3: '重要', 4: '紧急' }
const RANK_TAG_MAP   = { 1: 'info', 2: 'warning', 3: 'danger', 4: 'danger' }

export function rankLabel(rankId) {
  if (rankId === null || rankId === undefined || rankId === '') return '—'
  return RANK_LABEL_MAP[rankId] || `级别 ${rankId}`
}

export function rankTagType(rankId) {
  return RANK_TAG_MAP[rankId] || 'info'
}

const WORK_ORDER_TYPE_MAP = { REPAIR: '维修', MAINTENANCE: '维护', INSPECTION: '巡检' }

export function workOrderTypeLabel(type) {
  if (!type) return ''
  return WORK_ORDER_TYPE_MAP[type] || type
}

// Work order priority copies the event rank id (4 = 紧急), matching the
// work order create dialog's scale — not an inverted P1-is-highest scale.
const PRIORITY_LABEL_MAP = { 1: 'P1 低', 2: 'P2 中', 3: 'P3 高', 4: 'P4 紧急' }

export function priorityLabel(priority) {
  if (priority === null || priority === undefined || priority === '') return ''
  return PRIORITY_LABEL_MAP[priority] || `P${priority}`
}

export function escalationLabel(level) {
  const n = Number(level)
  if (!Number.isFinite(n) || n <= 0) return '未升级'
  return `已升级 ${n} 次`
}

const NOTIFY_TYPE_MAP = { site_notice: '站内通知', email: '邮件', sms: '短信', phone: '电话' }

export function notifyTypeLabel(notifyType) {
  if (!notifyType) return ''
  return NOTIFY_TYPE_MAP[notifyType] || notifyType
}

// -1 is the engine's toggle sentinel for linkage effects; ON/OFF are the
// common control commands. Custom commands pass through untouched.
const COMMAND_LABEL_MAP = { '-1': '切换', ON: '开', OFF: '关' }

export function commandLabel(command) {
  if (command === null || command === undefined || command === '') return ''
  return COMMAND_LABEL_MAP[String(command)] || String(command)
}

// Lightweight humanizer for the Spring 6-field cron expressions the schedule
// and inspection forms store. Recognizes the common shapes (daily/weekly/
// weekday times, every-N intervals); anything else falls back to the raw
// expression so no information is lost.
const DOW_LABEL_MAP = { SUN: '周日', MON: '周一', TUE: '周二', WED: '周三', THU: '周四', FRI: '周五', SAT: '周六' }

export function describeCron(expr) {
  if (!expr) return ''
  const raw = String(expr).trim()
  let fields = raw.split(/\s+/)
  if (fields.length === 5) fields = ['0', ...fields]
  if (fields.length !== 6) return raw

  const [sec, min, hour, dom, mon, dow] = fields
  if (mon !== '*' || (sec !== '0' && sec !== '*')) return raw
  if (dom !== '*' && dom !== '?') return raw

  const dayPrefix = describeCronDay(dow)
  if (dayPrefix === null) return raw

  const pad = n => String(n).padStart(2, '0')

  // Interval shapes: every minute / every N minutes / every N hours.
  if (min === '*' && hour === '*') return dayPrefix === '每天' ? '每分钟' : `${dayPrefix}每分钟`
  const minInterval = /^\*\/(\d+)$/.exec(min)
  if (minInterval && hour === '*') return dayPrefix === '每天' ? `每 ${minInterval[1]} 分钟` : `${dayPrefix}每 ${minInterval[1]} 分钟`
  const hourInterval = /^\*\/(\d+)$/.exec(hour)
  if (hourInterval && min === '0') return dayPrefix === '每天' ? `每 ${hourInterval[1]} 小时` : `${dayPrefix}每 ${hourInterval[1]} 小时`

  // Fixed-time shapes: hour and minute are digits or comma lists.
  if (!/^[\d,]+$/.test(hour) || !/^[\d,]+$/.test(min)) return raw
  const times = hour.split(',').flatMap(h => min.split(',').map(m => `${pad(h)}:${pad(m)}`))
  return `${dayPrefix} ${times.join('、')}`
}

function describeCronDay(dow) {
  if (dow === '*' || dow === '?') return '每天'
  if (dow === 'MON-FRI') return '工作日'
  if (DOW_LABEL_MAP[dow]) return `每${DOW_LABEL_MAP[dow]}`
  return null
}
