import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import ConfirmDialog from '../index.vue'

let wrapper = null

afterEach(() => {
  if (wrapper) {
    wrapper.unmount()
    wrapper = null
  }
  // Clean up document.body after each test (el-dialog teleports content)
  document.body.innerHTML = ''
})

function createWrapper(props = {}) {
  wrapper = mount(ConfirmDialog, {
    props: {
      visible: true,
      title: '确认删除',
      ...props,
    },
    global: {
      plugins: [ElementPlus],
    },
    attachTo: document.body,
  })
  return wrapper
}

/**
 * Element Plus el-dialog renders content into document.body via teleport.
 * After nextTick, content becomes queryable on document.body.
 */
async function waitForDialogRender() {
  await nextTick()
  await nextTick()
}

function getDialogBody() {
  return document.querySelector('.el-dialog__body')
}

function getDialogFooter() {
  return document.querySelector('.el-dialog__footer')
}

describe('ConfirmDialog', () => {
  it('renders title and message', async () => {
    createWrapper({ message: '确认删除「test」吗？' })
    await waitForDialogRender()
    const titleText = document.querySelector('.el-dialog__title')?.textContent ?? ''
    const bodyText = getDialogBody()?.textContent ?? ''
    expect(titleText).toContain('确认删除')
    expect(bodyText).toContain('确认删除「test」吗？')
  })

  it('shows input field when requireInput is true', async () => {
    createWrapper({ requireInput: true, inputPlaceholder: '请输入资产名前4字' })
    await waitForDialogRender()
    const input = getDialogBody()?.querySelector('input')
    expect(input).toBeTruthy()
  })

  it('disables confirm button when input does not match', async () => {
    createWrapper({ requireInput: true, expectedInput: '测试' })
    await waitForDialogRender()
    const btn = getDialogFooter()?.querySelector('.el-button--danger')
    expect(btn?.classList.contains('is-disabled')).toBe(true)
  })

  it('enables confirm button when input matches', async () => {
    createWrapper({ requireInput: true, expectedInput: '测试' })
    await waitForDialogRender()
    const input = getDialogBody()?.querySelector('input')
    input.value = '测试'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    const btn = getDialogFooter()?.querySelector('.el-button--danger')
    expect(btn?.classList.contains('is-disabled')).toBe(false)
  })

  it('emits confirm when confirm button clicked and input matches', async () => {
    const w = createWrapper({ requireInput: true, expectedInput: '测试' })
    await waitForDialogRender()
    const input = getDialogBody()?.querySelector('input')
    input.value = '测试'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    const btn = getDialogFooter()?.querySelector('.el-button--danger')
    btn.click()
    expect(w.emitted('confirm')).toBeTruthy()
  })

  it('emits cancel when cancel button clicked', async () => {
    const w = createWrapper()
    await waitForDialogRender()
    const btn = getDialogFooter()?.querySelector('.el-button:not(.el-button--danger)')
    btn.click()
    expect(w.emitted('cancel')).toBeTruthy()
  })

  it('shows impact text when provided', async () => {
    createWrapper({ impact: '将删除 3 条关联规则' })
    await waitForDialogRender()
    const bodyText = getDialogBody()?.textContent ?? ''
    expect(bodyText).toContain('将删除 3 条关联规则')
  })

  it('works without requireInput (simple confirm)', async () => {
    const w = createWrapper({ requireInput: false })
    await waitForDialogRender()
    const btn = getDialogFooter()?.querySelector('.el-button--danger')
    expect(btn?.classList.contains('is-disabled')).toBe(false)
    btn.click()
    expect(w.emitted('confirm')).toBeTruthy()
  })
})
