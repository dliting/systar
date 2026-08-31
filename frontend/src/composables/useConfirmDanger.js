import { ref, onBeforeUnmount } from 'vue'

/**
 * Composable for dangerous action confirmation with optional text input verification.
 *
 * Usage:
 *   const { confirm, handleConfirm, handleCancel } = useConfirmDanger()
 *   const ok = await confirm({ title: '删除', message: '确认？', expectedInput: 'test' })
 *   // Wire handleConfirm/handleCancel to <ConfirmDialog> events
 */
export function useConfirmDanger() {
  const dialogVisible    = ref(false)
  const dialogTitle      = ref('')
  const dialogMessage    = ref('')
  const dialogImpact     = ref('')
  const requireInput     = ref(false)
  const expectedInput    = ref('')
  const inputPlaceholder = ref('')

  let resolveFn = null

  function confirm(options = {}) {
    return new Promise((resolve) => {
      // Reject previous pending promise if any (concurrent guard)
      if (resolveFn) { resolveFn(false) }

      dialogTitle.value       = options.title ?? '确认操作'
      dialogMessage.value     = options.message ?? ''
      dialogImpact.value      = options.impact ?? ''
      requireInput.value      = options.requireInput ?? false
      expectedInput.value     = options.expectedInput ?? ''
      inputPlaceholder.value  = options.inputPlaceholder ?? ''
      dialogVisible.value     = true
      resolveFn = resolve
    })
  }

  function handleConfirm() {
    dialogVisible.value = false
    if (resolveFn) { resolveFn(true); resolveFn = null }
  }

  function handleCancel() {
    dialogVisible.value = false
    if (resolveFn) { resolveFn(false); resolveFn = null }
  }

  onBeforeUnmount(() => {
    if (resolveFn) { resolveFn(false); resolveFn = null }
  })

  return {
    dialogVisible,
    dialogTitle,
    dialogMessage,
    dialogImpact,
    requireInput,
    expectedInput,
    inputPlaceholder,
    confirm,
    handleConfirm,
    handleCancel,
  }
}
