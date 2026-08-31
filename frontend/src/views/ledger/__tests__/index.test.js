import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/ledger', () => ({
  listDevices: vi.fn().mockResolvedValue({ data: [] }),
  getDeviceStats: vi.fn().mockResolvedValue({ data: { total: 0, inService: 0, repairing: 0, retired: 0 } }),
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  EnhancedTable: {
    template: '<div class="stub-enhanced-table"><slot /></div>',
  },
}

import Ledger from '../index.vue'

function mountLedger() {
  return mount(Ledger, {
    global: { plugins: [ElementPlus], stubs }
  })
}

describe('Ledger', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders ledger page container', () => {
    const wrapper = mountLedger()
    expect(wrapper.find('.ops-container').exists()).toBe(true)
  })

  it('renders page title', () => {
    const wrapper = mountLedger()
    expect(wrapper.text()).toContain('设备台账')
  })

  it('renders stat cards row', () => {
    const wrapper = mountLedger()
    expect(wrapper.find('.stat-row').exists()).toBe(true)
  })

  it('calls API on mount', async () => {
    mountLedger()
    await flushPromises()
    const { listDevices } = await import('@/api/iot/ledger')
    expect(listDevices).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountLedger()).not.toThrow()
  })
})
