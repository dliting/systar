/**
 * Single source of truth for trend-chart colors and shared ECharts fragments.
 * Colors follow spec D7: rising #ffab00 / falling #00d4ff; #ff5252 and #69f0ae
 * are reserved for alarm/normal state and must not be used for series bodies.
 */
import * as echarts from 'echarts'

export const RISING = '#ffab00'
export const FALLING = '#00d4ff'
export const ALARM = '#ff5252'
export const NORMAL = '#69f0ae'
export const CYAN = '#00d4ff'
export const MA_COLORS = ['#9aa7b8', '#d4b96a', '#6ac3b0']

export function darkTooltip() {
  return {
    backgroundColor: 'rgba(10, 25, 41, 0.92)',
    borderColor: 'rgba(0, 212, 255, 0.35)',
    textStyle: { color: '#e0e6ed', fontSize: 12 },
  }
}

export function axisStyle() {
  return {
    axisLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.25)' } },
    axisLabel: { color: '#8b9dc3', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(139, 157, 195, 0.12)' } },
  }
}

export function gridStyle() {
  return { left: 56, right: 16, top: 40, bottom: 56, containLabel: false }
}

export function cyanGradient() {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(0, 212, 255, 0.35)' },
    { offset: 1, color: 'rgba(0, 212, 255, 0.02)' },
  ])
}

const pad = (n) => String(n).padStart(2, '0')

export function labelFor(t, granularity) {
  const d = new Date(String(t).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(t)
  const y = d.getFullYear(), m = pad(d.getMonth() + 1), day = pad(d.getDate())
  if (granularity === 'MONTH') return `${y}-${m}`
  if (granularity === 'DAY' || granularity === 'WEEK') return `${m}-${day}`
  return `${m}-${day} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function perfSwitches(pointCount) {
  return {
    sampling: pointCount > 1000 ? 'lttb' : 'none',
    large: pointCount > 1000,
    animation: pointCount <= 2000,
  }
}
