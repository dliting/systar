import { describe, it, expect } from 'vitest'
import { ref, nextTick } from 'vue'
import { movingAverage, useMovingAverage } from '../useMovingAverage'

describe('movingAverage', () => {
  it('averages sliding windows and null-prefixes the warm-up', () => {
    expect(movingAverage([1, 2, 3, 4], 2)).toEqual([null, 1.5, 2.5, 3.5])
  })
  it('yields null for windows containing a gap', () => {
    expect(movingAverage([1, null, 3, 4], 2)).toEqual([null, null, null, 3.5])
  })
  it('returns all null when period exceeds the series', () => {
    expect(movingAverage([1, 2], 5)).toEqual([null, null])
  })
})

describe('useMovingAverage', () => {
  it('tracks points reactively and aligns to the point array', async () => {
    const points = ref([
      { t: 'a', avg: 2 }, { t: 'b', avg: 4 },
    ])
    const { ma5, ma10, ma20 } = useMovingAverage(points)
    expect(ma5.value).toEqual([null, null])
    points.value = [...points.value, { t: 'c', avg: 6 }]
    await nextTick()
    expect(ma5.value).toHaveLength(3)
    expect(ma20.value.every(v => v === null)).toBe(true)
  })
})
