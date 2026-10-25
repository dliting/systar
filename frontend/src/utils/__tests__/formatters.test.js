import { describe, it, expect } from 'vitest'
import { stateTagType, kindLabel, kindTagType, formatTimestamp, parseTime, formatMetricValue, splitPathSegments, formatDisplayPath } from '../../utils/formatters'

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
      expect(kindLabel('DEVICE')).toBe('设备')
      expect(kindLabel('PROBE')).toBe('监测器')
      expect(kindLabel('CONTROL')).toBe('控制器')
      expect(kindLabel('SERVICE')).toBe('服务')
    })

    it('labels GROUP nodes', () => {
      expect(kindLabel('GROUP')).toBe('分组')
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

    it('tags GROUP nodes as info', () => {
      expect(kindTagType('GROUP')).toBe('info')
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

  // Operators read whole numbers and at most a couple of decimals — the raw
  // double (12.77434613895595) on screen is developer noise.
  describe('formatMetricValue', () => {
    it('rounds a long float to two decimals', () => {
      expect(formatMetricValue(12.77434613895595)).toBe('12.77')
    })

    it('keeps integers bare', () => {
      expect(formatMetricValue(25)).toBe('25')
      expect(formatMetricValue(25.0)).toBe('25')
    })

    it('strips trailing zeros', () => {
      expect(formatMetricValue(25.10)).toBe('25.1')
      expect(formatMetricValue(0.5)).toBe('0.5')
    })

    it('rounds negatives symmetrically', () => {
      expect(formatMetricValue(-3.14159)).toBe('-3.14')
    })

    it('keeps four decimals below one so small readings do not collapse to zero', () => {
      expect(formatMetricValue(0.001234)).toBe('0.0012')
    })

    it('formats numeric strings like numbers', () => {
      expect(formatMetricValue('12.77434613895595')).toBe('12.77')
    })

    it('passes non-numeric values through (control states)', () => {
      expect(formatMetricValue('ON')).toBe('ON')
      expect(formatMetricValue(true)).toBe('true')
    })

    it('shows an em dash for missing values', () => {
      expect(formatMetricValue(null)).toBe('—')
      expect(formatMetricValue(undefined)).toBe('—')
    })
  })

  // Asset paths arrive as '->'-joined caption segments from the backend;
  // the UI renders them as breadcrumb segments / an arrow-joined string.
  describe('splitPathSegments', () => {
    it('splits a caption path into segments', () => {
      expect(splitPathSegments('温湿度传感器组->102湿度')).toEqual(['温湿度传感器组', '102湿度'])
    })

    it('drops empty segments from leading or trailing separators', () => {
      expect(splitPathSegments('->a->')).toEqual(['a'])
    })

    it('returns an empty array for empty input', () => {
      expect(splitPathSegments('')).toEqual([])
      expect(splitPathSegments(null)).toEqual([])
      expect(splitPathSegments(undefined)).toEqual([])
    })
  })

  describe('formatDisplayPath', () => {
    it('joins segments with an arrow', () => {
      expect(formatDisplayPath('a->b->c')).toBe('a → b → c')
    })

    it('renders a single segment bare', () => {
      expect(formatDisplayPath('102湿度')).toBe('102湿度')
    })

    it('returns an empty string for empty input', () => {
      expect(formatDisplayPath('')).toBe('')
      expect(formatDisplayPath(null)).toBe('')
    })
  })
})
