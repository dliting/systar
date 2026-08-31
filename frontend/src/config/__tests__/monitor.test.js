import { describe, it, expect } from 'vitest'
import {
  formatMonitorValue,
  formatMonitorDateTime,
  formatMonitorAxisTime,
  formatAxisTimeAdaptive,
  computeRangeDays,
  UNIT_PRECISION_MAP,
  MONITOR_VALUE_PRECISION,
  DEFAULT_PERIOD_WINDOW,
} from '../monitor'

describe('formatMonitorValue', () => {
  describe('null / undefined handling', () => {
    it('returns "--" for null', () => {
      expect(formatMonitorValue(null)).toBe('--')
    })

    it('returns "--" for undefined', () => {
      expect(formatMonitorValue(undefined)).toBe('--')
    })
  })

  describe('integer values (preserved unchanged)', () => {
    it('returns integer as string without decimals', () => {
      expect(formatMonitorValue(42)).toBe('42')
    })

    it('returns zero as "0"', () => {
      expect(formatMonitorValue(0)).toBe('0')
    })

    it('returns negative integer as string', () => {
      expect(formatMonitorValue(-5)).toBe('-5')
    })

    it('preserves integer regardless of unit', () => {
      expect(formatMonitorValue(100, '°C')).toBe('100')
      expect(formatMonitorValue(100, '%')).toBe('100')
    })
  })

  describe('without unit (backward-compat, global precision)', () => {
    it('formats float with MONITOR_VALUE_PRECISION decimals', () => {
      expect(formatMonitorValue(23.456789)).toBe('23.457')
    })

    it('rounds half-up', () => {
      expect(formatMonitorValue(1.2345)).toBe('1.234')
      expect(formatMonitorValue(1.2355)).toBe('1.236')
    })
  })

  describe('with unit — precision from UNIT_PRECISION_MAP', () => {
    it('°C uses 1 decimal', () => {
      expect(formatMonitorValue(23.456, '°C')).toBe('23.5')
    })

    it('C (alias) uses 1 decimal', () => {
      expect(formatMonitorValue(23.456, 'C')).toBe('23.5')
    })

    it('℃ (alias) uses 1 decimal', () => {
      expect(formatMonitorValue(23.456, '℃')).toBe('23.5')
    })

    it('% uses 0 decimals', () => {
      expect(formatMonitorValue(72.6, '%')).toBe('73')
    })

    it('%RH uses 0 decimals', () => {
      expect(formatMonitorValue(72.4, '%RH')).toBe('72')
    })

    it('V uses 2 decimals', () => {
      expect(formatMonitorValue(220.123, 'V')).toBe('220.12')
    })

    it('A uses 2 decimals', () => {
      expect(formatMonitorValue(5.6789, 'A')).toBe('5.68')
    })

    it('kPa uses 2 decimals', () => {
      expect(formatMonitorValue(101.325, 'kPa')).toBe('101.33')
    })

    it('rpm uses 0 decimals', () => {
      expect(formatMonitorValue(1499.6, 'rpm')).toBe('1500')
    })
  })

  describe('with unknown unit (fallback to global precision)', () => {
    it('unknown unit falls back to MONITOR_VALUE_PRECISION', () => {
      expect(formatMonitorValue(12.345678, 'lux')).toBe('12.346')
    })

    it('empty unit falls back to global precision', () => {
      expect(formatMonitorValue(12.345678, '')).toBe('12.346')
    })
  })

  describe('non-number values', () => {
    it('returns string representation for strings', () => {
      expect(formatMonitorValue('hello')).toBe('hello')
    })

    it('returns string representation for booleans', () => {
      expect(formatMonitorValue(true)).toBe('true')
    })
  })
})

describe('formatMonitorDateTime', () => {
  it('formats a Date to YYYY-MM-DD HH:mm:ss', () => {
    const d = new Date(2026, 5, 15, 14, 30, 45)
    expect(formatMonitorDateTime(d)).toBe('2026-06-15 14:30:45')
  })

  it('zero-pads single-digit components', () => {
    const d = new Date(2026, 0, 5, 3, 7, 9)
    expect(formatMonitorDateTime(d)).toBe('2026-01-05 03:07:09')
  })
})

describe('formatMonitorAxisTime (HH:mm)', () => {
  it('extracts HH:mm from YYYY-MM-DD HH:mm:ss', () => {
    expect(formatMonitorAxisTime('2026-06-15 14:30:00')).toBe('14:30')
  })

  it('extracts HH:mm from ISO format with T separator', () => {
    expect(formatMonitorAxisTime('2026-06-15T14:30:00')).toBe('14:30')
  })

  it('returns input unchanged when no separator present', () => {
    // Pre-existing behavior: without ' ' or 'T' separator, returns the string verbatim.
    // This is intentional for already-short inputs like '14:30'.
    expect(formatMonitorAxisTime('2026-06-15')).toBe('2026-06-15')
  })

  it('returns date part when separator present but time empty', () => {
    expect(formatMonitorAxisTime('2026-06-15 ')).toBe('06-15')
  })

  it('returns empty for falsy', () => {
    expect(formatMonitorAxisTime('')).toBe('')
    expect(formatMonitorAxisTime(null)).toBe('')
  })
})

