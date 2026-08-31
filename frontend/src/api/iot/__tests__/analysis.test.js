import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock systarApi before importing analysis.js
vi.mock('@/api/request', () => ({
  systarApi: vi.fn(),
}))

import { systarApi } from '@/api/request'
import { detectAnomalies } from '../analysis'

describe('detectAnomalies', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('maps backend field names to frontend field names', async () => {
    systarApi.mockResolvedValue([
      {
        timestamp    : '2026-06-15T14:23:45',
        actualValue  : 95.2,
        expectedValue: 22.1,
        deviation    : 3.1,
        severity     : 'high',
      },
    ])

    const result = await detectAnomalies(1, '2026-06-01T00:00:00', '2026-06-30T00:00:00')

    expect(result).toHaveLength(1)
    expect(result[0]).toEqual({
      time     : '2026-06-15T14:23:45',
      actual   : 95.2,
      expected : 22.1,
      deviation: 3.1,
      severity : 'high',
    })
  })

  it('passes monitorId and ISO times to backend endpoint', async () => {
    systarApi.mockResolvedValue([])
    await detectAnomalies(42, '2026-06-01T00:00:00', '2026-06-30T23:59:59')

    expect(systarApi).toHaveBeenCalledWith({
      url   : '/api/ops/analysis/anomaly/42',
      method: 'get',
      params: { start: '2026-06-01T00:00:00', end: '2026-06-30T23:59:59' },
    })
  })

  it('returns empty array when backend returns empty array', async () => {
    systarApi.mockResolvedValue([])
    const result = await detectAnomalies(1, '2026-06-01T00:00:00', '2026-06-02T00:00:00')
    expect(result).toEqual([])
  })

  it('returns empty array when backend returns null (res falls back)', async () => {
    systarApi.mockResolvedValue(null)
    const result = await detectAnomalies(1, '2026-06-01T00:00:00', '2026-06-02T00:00:00')
    expect(result).toEqual([])
  })

  it('falls back to res.data if backend wraps response in {code, data}', async () => {
    systarApi.mockResolvedValue({
      code: 0,
      data: [
        { timestamp: '2026-06-15T14:00:00', actualValue: 90, expectedValue: 20, deviation: 2.5, severity: 'medium' },
      ],
    })
    const result = await detectAnomalies(1, '2026-06-01T00:00:00', '2026-06-02T00:00:00')
    expect(result).toHaveLength(1)
    expect(result[0].actual).toBe(90)
  })

  it('maps multiple anomaly points', async () => {
    systarApi.mockResolvedValue([
      { timestamp: '2026-06-01T01:00:00', actualValue: 50, expectedValue: 25, deviation: 2.1, severity: 'medium' },
      { timestamp: '2026-06-01T02:00:00', actualValue: 80, expectedValue: 25, deviation: 4.5, severity: 'high' },
      { timestamp: '2026-06-01T03:00:00', actualValue: 60, expectedValue: 25, deviation: 2.8, severity: 'medium' },
    ])
    const result = await detectAnomalies(1, '2026-06-01T00:00:00', '2026-06-02T00:00:00')
    expect(result).toHaveLength(3)
    expect(result.map(p => p.severity)).toEqual(['medium', 'high', 'medium'])
  })
})
