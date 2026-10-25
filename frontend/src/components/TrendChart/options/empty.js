/**
 * Empty-frame option: placeholder axes shown while a trend has no data.
 * Moved verbatim out of trendOptions.js (Task 10). Grid/label colors stay
 * local to keep the frame pixel-identical — theme.js exports no raw colors.
 */
import { DEFAULT_PERIOD_WINDOW } from '@/config/monitor'

const GRID_COLOR = '#2a4a7f'
const LABEL_COLOR = '#8892b0'

export function emptyFrameOption(granularity, unit, detectIntervalSeconds = 10) {
  const half = Math.floor(DEFAULT_PERIOD_WINDOW / 2)
  const now  = new Date()
  const times = []

  for (let i = -half; i < DEFAULT_PERIOD_WINDOW - half; i++) {
    const d = new Date(now)
    switch (granularity) {
      case 'INTRADAY': d.setSeconds(d.getSeconds() + i * detectIntervalSeconds); break
      case 'HOUR'    : d.setHours(d.getHours() + i); break
      case 'DAY'     : d.setDate(d.getDate() + i); break
      case 'WEEK'    : d.setDate(d.getDate() + i * 7); break
      case 'MONTH'   : d.setMonth(d.getMonth() + i); break
      default        : d.setDate(d.getDate() + i)
    }
    const pad = n => String(n).padStart(2, '0')
    if (granularity === 'MONTH') {
      times.push(`${d.getFullYear()}/${pad(d.getMonth() + 1)}`)
    } else if (granularity === 'INTRADAY' || granularity === 'HOUR') {
      times.push(`${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`)
    } else {
      times.push(`${pad(d.getMonth() + 1)}/${pad(d.getDate())}`)
    }
  }

  return {
    grid: { left: 60, right: 20, top: 20, bottom: 50 },
    xAxis: {
      type     : 'category',
      data     : times,
      axisLabel: { color: LABEL_COLOR, fontSize: 10 },
      axisLine : { lineStyle: { color: GRID_COLOR } },
      splitLine: { show: true, lineStyle: { color: GRID_COLOR } },
    },
    yAxis: {
      type        : 'value',
      min         : 0,
      max         : 100,
      name        : unit || '',
      axisLabel   : { color: LABEL_COLOR },
      nameTextStyle: { color: LABEL_COLOR },
      splitLine   : { show: true, lineStyle: { color: GRID_COLOR } },
      axisLine    : { lineStyle: { color: GRID_COLOR } },
    },
    series: [],
  }
}
