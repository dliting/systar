import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Skeleton from '../index.vue'

describe('Skeleton', () => {
  it('renders table variant with correct rows and columns', () => {
    const wrapper = mount(Skeleton, { props: { variant: 'table', rows: 3, columns: 4 } })
    const rows = wrapper.findAll('.skeleton-table__row')
    expect(rows).toHaveLength(3)
    expect(rows[0].findAll('.skeleton-table__cell')).toHaveLength(4)
  })

  it('renders card variant', () => {
    const wrapper = mount(Skeleton, { props: { variant: 'card' } })
    expect(wrapper.find('.skeleton-card').exists()).toBe(true)
  })

  it('renders chart variant', () => {
    const wrapper = mount(Skeleton, { props: { variant: 'chart' } })
    expect(wrapper.find('.skeleton-chart').exists()).toBe(true)
  })

  it('defaults to table variant with 5 rows', () => {
    const wrapper = mount(Skeleton)
    expect(wrapper.findAll('.skeleton-table__row')).toHaveLength(5)
  })

  it('applies animated class when animated prop is true', () => {
    const wrapper = mount(Skeleton, { props: { variant: 'table', animated: true } })
    expect(wrapper.find('.skeleton--animated').exists()).toBe(true)
  })

  it('does not apply animated class by default', () => {
    const wrapper = mount(Skeleton)
    expect(wrapper.find('.skeleton--animated').exists()).toBe(false)
  })
})
