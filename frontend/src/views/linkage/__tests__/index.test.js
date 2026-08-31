import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/linkage', () => ({
  listLinkageRules: vi.fn().mockResolvedValue({ data: [] }),
  createLinkageRule: vi.fn().mockResolvedValue({ data: 1 }),
  updateLinkageRule: vi.fn().mockResolvedValue({}),
  deleteLinkageRule: vi.fn().mockResolvedValue({}),
  toggleLinkageRule: vi.fn().mockResolvedValue({}),
  getAssetTree: vi.fn().mockResolvedValue({ data: { id: 1, name: 'root', kind: 'SPACE', children: [] } })
}))

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn(),
  showSystarSuccess: vi.fn()
}))

const stubs = {
  'right-toolbar': { template: '<div class="stub-toolbar" />' },
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  EnhancedTable: { template: '<div class="stub-enhanced-table"><slot /></div>' },
  Monitor: { template: '<span />' },
  Switch: { template: '<span />' },
  Right: { template: '<span />' },
  WarningFilled: { template: '<span />' },
  FolderOpened: { template: '<span />' },
  Coin: { template: '<span />' },
}

import Linkage from '../index.vue'

function mountLinkage() {
  return mount(Linkage, {
    global: { plugins: [ElementPlus], stubs }
  })
}

describe('Linkage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders linkage page container', () => {
    const wrapper = mountLinkage()
    expect(wrapper.find('.linkage-page').exists()).toBe(true)
  })

  it('renders add rule button', () => {
    const wrapper = mountLinkage()
    expect(wrapper.text()).toContain('新增规则')
  })

  it('calls listLinkageRules on mount', async () => {
    mountLinkage()
    await flushPromises()
    const { listLinkageRules } = await import('@/api/iot/linkage')
    expect(listLinkageRules).toHaveBeenCalled()
  })

  it('renders without errors', () => {
    expect(() => mountLinkage()).not.toThrow()
  })
})
