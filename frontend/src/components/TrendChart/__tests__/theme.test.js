import { describe, it, expect } from 'vitest'
import { RISING, FALLING, ALARM, labelFor, perfSwitches } from '../theme'

describe('TrendChart theme', () => {
  it('pins SCADA semantics colors (D7)', () => {
    expect(RISING).toBe('#ffab00')
    expect(FALLING).toBe('#00d4ff')
    expect(ALARM).toBe('#ff5252')
  })

  it('labelFor formats per granularity from local-time strings', () => {
    expect(labelFor('2026-06-01T08:30:00', 'HOUR')).toBe('06-01 08:30')
    expect(labelFor('2026-06-01 00:00:00', 'DAY')).toBe('06-01')
    expect(labelFor('2026-06-01 00:00:00', 'MONTH')).toBe('2026-06')
  })

  it('perfSwitches follows the spec budgets', () => {
    expect(perfSwitches(900)).toEqual({ sampling: 'none', large: false, animation: true })
    expect(perfSwitches(1500)).toEqual({ sampling: 'lttb', large: true, animation: true })
    expect(perfSwitches(2500)).toEqual({ sampling: 'lttb', large: true, animation: false })
  })
})
