import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('vue-router', () => ({
  useRoute: vi.fn(() => ({ path: '/system/user' })),
  useRouter: vi.fn()
}))

import System from '../index.vue'

function mountSystem() {
  return mount(System, {
    global: {
      plugins: [ElementPlus],
      stubs: {
        'router-link': true,
        'router-view': true
      }
    }
  })
}

describe('System', () => {
  it('renders sidebar', () => {
    const wrapper = mountSystem()
    expect(wrapper.find('.sys-sidebar').exists()).toBe(true)
  })

  it('renders content area', () => {
    const wrapper = mountSystem()
    expect(wrapper.find('.sys-content').exists()).toBe(true)
  })

  it('renders 8 router-link stubs for menu items', () => {
    const wrapper = mountSystem()
    const links = wrapper.findAll('router-link-stub')
    expect(links.length).toBe(8)
  })

  it('has correct menu item hrefs', () => {
    const wrapper = mountSystem()
    const links = wrapper.findAll('router-link-stub')
    const hrefs = links.map(l => l.attributes('to'))
    expect(hrefs).toContain('/system/user')
    expect(hrefs).toContain('/system/role')
    expect(hrefs).toContain('/system/log')
    expect(hrefs).toContain('/system/retention')
  })

  it('renders without errors', () => {
    expect(() => mountSystem()).not.toThrow()
  })
})
