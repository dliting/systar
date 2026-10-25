/**
 * Playwright interaction suite for the trend chart component (spec: Playwright
 * UI 交互测试). Runs against the production preview build with page.route
 * fixtures — no backend. Canvas internals are read through the dev hook
 * window.__trendChart() exposed by TrendChart/index.vue (test seam).
 */
import { test, expect } from '@playwright/test'
import { mockTrendApi, trendResponse } from './fixtures'

const STANDALONE = '/trend-standalone?monitorId=1&monitorKind=PROBE'

async function openStandalone(page) {
  await mockTrendApi(page)
  await page.goto(STANDALONE)
  await expect(page.locator('canvas').first()).toBeVisible()
}

async function pickGranularity(page, label) {
  await page.locator('.el-select').first().click()
  await page.locator('.el-select-dropdown__item').filter({ hasText: label }).click()
}

const optionEval = (fn) => `(() => { const c = window.__trendChart(); return c ? (${fn})(c.getOption()) : null })()`

async function dataZoomWindow(page) {
  return page.evaluate(`(() => { const dz = window.__trendChart().getOption().dataZoom[0]; return { start: dz.start, end: dz.end } })()`)
}

test('granularity switch: 日 renders the default line series and requests granularity=DAY', async ({ page }) => {
  await openStandalone(page)
  const reqPromise = page.waitForRequest((r) =>
    r.url().includes('/api/ops/trend/data') && r.url().includes('granularity=DAY'))
  await pickGranularity(page, '日')
  const req = await reqPromise
  // The 24h preset cannot hold DAY buckets — the backend would clamp the
  // request to HOUR while the select still read 日. The view extends to the
  // DAY default (30d) so control and data agree.
  const u = new URL(req.url())
  const secs = (new Date(u.searchParams.get('endTime').replace(' ', 'T')) -
    new Date(u.searchParams.get('startTime').replace(' ', 'T'))) / 1000
  expect(Math.abs(secs - 2_592_000)).toBeLessThanOrEqual(60)
  // D1: every OHLC granularity defaults to a line render, not candlesticks.
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('line')
})

test('箱体 toggle switches the DAY main series between line and candlestick', async ({ page }) => {
  await openStandalone(page)
  await pickGranularity(page, '日')
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('line')
  const candles = page.locator('label.el-checkbox', { hasText: '箱体' })
  await expect(candles).toBeVisible()
  await expect(candles.locator('input')).not.toBeChecked()
  await candles.click()
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('candlestick')
  await candles.click()
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('line')
})

test('preset relative window: 近7天 requests a ~604800s window', async ({ page }) => {
  await openStandalone(page)
  const windowSecondsOf = (url) => {
    const u = new URL(url)
    const s = u.searchParams.get('startTime')
    const e = u.searchParams.get('endTime')
    if (!s || !e) return null
    return (new Date(e.replace(' ', 'T')) - new Date(s.replace(' ', 'T'))) / 1000
  }
  const reqPromise = page.waitForRequest((r) =>
    r.url().includes('/api/ops/trend/data') &&
    Math.abs((windowSecondsOf(r.url()) ?? -1) - 604_800) <= 60)
  await page.locator('.el-radio-button__inner', { hasText: '近7天' }).click()
  const u = new URL((await reqPromise).url())
  const secs = windowSecondsOf(u.toString())
  expect(Math.abs(secs - 604_800)).toBeLessThanOrEqual(60)
})

