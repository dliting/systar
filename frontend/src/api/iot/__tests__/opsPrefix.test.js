import { describe, it, expect, vi, beforeEach } from 'vitest'

// Both the vite dev proxy and the packaged server route only /api/** to the
// backend. A bare /ops/** URL resolves to the SPA index.html instead — HTTP
// 200 with an HTML body — so the page renders an empty table and zeroed stat
// cards with no console error and no failed request to catch. The workorder,
// inspection and ledger modules regressed this way (statistics/trend use the
// correct prefix). These tests pin every exported URL to the /api prefix.

vi.mock('@/api/request', () => ({ systarApi: vi.fn() }))

import { systarApi } from '@/api/request'
import * as workorder from '../workorder'
import * as inspection from '../inspection'
import * as ledger from '../ledger'

function everyExportedUrlStartsWithApi(module, moduleName) {
  for (const [name, fn] of Object.entries(module)) {
    systarApi.mockClear()
    fn(1, {}, {}) // covers (query), (id) and (id, data) signatures alike
    const call = systarApi.mock.calls.at(-1)
    expect(call, `${moduleName}.${name} never called systarApi`).toBeTruthy()
    expect(call[0].url, `${moduleName}.${name} must target /api/**`).toMatch(/^\/api\//)
  }
}

describe('/ops API modules carry the /api prefix', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('workorder.js', () => everyExportedUrlStartsWithApi(workorder, 'workorder'))
  it('inspection.js', () => everyExportedUrlStartsWithApi(inspection, 'inspection'))
  it('ledger.js', () => everyExportedUrlStartsWithApi(ledger, 'ledger'))
})
