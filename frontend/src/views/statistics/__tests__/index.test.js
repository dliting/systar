import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const childStubs = {
  Breadcrumb: { template: '<div class="stub-breadcrumb" />' },
  StatsFilterBar: { template: '<div class="stub-filter" />' },
  StatsNavTree: { template: '<div class="stub-nav" />' },
  StatsContentArea: { template: '<div class="stub-content" />' },
}

import Statistics from '../index.vue'

function mountStatistics() {
  return mount(Statistics, {
    global: { plugins: [ElementPlus], stubs: childStubs }
  })
}

describe('Statistics', () => {
  it('renders statistics page container', () => {
    const wrapper = mountStatistics()
    expect(wrapper.find('.ops-container').exists()).toBe(true)
  })

  it('renders page title with description', () => {
    const wrapper = mountStatistics()
    expect(wrapper.text()).toContain('统计报表')
    expect(wrapper.text()).toContain('实时数据分析')
  })

  it('renders stats layout with left nav and right content', () => {
    const wrapper = mountStatistics()
    expect(wrapper.find('.stats-left').exists()).toBe(true)
    expect(wrapper.find('.stats-right').exists()).toBe(true)
  })

  it('renders stubbed filter bar', () => {
    const wrapper = mountStatistics()
    expect(wrapper.find('.stub-filter').exists()).toBe(true)
  })

  it('renders without errors', () => {
    expect(() => mountStatistics()).not.toThrow()
  })
})
