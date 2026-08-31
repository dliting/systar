import { describe, it, expect } from 'vitest'
import { COMPARISON_PALETTE, pickColor, groupSeriesByUnit } from '../palette'

describe('palette', () => {
  describe('COMPARISON_PALETTE', () => {
    it('has 8 high-contrast colors', () => {
      expect(COMPARISON_PALETTE).toHaveLength(8)
    })

    it('contains expected signature colors', () => {
      expect(COMPARISON_PALETTE).toContain('#00d4ff')  // cyan — matches single-chart mean
      expect(COMPARISON_PALETTE).toContain('#ff5252')  // red — matches high severity
      expect(COMPARISON_PALETTE).toContain('#69f0ae')  // green — matches low boundary
      expect(COMPARISON_PALETTE).toContain('#ffab00')  // amber — matches MA5
    })

    it('all entries are valid hex colors', () => {
      for (const c of COMPARISON_PALETTE) {
        expect(c).toMatch(/^#[0-9a-fA-F]{6}$/)
      }
    })
  })

  describe('pickColor', () => {
    it('returns palette[0] for index 0', () => {
      expect(pickColor(0)).toBe(COMPARISON_PALETTE[0])
    })

    it('returns palette[7] for index 7', () => {
      expect(pickColor(7)).toBe(COMPARISON_PALETTE[7])
    })

    it('wraps around modulo 8 for index 8', () => {
      expect(pickColor(8)).toBe(COMPARISON_PALETTE[0])
    })

    it('wraps around modulo 8 for index 16', () => {
      expect(pickColor(16)).toBe(COMPARISON_PALETTE[0])
    })

    it('wraps around for index 10 (returns palette[2])', () => {
      expect(pickColor(10)).toBe(COMPARISON_PALETTE[2])
    })

    it('handles negative index by wrapping via modulo', () => {
      // JS modulo of negative is negative; pickColor should guard against this
      // to avoid returning palette[-1] === undefined
      expect(pickColor(-1)).toBeDefined()
    })
  })

  describe('groupSeriesByUnit', () => {
    it('groups single unit into one group', () => {
      const series = [
        { id: 1, unit: '°C' },
        { id: 2, unit: '°C' },
        { id: 3, unit: '°C' },
      ]
      const groups = groupSeriesByUnit(series)
      expect(groups.size).toBe(1)
      expect(groups.get('°C')).toHaveLength(3)
    })

    it('groups two units into two groups', () => {
      const series = [
        { id: 1, unit: '°C' },
        { id: 2, unit: '%' },
      ]
      const groups = groupSeriesByUnit(series)
      expect(groups.size).toBe(2)
      expect(groups.get('°C')).toHaveLength(1)
      expect(groups.get('%')).toHaveLength(1)
    })

    it('treats null and empty unit as same "(无单位)" group', () => {
      const series = [
        { id: 1, unit: null },
        { id: 2, unit: '' },
        { id: 3, unit: undefined },
      ]
      const groups = groupSeriesByUnit(series)
      expect(groups.size).toBe(1)
      expect(groups.get('(无单位)')).toHaveLength(3)
    })

    it('separates null/empty from named units', () => {
      const series = [
        { id: 1, unit: '°C' },
        { id: 2, unit: null },
      ]
      const groups = groupSeriesByUnit(series)
      expect(groups.size).toBe(2)
      expect(groups.has('°C')).toBe(true)
      expect(groups.has('(无单位)')).toBe(true)
    })

    it('preserves insertion order within each group', () => {
      const series = [
        { id: 1, unit: '°C' },
        { id: 2, unit: '%' },
        { id: 3, unit: '°C' },
      ]
      const groups = groupSeriesByUnit(series)
      expect(groups.get('°C').map(s => s.id)).toEqual([1, 3])
      expect(groups.get('%').map(s => s.id)).toEqual([2])
    })

    it('handles empty array input', () => {
      const groups = groupSeriesByUnit([])
      expect(groups.size).toBe(0)
    })
  })
})
