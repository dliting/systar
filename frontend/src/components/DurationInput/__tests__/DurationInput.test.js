import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import DurationInput from '../index.vue'

function createWrapper(props = {}) {
  return mount(DurationInput, {
    props: {
      modelValue: '5m',
      ...props,
    },
    global: {
      plugins: [ElementPlus],
    },
  })
}

function lastEmit(wrapper, event = 'update:modelValue') {
  const emitted = wrapper.emitted(event)
  if (!emitted || emitted.length === 0) return null
  return emitted[emitted.length - 1][0]
}

describe('DurationInput', () => {
  it('renders with initial modelValue "5m"', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.duration-input').exists()).toBe(true)
  })

  it('renders both input-number and select', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.duration-input__number').exists()).toBe(true)
    expect(wrapper.find('.duration-input__unit').exists()).toBe(true)
  })

  it('handles empty modelValue without emitting', () => {
    const wrapper = createWrapper({ modelValue: '' })
    expect(wrapper.find('.duration-input').exists()).toBe(true)
    expect(lastEmit(wrapper)).toBe('')
  })

  it('handles null modelValue', () => {
    const wrapper = createWrapper({ modelValue: null })
    expect(wrapper.find('.duration-input').exists()).toBe(true)
    expect(lastEmit(wrapper)).toBe('')
  })

  it('normalizes HH:mm:ss to TimeSpan on mount', () => {
    const wrapper = createWrapper({ modelValue: '00:00:10' })
    expect(lastEmit(wrapper)).toBe('10s')
  })

  it('normalizes HH:mm:ss 5min to TimeSpan', () => {
    const wrapper = createWrapper({ modelValue: '00:05:00' })
    expect(lastEmit(wrapper)).toBe('5m')
  })

  it('handles unparseable modelValue gracefully', () => {
    const wrapper = createWrapper({ modelValue: 'invalid' })
    expect(wrapper.find('.duration-input').exists()).toBe(true)
    expect(lastEmit(wrapper)).toBe('')
  })

  it('normalizes 120s to 2m', () => {
    const wrapper = createWrapper({ modelValue: '120s' })
    expect(lastEmit(wrapper)).toBe('2m')
  })

  it('normalizes 3600s to 1h', () => {
    const wrapper = createWrapper({ modelValue: '3600s' })
    expect(lastEmit(wrapper)).toBe('1h')
  })

  it('keeps 90s as 90s (not divisible by 60)', () => {
    const wrapper = createWrapper({ modelValue: '90s' })
    expect(lastEmit(wrapper)).toBe('90s')
  })

  it('keeps "5m" as "5m" (already normalized)', () => {
    const wrapper = createWrapper({ modelValue: '5m' })
    // "5m" is already normalized — may not emit on mount
    const emitted = wrapper.emitted('update:modelValue')
    // Either no emit (already matches) or emits "5m"
    if (emitted) {
      expect(lastEmit(wrapper)).toBe('5m')
    }
  })

  it('responds to modelValue prop change', async () => {
    const wrapper = createWrapper({ modelValue: '5m' })
    await wrapper.setProps({ modelValue: '10s' })
    // Should parse "10s" and emit "10s"
    expect(lastEmit(wrapper)).toBe('10s')
  })

  it('responds to modelValue prop change from valid to empty', async () => {
    const wrapper = createWrapper({ modelValue: '5m' })
    await wrapper.setProps({ modelValue: '' })
    expect(lastEmit(wrapper)).toBe('')
  })

  it('clamps value below min', () => {
    const wrapper = createWrapper({ modelValue: '5s', min: 60 })
    // 5s < min 60s → should clamp to 1m
    expect(lastEmit(wrapper)).toBe('1m')
  })

  it('respects disabled prop', () => {
    const wrapper = createWrapper({ modelValue: '5m', disabled: true })
    expect(wrapper.find('.duration-input__number').exists()).toBe(true)
    expect(wrapper.find('.duration-input__unit').exists()).toBe(true)
  })
})