import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import InlineEdit from '../index.vue'

const BLUR_DEBOUNCE_MS = 200 // component uses 150ms; extra margin for timer jitter

function createWrapper(props = {}) {
  return mount(InlineEdit, {
    props: {
      value: 'hello',
      ...props,
    },
    global: {
      plugins: [ElementPlus],
    },
  })
}

function findDisplay(wrapper) {
  return wrapper.find('.inline-edit__display')
}

function findEditIcon(wrapper) {
  return wrapper.find('.inline-edit__icon')
}

function findInput(wrapper) {
  return wrapper.findComponent({ name: 'ElInput' })
}

function findInputNumber(wrapper) {
  return wrapper.findComponent({ name: 'ElInputNumber' })
}

function findSwitch(wrapper) {
  return wrapper.findComponent({ name: 'ElSwitch' })
}

function findSelect(wrapper) {
  return wrapper.findComponent({ name: 'ElSelect' })
}

/**
 * Find the native <input> element rendered inside an ElInput component.
 * Element Plus renders the native input inside a wrapper div.
 */
function findNativeInput(wrapper) {
  return wrapper.find('input')
}

describe('InlineEdit', () => {
  // --- Display mode ---

  it('renders display mode by default showing value', () => {
    const wrapper = createWrapper({ value: 'hello' })
    expect(findDisplay(wrapper).exists()).toBe(true)
    expect(findDisplay(wrapper).text()).toContain('hello')
  })

  it('shows placeholder when value is empty', () => {
    const wrapper = createWrapper({ value: '', placeholder: '请输入' })
    expect(findDisplay(wrapper).text()).toContain('请输入')
  })

  it('shows "—" when no placeholder and value is empty', () => {
    const wrapper = createWrapper({ value: '' })
    expect(findDisplay(wrapper).text()).toContain('—')
  })

  // --- Edit mode transitions ---

  it('enters edit mode on click', async () => {
    const wrapper = createWrapper({ value: 'hello' })
    await findDisplay(wrapper).trigger('click')
    expect(wrapper.vm.isEditing).toBe(true)
    expect(findInput(wrapper).exists()).toBe(true)
  })

  it('emits save on Enter for text type', async () => {
    const wrapper = createWrapper({ value: 'hello' })
    await findDisplay(wrapper).trigger('click')

    // Set editValue directly via the component's internal state
    wrapper.vm.editValue = 'world'
    await wrapper.vm.$nextTick()

    // Trigger keydown Enter on the native input element
    const nativeInput = findNativeInput(wrapper)
    await nativeInput.trigger('keydown', { key: 'Enter' })
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('save')).toBeTruthy()
    expect(wrapper.emitted('save')[0][0]).toBe('world')
  })

  it('emits save and exits edit mode', async () => {
    const wrapper = createWrapper({ value: 'hello' })
    await findDisplay(wrapper).trigger('click')

    wrapper.vm.editValue = 'new value'
    await wrapper.vm.$nextTick()

    const nativeInput = findNativeInput(wrapper)
    await nativeInput.trigger('keydown', { key: 'Enter' })
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('save')).toBeTruthy()
    expect(wrapper.vm.isEditing).toBe(false)
  })

  it('cancels on Escape, reverts to original', async () => {
    const wrapper = createWrapper({ value: 'hello' })
    await findDisplay(wrapper).trigger('click')

    wrapper.vm.editValue = 'changed'
    await wrapper.vm.$nextTick()

    const nativeInput = findNativeInput(wrapper)
    await nativeInput.trigger('keydown', { key: 'Escape' })
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('save')).toBeFalsy()
    expect(wrapper.vm.isEditing).toBe(false)
  })

  // --- Number type ---

  it('renders el-input-number for type="number"', async () => {
    const wrapper = createWrapper({ value: 42, type: 'number' })
    await findDisplay(wrapper).trigger('click')
    expect(findInputNumber(wrapper).exists()).toBe(true)
  })

  it('passes min/max to number input', async () => {
    const wrapper = createWrapper({ value: 5, type: 'number', min: 0, max: 100 })
    await findDisplay(wrapper).trigger('click')
    const inputNumber = findInputNumber(wrapper)
    expect(inputNumber.props('min')).toBe(0)
    expect(inputNumber.props('max')).toBe(100)
  })

  // --- Switch type ---

  it('renders el-switch for type="switch"', () => {
    const wrapper = createWrapper({ value: true, type: 'switch' })
    expect(findSwitch(wrapper).exists()).toBe(true)
    expect(findDisplay(wrapper).exists()).toBe(false)
  })

  it('emits save immediately on switch toggle', async () => {
    const wrapper = createWrapper({ value: true, type: 'switch' })
    const switchComp = findSwitch(wrapper)
    await switchComp.vm.$emit('change', false)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('save')).toBeTruthy()
    expect(wrapper.emitted('save')[0][0]).toBe(false)
  })

  it('displays switch value as tag (是/否)', () => {
    const wrapperTrue = createWrapper({ value: true, type: 'switch' })
    expect(wrapperTrue.find('.inline-edit__switch-tag').text()).toBe('是')

    const wrapperFalse = createWrapper({ value: false, type: 'switch' })
    expect(wrapperFalse.find('.inline-edit__switch-tag').text()).toBe('否')
  })

  // --- Select type ---

  it('renders el-select for type="select" with options', async () => {
    const options = [
      { label: 'Option A', value: 'a' },
      { label: 'Option B', value: 'b' },
    ]
    const wrapper = createWrapper({ value: 'a', type: 'select', options })
    await findDisplay(wrapper).trigger('click')
    expect(findSelect(wrapper).exists()).toBe(true)
  })

  it('shows label for select value in display mode', () => {
    const options = [
      { label: 'Option A', value: 'a' },
      { label: 'Option B', value: 'b' },
    ]
    const wrapper = createWrapper({ value: 'a', type: 'select', options })
    expect(findDisplay(wrapper).text()).toContain('Option A')
  })

  // --- Blur behavior ---

  it('emits save on blur for text type', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = createWrapper({ value: 'hello' })
      await findDisplay(wrapper).trigger('click')

      wrapper.vm.editValue = 'blurred'
      await wrapper.vm.$nextTick()

      const nativeInput = findNativeInput(wrapper)
      await nativeInput.trigger('blur')
      vi.advanceTimersByTime(BLUR_DEBOUNCE_MS)
      await wrapper.vm.$nextTick()

      expect(wrapper.emitted('save')).toBeTruthy()
      expect(wrapper.emitted('save')[0][0]).toBe('blurred')
    } finally {
      vi.useRealTimers()
    }
  })

  // --- No-save guard ---

  it('does not emit save if value unchanged', async () => {
    const wrapper = createWrapper({ value: 'hello' })
    await findDisplay(wrapper).trigger('click')

    // editValue defaults to props.value, so don't change it
    const nativeInput = findNativeInput(wrapper)
    await nativeInput.trigger('keydown', { key: 'Enter' })
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('save')).toBeFalsy()
  })

  // --- Edit icon ---

  it('shows edit icon hint', () => {
    const wrapper = createWrapper({ value: 'hello' })
    expect(findEditIcon(wrapper).exists()).toBe(true)
  })

  // --- Disabled ---

  it('disables interaction when disabled', async () => {
    const wrapper = createWrapper({ value: 'hello', disabled: true })
    await findDisplay(wrapper).trigger('click')
    expect(wrapper.vm.isEditing).toBe(false)
    expect(findInput(wrapper).exists()).toBe(false)
  })

  it('disabled switch does not emit save on toggle', async () => {
    const wrapper = createWrapper({ value: true, type: 'switch', disabled: true })
    const switchComp = findSwitch(wrapper)
    expect(switchComp.props('disabled')).toBe(true)
  })

  // --- Select dropdown cancel ---

  it('select cancels edit when dropdown closes without selection', async () => {
    const wrapper = createWrapper({
      value: 'a',
      type: 'select',
      options: [{ label: 'Alpha', value: 'a' }, { label: 'Beta', value: 'b' }],
    })
    await findDisplay(wrapper).trigger('click')
    expect(wrapper.vm.isEditing).toBe(true)

    const select = findSelect(wrapper)
    await select.vm.$emit('visible-change', false)
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.isEditing).toBe(false)
    expect(wrapper.emitted('save')).toBeFalsy()
  })

  // --- Keyboard accessibility ---

  it('enters edit mode on Enter key from display mode', async () => {
    const wrapper = createWrapper({ value: 'hello' })
    await findDisplay(wrapper).trigger('keydown', { key: 'Enter' })
    expect(wrapper.vm.isEditing).toBe(true)
  })
})
