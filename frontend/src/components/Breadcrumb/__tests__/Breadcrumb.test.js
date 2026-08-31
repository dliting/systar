import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import Breadcrumb from '@/components/Breadcrumb.vue'

describe('Breadcrumb', () => {
  it('renders route titles from matched meta', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div/>' }, children: [
          { path: 'alarm', component: { template: '<div/>' }, meta: { title: '告警管理' } }
        ]}
      ]
    })
    await router.push('/alarm')
    await router.isReady()

    const wrapper = mount(Breadcrumb, {
      global: { plugins: [router], stubs: { ElIcon: true } }
    })
    expect(wrapper.text()).toContain('告警管理')
  })

  it('renders custom items when provided', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div/>' } }]
    })
    const wrapper = mount(Breadcrumb, {
      global: { plugins: [router], stubs: { ElIcon: true } },
      props: { items: [{ title: '资产运维' }, { title: '设备A' }] }
    })
    expect(wrapper.text()).toContain('资产运维')
    expect(wrapper.text()).toContain('设备A')
  })

  it('applies dark theme class', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div/>' } }]
    })
    const wrapper = mount(Breadcrumb, {
      global: { plugins: [router], stubs: { ElIcon: true } },
      props: { items: [{ title: '工单管理' }], theme: 'dark' }
    })
    expect(wrapper.find('.breadcrumb-bar').classes()).toContain('theme-dark')
  })
})