test('layer toggles: 采样数 adds a second grid in DAY, 极值带 stacks two series in 分时, both default off', async ({ page }) => {
  await openStandalone(page)
  // 分时 (default AUTO→INTRADAY RAW): 极值带 present and unchecked by default
  const extremes = page.locator('label.el-checkbox', { hasText: '极值带' })
  await expect(extremes).toBeVisible()
  await expect(extremes.locator('input')).not.toBeChecked()
  await extremes.click()
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series.filter((s) => s.stack === "band").length')))
    .toBe(2)
  // DAY: 采样数 present and unchecked by default (main series is the D1 line)
  await pickGranularity(page, '日')
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('line')
  const samples = page.locator('label.el-checkbox', { hasText: '采样数' })
  await expect(samples).toBeVisible()
  await expect(samples.locator('input')).not.toBeChecked()
  await samples.click()
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.grid.length')))
    .toBe(2)
})

test('MA legend: DAY mode series include 5期平均', async ({ page }) => {
  await openStandalone(page)
  await pickGranularity(page, '日')
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series.map((s) => s.name).includes("5期平均")')))
    .toBe(true)
})

test('crosshair readout: RAW shows 数值, DAY shows 期初/期末 (D2)', async ({ page }) => {
  await openStandalone(page)
  const box = await page.locator('canvas').first().boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  const readout = page.locator('.readout')
  await expect(readout).toBeVisible()
  // RAW points have open≡close — OHLC wording would be meaningless there.
  await expect(readout).toContainText('数值')
  await expect(readout).not.toContainText('期初')
  // The DAY view fires an anomaly fetch whose response re-renders the chart
  // and clears the crosshair readout (D2) — hover only after it settles.
  const anomalyPromise = page.waitForResponse((r) => r.url().includes('/api/ops/analysis/anomaly'))
  await pickGranularity(page, '日')
  await anomalyPromise
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('line')
  await page.waitForTimeout(300)
  // Move to a fresh pixel — a same-position move emits no mousemove event.
  await page.mouse.move(box.x + box.width / 2 + 30, box.y + box.height / 2)
  await expect(readout).toContainText('期初')
  await expect(readout).toContainText('期末')
})

test('wheel zoom and drag pan change the visible data window', async ({ page }) => {
  await openStandalone(page)
  const box = await page.locator('canvas').first().boundingBox()
  const cx = box.x + box.width / 2
  const cy = box.y + box.height / 2
  const before = await dataZoomWindow(page)
  expect(before).toEqual({ start: 0, end: 100 })
  // Wheel zoom in over the plot center
  await page.mouse.move(cx, cy)
  await page.mouse.wheel(0, -240)
  await expect
    .poll(() => page.evaluate(`(() => { const dz = window.__trendChart().getOption().dataZoom[0]; return dz.start > ${before.start} || dz.end < ${before.end} })()`))
    .toBe(true)
  // Drag pan: shift the window horizontally
  const zoomed = await dataZoomWindow(page)
  await page.mouse.move(cx, cy)
  await page.mouse.down()
  await page.mouse.move(cx - 120, cy, { steps: 6 })
  await page.mouse.up()
  await expect
    .poll(() => page.evaluate(`(() => { const dz = window.__trendChart().getOption().dataZoom[0]; return { start: dz.start, end: dz.end } })()`))
    .not.toEqual(zoomed)
})

test('drill-down: clicking a DAY point loads the next granularity for that day', async ({ page }) => {
  await openStandalone(page)
  await page.locator('.el-select').first().click()
  // The DAY view triggers an anomaly fetch whose response re-renders the
  // chart; the candle click must land after that settles or the zrender
  // hit-test races setOption (probe-verified).
  const anomalyPromise = page.waitForResponse((r) => r.url().includes('/api/ops/analysis/anomaly'))
  await page.locator('.el-select-dropdown__item').filter({ hasText: '日' }).click()
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0].type')))
    .toBe('line')
  await anomalyPromise
  await page.waitForTimeout(300)
  // Pixel of point #2 (avg 24) on the default line series
  const pos = await page.evaluate(`(() => {
    const c = window.__trendChart()
    const p = c.convertToPixel({ seriesIndex: 0 }, [2, 24])
    const r = c.getDom().getBoundingClientRect()
    return { x: r.x + p[0], y: r.y + p[1] }
  })()`)
  // Spec F: click → next granularity (NEXT ladder DAY→HOUR) over the clicked
  // day. The click lands on the line body, which ECharts does not map to a
  // series click — the component snaps plot-area clicks to the nearest
  // category (see index.vue registerHandlers zr fallback).
  const reqPromise = page.waitForRequest((r) =>
    r.url().includes('/api/ops/trend/data') && r.url().includes('granularity=HOUR'))
  await page.mouse.click(pos.x, pos.y)
  const req = await reqPromise
  const params = new URL(req.url()).searchParams
  expect(params.get('startTime')).toBe('2026-10-20 00:00:00')
  expect(params.get('endTime')).toBe('2026-10-21 00:00:00')
})

