/**
 * Consolidated operator-UX interaction suite (tasks #72/#76): the failure
 * paths an operations engineer actually hits — backend down, wrong password,
 * a live alarm push, and a dropped realtime connection — must surface as
 * plain Chinese guidance, not raw transport text. Runs against the preview
 * build with page.route/page.routeWebSocket fixtures, no backend process.
 */
import { test, expect } from '@playwright/test'

async function mockShellApis(page) {
  await page.addInitScript(() => localStorage.setItem('Systar-Token', 'e2e-token'))
  // Generic success for everything the shell and the operations view fetch on
  // mount — these specs assert on the header (bell / banner), not page data,
  // and the layout renders independently of the router-view content.
  await page.route('**/api/**', (r) => r.fulfill({ json: { code: 0, data: null } }))
}

test('login with the backend down shows the actionable network error, not "Network Error"', async ({ page }) => {
  await page.route('**/api/auth/login*', (r) => r.abort('connectionrefused'))
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill('admin')
  await page.getByPlaceholder('密码').fill('whatever')
  await page.locator('.login-btn').click()
  const message = page.locator('.el-message').first()
  await expect(message).toContainText('无法连接服务器，请确认后端服务已启动')
  await expect(message).not.toContainText('Network Error')
})

test('wrong password shows the real reason, not the expired-session dialog', async ({ page }) => {
  // The login endpoint answers a bad password with body code 401 — the
  // interceptor must not confuse it with an expired session (re-login dialog).
  await page.route('**/api/auth/login*', (r) =>
    r.fulfill({ json: { code: 401, message: '用户名或密码错误' } }))
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill('admin')
  await page.getByPlaceholder('密码').fill('wrongpass')
  await page.locator('.login-btn').click()
  await expect(page.locator('.el-message').first()).toContainText('用户名或密码错误')
  await expect(page.locator('.el-message-box')).toHaveCount(0)
})

test('a live alarm push surfaces in the notification bell with its rank label', async ({ page }) => {
  await mockShellApis(page)
  await page.routeWebSocket('**/ws**', (route) => {
    route.send(JSON.stringify({
      type: 'alarm', alarmMessageId: 9001, eventRankId: 2, assetId: 1201, caption: 'UPS 温度超限',
    }))
  })
  await page.goto('/operations') // the view that opens the realtime connection
  await expect(page.locator('.bell-badge .el-badge__content')).toBeVisible()
  await page.locator('.bell-icon').click()
  await expect(page.locator('.notification-text').first()).toContainText('UPS 温度超限（警告）')
})

test('dropping the realtime connection shows the reconnect banner', async ({ page }) => {
  await mockShellApis(page)
  await page.routeWebSocket('**/ws**', (route) => {
    // Close from the server side shortly after open; the store's backoff
    // reconnect hits the same route, so the banner stays up.
    setTimeout(() => route.close(), 300)
  })
  await page.goto('/operations')
  const banner = page.locator('.ws-offline-badge')
  await expect(banner).toBeVisible()
  await expect(banner).toContainText('实时连接已断开，正在重连')
})
