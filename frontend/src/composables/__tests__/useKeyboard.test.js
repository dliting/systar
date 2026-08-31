import { describe, it, expect, afterEach, vi } from 'vitest'
import { useKeyboard } from '@/composables/useKeyboard'

describe('useKeyboard', () => {
  let cleanup

  afterEach(() => {
    if (cleanup) {
      cleanup()
      cleanup = null
    }
  })

  it('calls onEsc when Escape is pressed', () => {
    const onEsc = vi.fn()
    cleanup = useKeyboard({ onEsc })

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(onEsc).toHaveBeenCalledTimes(1)
  })

  it('calls onEnterInForm when Enter is pressed inside .el-form', () => {
    const onEnterInForm = vi.fn()
    cleanup = useKeyboard({ onEnterInForm })

    const form = document.createElement('form')
    form.classList.add('el-form')
    document.body.appendChild(form)

    form.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))

    expect(onEnterInForm).toHaveBeenCalledTimes(1)

    document.body.removeChild(form)
  })

  it('does not call onEnterInForm when Enter is pressed outside form', () => {
    const onEnterInForm = vi.fn()
    cleanup = useKeyboard({ onEnterInForm })

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))

    expect(onEnterInForm).not.toHaveBeenCalled()
  })

  it('cleanup removes listeners', () => {
    const onEsc = vi.fn()
    cleanup = useKeyboard({ onEsc })

    // Call cleanup immediately
    cleanup()
    cleanup = null

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(onEsc).not.toHaveBeenCalled()
  })

  it('works with no options', () => {
    // Should not throw when called with empty options
    cleanup = useKeyboard()

    // Dispatching events should not throw
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
  })

  it('does not call onEsc for non-Escape keys', () => {
    const onEsc = vi.fn()
    cleanup = useKeyboard({ onEsc })

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }))

    expect(onEsc).not.toHaveBeenCalled()
  })

  it('does not call onEnterInForm when onEnterInForm is not provided', () => {
    const onEsc = vi.fn()
    cleanup = useKeyboard({ onEsc })

    const form = document.createElement('form')
    form.classList.add('el-form')
    document.body.appendChild(form)

    form.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))

    expect(onEsc).not.toHaveBeenCalled()

    document.body.removeChild(form)
  })

  it('calls onEnterInForm for nested elements inside .el-form', () => {
    const onEnterInForm = vi.fn()
    cleanup = useKeyboard({ onEnterInForm })

    const form = document.createElement('form')
    form.classList.add('el-form')
    const input = document.createElement('input')
    form.appendChild(input)
    document.body.appendChild(form)

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))

    expect(onEnterInForm).toHaveBeenCalledTimes(1)

    document.body.removeChild(form)
  })

  it('handles multiple useKeyboard instances independently', () => {
    const onEsc1 = vi.fn()
    const onEsc2 = vi.fn()
    const cleanup1 = useKeyboard({ onEsc: onEsc1 })
    const cleanup2 = useKeyboard({ onEsc: onEsc2 })

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(onEsc1).toHaveBeenCalledTimes(1)
    expect(onEsc2).toHaveBeenCalledTimes(1)

    // Clean up first instance only
    cleanup1()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(onEsc1).toHaveBeenCalledTimes(1) // no additional call
    expect(onEsc2).toHaveBeenCalledTimes(2) // still active

    cleanup2()
  })
})