test('anomaly markers: DAY view renders the analysis API anomaly as a markPoint', async ({ page }) => {
  await openStandalone(page)
  const anomalyPromise = page.waitForResponse((r) => r.url().includes('/api/ops/analysis/anomaly'))
  await pickGranularity(page, '日')
  await anomalyPromise
  // The fixture anomaly (2026-10-22, high severity) must produce exactly one
  // marker on the candlestick series — the builders once read the wrong
  // field shape and rendered none.
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series.find((s) => s.markPoint)?.markPoint?.data.length ?? 0')))
    .toBe(1)
})

test('error → retry: 500 shows the error overlay, 重试 recovers', async ({ page }) => {
  await mockTrendApi(page, { failData: true })
  await page.goto(STANDALONE)
  const error = page.locator('.trend-error')
  await expect(error).toBeVisible()
  await expect(error).toContainText('重试')
  // Swap the failing route for a succeeding one, then click 重试
  await page.unroute('**/api/ops/trend/data*')
  await page.route('**/api/ops/trend/data*', (r) =>
    r.fulfill({ json: { code: 0, data: trendResponse(r.request().url()) } }))
  await error.getByRole('button', { name: '重试' }).click()
  await expect(error).toBeHidden()
  // RAW gap insertion (detect interval 10s vs 60s sample spacing) legitimately
  // interleaves null break points — count only the real samples. The optional
  // chain keeps the poll retrying through the brief empty-frame state right
  // after the overlay clears (expect.poll propagates callback throws).
  await expect
    .poll(() => page.evaluate(optionEval('(o) => o.series[0]?.data.filter((d) => d != null).length ?? 0')))
    .toBe(60)
})

test('threshold markLines render from metadata and the 阈值 switch removes them', async ({ page }) => {
  await openStandalone(page)
  // metadata carries minValue/maxValue → 上限/下限 markLines on the main series
  const markLineCount = '(o) => o.series.find((s) => s.markLine)?.markLine?.data.length ?? 0'
  await expect
    .poll(() => page.evaluate(optionEval(markLineCount)))
    .toBe(2)
  await page.locator('.el-switch').first().click()
  await expect
    .poll(() => page.evaluate(optionEval(markLineCount)))
    .toBe(0)
})

test('custom absolute range: picker values reach the request verbatim', async ({ page }) => {
  await openStandalone(page)
  await page.locator('.el-date-editor').first().click()
  await page.locator('input.el-range-input').first().fill('2026-09-01 00:00:00')
  await page.locator('input.el-range-input').nth(1).fill('2026-09-02 00:00:00')
  // Enter on the end input confirms the typed range: the panel's footer OK
  // stays disabled until both inputs are parsed, and fill() never blurs.
  const reqPromise = page.waitForRequest((r) =>
    r.url().includes('/api/ops/trend/data') && r.url().includes('2026-09-01'))
  await page.locator('input.el-range-input').nth(1).press('Enter')
  const req = await reqPromise
  const params = new URL(req.url()).searchParams
  expect(params.get('startTime')).toBe('2026-09-01 00:00:00')
  expect(params.get('endTime')).toBe('2026-09-02 00:00:00')
})
