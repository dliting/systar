/**
 * Asset-tree children rendering (2026-10-25 regression): probes mounted
 * under a device vanished from the 资产运维 tree when their source services
 * failed to load at startup. Pins the UI contract that the tree renders the
 * child hierarchy returned by /api/monitor/asset-tree — a device node with
 * probe children must expose those probes after expansion.
 */
import { test, expect } from '@playwright/test'

const FOREST = [{
  key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', id: null,
  name: 'ahu_group', caption: '设备', state: null, enabled: null,
  children: [{
    key: 'ASSET:1101', nodeKind: 'ASSET', assetKind: 'DEVICE', id: 1101, assetRowId: 60,
    name: 'ahu_sim_01', caption: '空调机组模拟', state: 'NORMAL', enabled: true,
    children: [{
      key: 'ASSET:1201', nodeKind: 'ASSET', assetKind: 'PROBE', id: 1201, assetRowId: 61,
      name: 'sim_supply_temp', caption: '送风温度', state: 'NORMAL', enabled: true, children: [],
    }],
  }],
}]

async function expandNode(page, text) {
  const node = page.locator('.el-tree-node__content', { hasText: text })
  await expect(node).toBeVisible()
  const icon = node.locator('.el-tree-node__expand-icon')
  if (!(await icon.evaluate((el) => el.classList.contains('expanded')))) await icon.click()
}

test('the operations tree exposes probes mounted under their device', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('Systar-Token', 'e2e-token'))
  await page.route('**/api/**', (r) => r.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/monitor/asset-tree**', (r) => r.fulfill({ json: { code: 0, data: FOREST } }))

  await page.goto('/operations')
  await expandNode(page, '设备')
  await expandNode(page, '空调机组模拟')
  await expect(page.locator('.el-tree-node__content', { hasText: '送风温度' })).toBeVisible()
})
