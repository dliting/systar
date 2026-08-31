import { systarApi } from '@/api/request'

/**
 * Detect anomalies for a monitor within a time range.
 *
 * Backend returns AnomalyPoint records (record class, Jackson default field names):
 *   { timestamp, actualValue, expectedValue, deviation, severity }
 *
 * This client normalizes to frontend-friendly names:
 *   { time, actual, expected, deviation, severity }
 *
 * Note: systarApi interceptor already unwraps to res.data (HTTP body), so the
 * resolved value IS the array (or, if backend later wraps in {code, data}, the
 * fallback below still works). Array.isArray guard handles both shapes.
 *
 * @param {number} monitorId
 * @param {string} startIso - ISO 8601 start time ("YYYY-MM-DDTHH:mm:ss")
 * @param {string} endIso   - ISO 8601 end time
 * @returns {Promise<Array<{time: string, actual: number, expected: number, deviation: number, severity: string}>>}
 */
export function detectAnomalies(monitorId, startIso, endIso) {
  return systarApi({
    url   : `/api/ops/analysis/anomaly/${monitorId}`,
    method: 'get',
    params: { start: startIso, end: endIso },
  }).then(res => {
    const list = Array.isArray(res) ? res : (res?.data || [])
    return list.map(p => ({
      time     : p.timestamp,
      actual   : p.actualValue,
      expected : p.expectedValue,
      deviation: p.deviation,
      severity : p.severity,
    }))
  })
}
