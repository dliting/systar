import { describe, it, expect } from 'vitest'
import { parseDuration, formatDuration, convertUnit, toSeconds, smartUnit } from '../duration-utils'

// ======================== parseDuration ========================

describe('parseDuration', () => {
  it('parses seconds: "10s" → { amount: 10, unit: "s" }', () => {
    expect(parseDuration('10s')).toEqual({ amount: 10, unit: 's' })
  })

  it('parses minutes: "5m" → { amount: 5, unit: "m" }', () => {
    expect(parseDuration('5m')).toEqual({ amount: 5, unit: 'm' })
  })

  it('parses hours: "2h" → { amount: 2, unit: "h" }', () => {
    expect(parseDuration('2h')).toEqual({ amount: 2, unit: 'h' })
  })

  it('parses days: "1d" → { amount: 1, unit: "d" }', () => {
    expect(parseDuration('1d')).toEqual({ amount: 1, unit: 'd' })
  })

  it('parses "0" → { amount: 0, unit: "s" }', () => {
    expect(parseDuration('0')).toEqual({ amount: 0, unit: 's' })
  })

  it('converts HH:mm:ss to best unit: "00:00:10" → 10s', () => {
    expect(parseDuration('00:00:10')).toEqual({ amount: 10, unit: 's' })
  })

  it('converts HH:mm:ss: "00:05:00" → 5m', () => {
    expect(parseDuration('00:05:00')).toEqual({ amount: 5, unit: 'm' })
  })

  it('converts HH:mm:ss: "01:00:00" → 1h', () => {
    expect(parseDuration('01:00:00')).toEqual({ amount: 1, unit: 'h' })
  })

  it('converts HH:mm:ss non-round: "00:01:30" → 90s', () => {
    expect(parseDuration('00:01:30')).toEqual({ amount: 90, unit: 's' })
  })

  it('returns null for empty string', () => {
    expect(parseDuration('')).toBeNull()
  })

  it('returns null for null', () => {
    expect(parseDuration(null)).toBeNull()
  })

  it('returns null for invalid format', () => {
    expect(parseDuration('abc')).toBeNull()
  })

  it('returns null for cron-like expression', () => {
    expect(parseDuration('0/5 * * * * ?')).toBeNull()
  })

  it('handles whitespace', () => {
    expect(parseDuration('  5m  ')).toEqual({ amount: 5, unit: 'm' })
  })

  it('smart unit: 120s → 2m', () => {
    expect(parseDuration('120s')).toEqual({ amount: 2, unit: 'm' })
  })

  it('smart unit: 3600s → 1h', () => {
    expect(parseDuration('3600s')).toEqual({ amount: 1, unit: 'h' })
  })
})

// ======================== formatDuration ========================

describe('formatDuration', () => {
  it('formats 5m → "5m"', () => {
    expect(formatDuration(5, 'm')).toBe('5m')
  })

  it('formats 10s → "10s"', () => {
    expect(formatDuration(10, 's')).toBe('10s')
  })

  it('formats 0 → "0"', () => {
    expect(formatDuration(0, 's')).toBe('0')
  })

  it('formats null → "0"', () => {
    expect(formatDuration(null, 's')).toBe('0')
  })
})

// ======================== convertUnit ========================

describe('convertUnit', () => {
  it('same unit returns same value', () => {
    expect(convertUnit(5, 'm', 'm')).toBe(5)
  })

  it('minutes to seconds: 5m → 300s', () => {
    expect(convertUnit(5, 'm', 's')).toBe(300)
  })

  it('seconds to minutes: 120s → 2m', () => {
    expect(convertUnit(120, 's', 'm')).toBe(2)
  })

  it('hours to minutes: 2h → 120m', () => {
    expect(convertUnit(2, 'h', 'm')).toBe(120)
  })

  it('days to hours: 1d → 24h', () => {
    expect(convertUnit(1, 'd', 'h')).toBe(24)
  })

  it('fractional result: 90s → 1.5m', () => {
    expect(convertUnit(90, 's', 'm')).toBe(1.5)
  })
})

// ======================== toSeconds ========================

describe('toSeconds', () => {
  it('10s → 10', () => {
    expect(toSeconds(10, 's')).toBe(10)
  })

  it('5m → 300', () => {
    expect(toSeconds(5, 'm')).toBe(300)
  })

  it('2h → 7200', () => {
    expect(toSeconds(2, 'h')).toBe(7200)
  })

  it('1d → 86400', () => {
    expect(toSeconds(1, 'd')).toBe(86400)
  })
})

// ======================== smartUnit ========================

describe('smartUnit', () => {
  it('0 → { 0, "s" }', () => {
    expect(smartUnit(0)).toEqual({ amount: 0, unit: 's' })
  })

  it('10 → { 10, "s" }', () => {
    expect(smartUnit(10)).toEqual({ amount: 10, unit: 's' })
  })

  it('60 → { 1, "m" }', () => {
    expect(smartUnit(60)).toEqual({ amount: 1, unit: 'm' })
  })

  it('120 → { 2, "m" }', () => {
    expect(smartUnit(120)).toEqual({ amount: 2, unit: 'm' })
  })

  it('90 → { 90, "s" } (not divisible by 60)', () => {
    expect(smartUnit(90)).toEqual({ amount: 90, unit: 's' })
  })

  it('3600 → { 1, "h" }', () => {
    expect(smartUnit(3600)).toEqual({ amount: 1, unit: 'h' })
  })

  it('86400 → { 1, "d" }', () => {
    expect(smartUnit(86400)).toEqual({ amount: 1, unit: 'd' })
  })

  it('7200 → { 2, "h" }', () => {
    expect(smartUnit(7200)).toEqual({ amount: 2, unit: 'h' })
  })
})
