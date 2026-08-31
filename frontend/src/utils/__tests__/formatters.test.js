import { describe, it, expect } from 'vitest'
import { stateTagType, kindLabel, kindTagType, formatTimestamp, parseTime } from '../../utils/formatters'

describe('formatters', () => {
  describe('stateTagType', () => {
    it('returns correct tag type for known states', () => {
      expect(stateTagType('NORMAL')).toBe('success')
      expect(stateTagType('WARNING')).toBe('warning')
      expect(stateTagType('ERROR')).toBe('danger')
      expect(stateTagType('OFFLINE')).toBe('info')
    })

    it('defaults to info for unknown state', () => {
      expect(stateTagType('UNKNOWN')).toBe('info')
      expect(stateTagType('')).toBe('info')
    })
  })

  describe('kindLabel', () => {
    it('returns Chinese label for known kinds', () => {
      expect(kindLabel('SPACE')).toBe('空间')
      expect(kindLabel('DEVICE')).toBe('设备')
      expect(kindLabel('PROBE')).toBe('监测器')
      expect(kindLabel('CONTROL')).toBe('控制器')
      expect(kindLabel('SERVICE')).toBe('服务')
    })

    it('returns raw kind for unknown', () => {
      expect(kindLabel('OTHER')).toBe('OTHER')
    })
  })

  describe('kindTagType', () => {
    it('returns correct tag type for kinds', () => {
      expect(kindTagType('PROBE')).toBe('success')
      expect(kindTagType('CONTROL')).toBe('warning')
      expect(kindTagType('DEVICE')).toBe('primary')
    })

    it('defaults to info', () => {
      expect(kindTagType('UNKNOWN')).toBe('info')
    })
  })

  describe('formatTimestamp', () => {
    it('returns empty string for falsy input', () => {
      expect(formatTimestamp(null)).toBe('')
      expect(formatTimestamp(0)).toBe('')
      expect(formatTimestamp('')).toBe('')
    })

    it('formats number timestamp', () => {
      const result = formatTimestamp(1700000000000)
      expect(result).toContain('2023')
    })

    it('formats string with T', () => {
      expect(formatTimestamp('2026-05-30T08:00:00')).toBe('2026-05-30 08:00:00')
    })
  })

  describe('parseTime', () => {
    it('returns empty string for falsy input', () => {
      expect(parseTime(null)).toBe('')
      expect(parseTime('')).toBe('')
    })

    it('parses ISO date string', () => {
      const result = parseTime('2026-05-30T08:30:00')
      expect(result).toBe('2026-05-30 08:30:00')
    })

    it('returns string for invalid date', () => {
      expect(parseTime('not-a-date')).toBe('not-a-date')
    })
  })
})
