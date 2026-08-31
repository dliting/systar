const LAST_DAY = 'LAST_DAY'

function wizardToCron(state) {
  if (!state || !state.frequency || !state.time) return null

  const parts = state.time.split(':')
  const hour   = Number(parts[0])
  const minute = Number(parts[1])
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null

  switch (state.frequency) {
    case 'daily':
      return `0 ${minute} ${hour} * * ?`

    case 'weekly':
      if (!state.weekDays || state.weekDays.length === 0) return null
      const days = [...state.weekDays].sort().join(',')
      return `0 ${minute} ${hour} * * ${days}`

    case 'monthly':
      const day = state.monthDay === LAST_DAY ? 'L' : state.monthDay
      return `0 ${minute} ${hour} ${day} * ?`

    default:
      return null
  }
}

function cronToWizard(expr) {
  if (!expr || typeof expr !== 'string') return null

  const parts = expr.trim().split(/\s+/)
  if (parts.length !== 6) return null

  const [sec, min, hour, day, month, weekday] = parts

  if (sec !== '0') return null
  if (month !== '*') return null

  const isWildcard = (v) => v === '*' || v === '?'
  const hasWeekdayNumbers = (v) => /^\d+(,\d+)*$/.test(v)
  const hasDayNumber = (v) => /^(\d+|L)$/.test(v)
  const isSimpleInt = (v) => /^\d+$/.test(v)

  if (!isSimpleInt(min) || !isSimpleInt(hour)) return null
  const hm = `${hour.padStart(2, '0')}:${min.padStart(2, '0')}`

  if (isWildcard(day) && isWildcard(weekday)) {
    return { frequency: 'daily', time: hm }
  }

  if (isWildcard(day) && hasWeekdayNumbers(weekday)) {
    const weekDays = weekday.split(',').map(Number).sort()
    return { frequency: 'weekly', time: hm, weekDays }
  }

  if (hasDayNumber(day) && isWildcard(weekday)) {
    const monthDay = day === 'L' ? LAST_DAY : Number(day)
    return { frequency: 'monthly', time: hm, monthDay }
  }

  return null
}

function defaultWizardState() {
  return {
    frequency: 'daily',
    time:      '08:00',
    weekDays:  [1],
    monthDay:  1,
  }
}

export { wizardToCron, cronToWizard, defaultWizardState, LAST_DAY }