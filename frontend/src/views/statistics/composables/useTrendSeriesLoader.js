import { ref } from 'vue'
import { getTrendData, getTrendMetadata } from '@/api/iot/trend'
import { detectAnomalies } from '@/api/iot/analysis'
import { pickColor } from './palette'

/**
 * Shared data loader for comparison-chart series.
 *
 * Used by both MonitorTrend.vue (compare mode) and TrendStandalone.vue
 * (multi-ID URL) so the Promise.allSettled + partial-failure + best-effort
 * anomalies logic lives in exactly one place.
 *
 * Granularities eligible for anomaly detection: HOUR and DAY only.
 * INTRADAY produces too-dense points (markPoint overlap); WEEK/MONTH aggregate
 * away single anomalies. detectAnomalies failures are caught silently — the
 * trend series still renders with empty anomaly arrays.
 *
 * @returns {{
 *   series: import('vue').Ref<Array>,
 *   loading: import('vue').Ref<boolean>,
 *   failedCount: import('vue').Ref<number>,
 *   load: (monitorIds:number[], monitorKind:string, dateRange:[string,string], granularity:string, showThresholds?:boolean, showAnomalies?:boolean) => Promise<void>,
 *   clear: () => void,
 * }}
 */
export function useTrendSeriesLoader() {
  const series      = ref([])
  const loading     = ref(false)
  const failedCount = ref(0)

  /**
   * Token guard against racing loads. When user clicks multiple monitors in
   * el-select multiple mode, @change fires for each click — without a token,
   * the slowest load() resolves last and overwrites the result of the
   * latest click. Each load() increments the token at start; before applying
   * results, it checks if it's still the latest. If not, it silently no-ops.
   */
  let loadToken = 0

  /**
   * Load all monitorIds in parallel. Partial failures do not abort the batch —
   * successfully loaded monitors still appear in `series`, and `failedCount`
   * reflects how many did not.
   */
  async function load(monitorIds, monitorKind, dateRange, granularity, showThresholds = true, showAnomalies = true) {
    if (!Array.isArray(monitorIds) || monitorIds.length === 0) {
      series.value = []
      failedCount.value = 0
      return
    }
    if (!dateRange || dateRange.length !== 2) {
      series.value = []
      failedCount.value = 0
      return
    }
    const myToken = ++loadToken
    const [startTime, endTime] = dateRange
    const anomaliesEnabled = showAnomalies && (granularity === 'HOUR' || granularity === 'DAY')

    loading.value = true
    try {
      const results = await Promise.allSettled(
        monitorIds.map(id => loadOneSeries(id, monitorKind, startTime, endTime, granularity, anomaliesEnabled)),
      )
      // Stale guard — a newer load() has started; discard our results
      if (myToken !== loadToken) return

      const ok = []
      let failures = 0
      results.forEach(r => {
        if (r.status === 'fulfilled') ok.push(r.value)
        else failures++
      })
      // Assign colors in selection order
      ok.forEach((s, i) => { s.color = pickColor(i) })
      series.value = ok
      failedCount.value = failures
    } finally {
      // Only clear loading if we're the latest; otherwise a newer load owns it
      if (myToken === loadToken) loading.value = false
    }
  }

  function clear() {
    // Bump token so any in-flight load() result is discarded — without this,
    // a pending load could resolve after clear() and undo the reset.
    loadToken++
    series.value = []
    failedCount.value = 0
  }

  async function loadOneSeries(monitorId, monitorKind, startTime, endTime, granularity, anomaliesEnabled) {
    // Parallel: data + metadata + (optional) anomalies — all three must succeed
    // for the series to be included (Promise.all rejects on any failure → outer
    // allSettled catches and increments failedCount).
    const [dataRes, metaRes] = await Promise.all([
      getTrendData({ monitorId, monitorKind, startTime, endTime, granularity }),
      getTrendMetadata({ monitorId, monitorKind }),
    ])
    const data = dataRes.data || dataRes
    const meta = metaRes.data || metaRes

    // Anomalies are best-effort — failure here should NOT fail the whole series.
    // Caught and returned as empty array so Promise.all above doesn't reject.
    let anomalies = []
    if (anomaliesEnabled) {
      try {
        const startIso = startTime.replace(' ', 'T')
        const endIso   = endTime.replace(' ', 'T')
        const r = await detectAnomalies(monitorId, startIso, endIso)
        anomalies = Array.isArray(r) ? r : []
      } catch (e) {
        // Anomaly service is best-effort; leave anomalies empty
        console.warn('Failed to load anomalies:', e)
      }
    }

    return {
      id        : monitorId,
      caption   : meta.caption || meta.name || `monitor-${monitorId}`,
      unit      : meta.unit || '',
      color     : null,  // assigned by caller after aggregation
      dataPoints: data.dataPoints || [],
      avg5      : data.avg5 || [],
      avg10     : data.avg10 || [],
      avg20     : data.avg20 || [],
      summary   : data.summary || null,
      thresholds: {
        min     : meta.minValue ?? null,
        max     : meta.maxValue ?? null,
        warnCond: meta.warnCond ?? null,
      },
      anomalies,
    }
  }

  return { series, loading, failedCount, load, clear }
}
