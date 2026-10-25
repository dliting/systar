import { describe, it, expect } from 'vitest'
import { valueRange } from '../yRange'

const pts = (...pairs) => pairs.map(([min, max]) => ({ min, max }))

describe('valueRange', () => {
  it('spans the data min/max with no thresholds', () => {
    expect(valueRange(pts([10, 20], [12, 30]), null)).toEqual({ lo: 10, hi: 30, showMax: false, showMin: false })
  })
  it('skips null min/max entries (RAW gap markers)', () => {
    expect(valueRange([{ min: 10, max: 20 }, { min: null, max: null }], null).lo).toBe(10)
  })
  it('returns undefined bounds for empty data', () => {
    expect(valueRange([], { min: 5, max: 35 })).toEqual({ lo: undefined, hi: undefined, showMax: false, showMin: false })
  })
  it('draws thresholds inside the data range without extending it', () => {
    const r = valueRange(pts([10, 30]), { min: 15, max: 25 })
    expect(r).toEqual({ lo: 10, hi: 30, showMax: true, showMin: true })
  })
  it('extends the range for a threshold within 2x the data span of its edge', () => {
    // span 20 → 2x = 40; 35 is 5 above hi=30
    const r = valueRange(pts([10, 30]), { max: 35 })
    expect(r.showMax).toBe(true)
    expect(r.hi).toBe(35)
  })
  it('omits a threshold farther than 2x the span and leaves the range alone', () => {
    const r = valueRange(pts([10, 30]), { max: 200, min: -200 })
    expect(r).toEqual({ lo: 10, hi: 30, showMax: false, showMin: false })
  })
  it('handles a one-sided threshold', () => {
    const r = valueRange(pts([10, 30]), { min: 12, max: null })
    expect(r).toEqual({ lo: 10, hi: 30, showMax: false, showMin: true })
  })
  it('showThresholds=false suppresses both flags', () => {
    expect(valueRange(pts([10, 30]), { min: 12, max: 25 }, false).showMax).toBe(false)
  })
  it('flat data gets a nonzero span fallback so near thresholds still count', () => {
    // hi=lo=20 → span = |20|*0.05 = 1 → 2x = 2; 21.5 is 1.5 above hi
    const r = valueRange(pts([20, 20]), { max: 21.5 })
    expect(r.showMax).toBe(true)
    expect(r.hi).toBe(21.5)
  })
})
