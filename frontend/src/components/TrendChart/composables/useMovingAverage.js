import { computed } from 'vue'

/** Pure sliding-window mean; any null inside a window makes that output null. */
export function movingAverage(values, period) {
  const out = new Array(values.length).fill(null)
  let sum = 0
  let nulls = 0
  for (let i = 0; i < values.length; i++) {
    const v = values[i]
    if (v == null) nulls++
    else sum += v
    if (i >= period) {
      const old = values[i - period]
      if (old == null) nulls--
      else sum -= old
    }
    if (i >= period - 1 && nulls === 0) out[i] = sum / period
  }
  return out
}

/** MA overlays (5/10/20 periods) over a trend point array's avg values. */
export function useMovingAverage(pointsRef) {
  const avgs = computed(() => (pointsRef.value || []).map(p => p.avg))
  return {
    ma5: computed(() => movingAverage(avgs.value, 5)),
    ma10: computed(() => movingAverage(avgs.value, 10)),
    ma20: computed(() => movingAverage(avgs.value, 20)),
  }
}
