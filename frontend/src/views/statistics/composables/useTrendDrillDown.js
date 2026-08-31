/**
 * Click drill-down from coarser to finer granularity.
 */

const NEXT = { MONTH: 'WEEK', WEEK: 'DAY', DAY: 'HOUR', HOUR: 'INTRADAY' }

export function getDrillDownRange(candleTime, fromGranularity) {
  const finer = NEXT[fromGranularity]
  if (!finer) return null
  const start = new Date(candleTime)
  const end = new Date(start)
  switch (fromGranularity) {
    case 'MONTH': end.setMonth(end.getMonth() + 1); break
    case 'WEEK': end.setDate(end.getDate() + 7); break
    case 'DAY': end.setDate(end.getDate() + 1); break
    case 'HOUR': end.setHours(end.getHours() + 1); break
  }
  return { startTime: start, endTime: end, granularity: finer }
}
