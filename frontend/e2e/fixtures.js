/**
 * Deterministic API fixtures for the Playwright suite — page.route intercepts
 * /api/** so the specs never depend on a backend process. Envelope is the
 * controller's Result<T> shape ({ code: 0, data }); param names mirror
 * src/api/iot/trend.js (monitorId / monitorKind / startTime / endTime /
 * granularity — axios drops null granularity, so searchParams.get() is null
 * for AUTO requests).
 */
const pad = (n) => String(n).padStart(2, '0')
export const fmt = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

// 8 daily candles so the 5期平均 overlay has real values (a 2-point fixture
// would render an all-null MA line).
export const OHLC_POINTS = [
  { t: '2026-10-18 00:00:00', avg: 20, max: 30, min: 10, open: 10, close: 30, count: 5 },
  { t: '2026-10-19 00:00:00', avg: 25, max: 28, min: 22, open: 28, close: 22, count: 3 },
  { t: '2026-10-20 00:00:00', avg: 24, max: 27, min: 20, open: 22, close: 26, count: 4 },
  { t: '2026-10-21 00:00:00', avg: 26, max: 29, min: 23, open: 26, close: 27, count: 6 },
  { t: '2026-10-22 00:00:00', avg: 23, max: 27, min: 19, open: 27, close: 20, count: 2 },
  { t: '2026-10-23 00:00:00', avg: 21, max: 24, min: 18, open: 20, close: 23, count: 5 },
  { t: '2026-10-24 00:00:00', avg: 20, max: 30, min: 10, open: 10, close: 30, count: 5 },
  { t: '2026-10-25 00:00:00', avg: 25, max: 28, min: 22, open: 28, close: 22, count: 3 },
]
export const RAW_POINTS = Array.from({ length: 60 }, (_, i) => ({
  t: `2026-10-25 ${pad(Math.floor(i / 60))}:${pad(i % 60)}:00`,
  avg: 20 + i, max: 21 + i, min: 19 + i, open: 20 + i, close: 20 + i, count: 1,
}))

export const SUMMARY = { currentValue: 25, periodMax: 30, periodMin: 10, totalSamples: 8 }

export function trendResponse(url) {
  const g = new URL(url).searchParams.get('granularity')
  if (g === 'INTRADAY' || g === null) {
    return { granularity: 'INTRADAY', mode: 'RAW', points: RAW_POINTS, bucketSeconds: null, summary: SUMMARY }
  }
  return { granularity: g, mode: 'OHLC', points: OHLC_POINTS, bucketSeconds: 3600, summary: SUMMARY }
}

export async function mockTrendApi(page, { failData = false } = {}) {
  await page.addInitScript(() => localStorage.setItem('Systar-Token', 'e2e-token'))
  await page.route('**/api/ops/trend/metadata*', (r) =>
    r.fulfill({ json: { code: 0, data: { unit: '℃', monitorName: 'UPS 温度', detectIntervalSeconds: 10, minValue: 5, maxValue: 35 } } }))
  await page.route('**/api/ops/trend/default*', (r) =>
    r.fulfill({ json: { code: 0, data: trendResponse(r.request().url()) } }))
  await page.route('**/api/ops/trend/data*', (r) =>
    failData
      ? r.fulfill({ status: 500, json: { code: 500, msg: 'boom' } })
      : r.fulfill({ json: { code: 0, data: trendResponse(r.request().url()) } }))
  // Backend AnomalyPoint record shape (src/api/iot/analysis.js normalizes it
  // to {time, actual, expected, deviation, severity}). 2026-10-22 lands on
  // candle #5 — away from the drill-down test's candle #2 click target.
  await page.route('**/api/ops/analysis/anomaly/**', (r) =>
    r.fulfill({ json: { code: 0, data: [
      { timestamp: '2026-10-22T12:00:00', actualValue: 27, expectedValue: 23, deviation: 3.1, severity: 'high' },
    ] } }))
}
