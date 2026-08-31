import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    token: null,
    login: vi.fn().mockResolvedValue(undefined)
  }))
}))

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({
    push: vi.fn(),
    replace: vi.fn()
  })),
  useRoute: vi.fn(() => ({ path: '/login' }))
}))

import Login from '../index.vue'

function mountLogin() {
  return mount(Login, {
    global: {
      plugins: [ElementPlus, createPinia()]
    }
  })
}

describe('Login', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders login form', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('.login-wrapper').exists()).toBe(true)
    expect(wrapper.find('.login-title').text()).toContain('Systar')
  })

  it('has username and password fields', () => {
    const wrapper = mountLogin()
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)
  })

  it('has login button', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('.login-btn').exists()).toBe(true)
  })

  it('renders without errors', () => {
    expect(() => mountLogin()).not.toThrow()
  })
})