describe('computeRangeDays', () => {
  it('returns days between two datetime strings', () => {
    const r = computeRangeDays(['2026-06-15 00:00:00', '2026-06-18 00:00:00'])
    expect(r).toBeCloseTo(3, 1)
  })

  it('handles T-separator format', () => {
    const r = computeRangeDays(['2026-06-15T00:00:00', '2026-06-16T00:00:00'])
    expect(r).toBeCloseTo(1, 1)
  })

  it('returns fractional days for partial ranges', () => {
    const r = computeRangeDays(['2026-06-15 00:00:00', '2026-06-15 12:00:00'])
    expect(r).toBeCloseTo(0.5, 2)
  })

  it('returns 0 for inverted range', () => {
    const r = computeRangeDays(['2026-06-18 00:00:00', '2026-06-15 00:00:00'])
    expect(r).toBe(0)
  })

  it('returns 0 for null input', () => {
    expect(computeRangeDays(null)).toBe(0)
  })

  it('returns 0 for undefined input', () => {
    expect(computeRangeDays(undefined)).toBe(0)
  })

  it('returns 0 for length !== 2 (length=1)', () => {
    expect(computeRangeDays(['2026-06-15'])).toBe(0)
  })

  it('returns 0 for length !== 2 (length=3)', () => {
    expect(computeRangeDays(['2026-06-15', '2026-06-16', '2026-06-17'])).toBe(0)
  })

  it('returns 0 for invalid date strings', () => {
    expect(computeRangeDays(['invalid', '2026-06-15'])).toBe(0)
  })
})

describe('formatAxisTimeAdaptive', () => {
  it('returns empty string for falsy input', () => {
    expect(formatAxisTimeAdaptive('', 'HOUR', 1)).toBe('')
    expect(formatAxisTimeAdaptive(null, 'HOUR', 1)).toBe('')
  })

  describe('MONTH granularity (always YYYY-MM)', () => {
    it('returns YYYY-MM', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'MONTH', 365))
        .toBe('2026-06')
    })
    it('handles T separator', () => {
      expect(formatAxisTimeAdaptive('2026-06-15T14:30', 'MONTH', 365))
        .toBe('2026-06')
    })
  })

  describe('DAY granularity', () => {
    it('short range returns MM-DD', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'DAY', 30))
        .toBe('06-15')
    })
    it('range > 365.5 days returns YY-MM-DD (cross-year clarity)', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'DAY', 400))
        .toBe('26-06-15')
    })
    it('range exactly 365.5 still uses short format', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'DAY', 365.5))
        .toBe('06-15')
    })
  })

  describe('WEEK granularity', () => {
    it('short range returns MM-DD', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'WEEK', 180))
        .toBe('06-15')
    })
    it('range > 365.5 days returns YY-MM-DD', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'WEEK', 500))
        .toBe('26-06-15')
    })
  })

  describe('HOUR granularity', () => {
    it('short range (≤ 1.5 days) returns HH:mm', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'HOUR', 1))
        .toBe('14:30')
    })
    it('range > 1.5 days returns MM-DD HH:mm (cross-day clarity)', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'HOUR', 7))
        .toBe('06-15 14')
    })
    it('range exactly 1.5 still uses short format', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30', 'HOUR', 1.5))
        .toBe('14:30')
    })
  })

  describe('INTRADAY granularity (always HH:mm)', () => {
    it('returns HH:mm regardless of range', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30:45', 'INTRADAY', 1))
        .toBe('14:30')
    })
  })

  describe('input format compatibility', () => {
    it('handles T separator', () => {
      expect(formatAxisTimeAdaptive('2026-06-15T14:30:00', 'HOUR', 7))
        .toBe('06-15 14')
    })

    it('handles space separator', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30:00', 'HOUR', 7))
        .toBe('06-15 14')
    })

    it('strips fractional seconds (.NNN)', () => {
      expect(formatAxisTimeAdaptive('2026-06-15 14:30:45.123', 'HOUR', 7))
        .toBe('06-15 14')
    })
  })
})

describe('constants', () => {
  it('MONITOR_VALUE_PRECISION is 3', () => {
    expect(MONITOR_VALUE_PRECISION).toBe(3)
  })

  it('DEFAULT_PERIOD_WINDOW is 60', () => {
    expect(DEFAULT_PERIOD_WINDOW).toBe(60)
  })

  it('UNIT_PRECISION_MAP has expected entries', () => {
    expect(UNIT_PRECISION_MAP['°C']).toBe(1)
    expect(UNIT_PRECISION_MAP['%']).toBe(0)
    expect(UNIT_PRECISION_MAP['V']).toBe(2)
  })
})
