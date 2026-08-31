import { describe, it, expect } from 'vitest'
import { useConfirmDanger } from '../useConfirmDanger'

describe('useConfirmDanger', () => {
  it('confirm returns a promise', () => {
    const { confirm } = useConfirmDanger()
    const result = confirm({ title: '删除', message: '确认删除？' })
    expect(result).toBeInstanceOf(Promise)
    // Don't await - promise won't resolve without handleConfirm/handleCancel
  })

  it('exposes reactive dialog state', () => {
    const { dialogVisible, dialogTitle, dialogMessage, requireInput, expectedInput, dialogImpact } = useConfirmDanger()
    expect(dialogVisible.value).toBe(false)
    expect(typeof dialogTitle.value).toBe('string')
    expect(typeof dialogMessage.value).toBe('string')
  })

  it('confirm sets dialog state correctly', async () => {
    const { confirm, dialogVisible, dialogTitle, dialogMessage, expectedInput } = useConfirmDanger()
    const promise = confirm({
      title: '删除确认',
      message: '确认删除「test」？',
      requireInput: true,
      expectedInput: 'test',
    })
    expect(dialogVisible.value).toBe(true)
    expect(dialogTitle.value).toBe('删除确认')
    expect(dialogMessage.value).toBe('确认删除「test」？')
    expect(expectedInput.value).toBe('test')
    // The promise stays pending until user confirms/cancels
    // We can't resolve it in unit test without mocking the dialog
  })

  it('handleConfirm resolves the promise with true', async () => {
    const { confirm, handleConfirm } = useConfirmDanger()
    const promise = confirm({ title: 'test', message: 'test' })
    handleConfirm()
    const result = await promise
    expect(result).toBe(true)
  })

  it('handleCancel resolves the promise with false', async () => {
    const { confirm, handleCancel } = useConfirmDanger()
    const promise = confirm({ title: 'test', message: 'test' })
    handleCancel()
    const result = await promise
    expect(result).toBe(false)
  })
})
