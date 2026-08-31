import { onBeforeUnmount, getCurrentInstance } from 'vue'

/**
 * Registers global keyboard shortcuts.
 *
 * The listener is registered immediately so the composable works
 * both inside and outside a Vue component lifecycle.
 *
 * @param {Object} options
 * @param {Function} [options.onEsc] - Called when Escape is pressed
 * @param {Function} [options.onEnterInForm] - Called when Enter is pressed inside .el-form
 * @returns {Function} cleanup function to remove listeners
 */
export function useKeyboard(options = {}) {
  function handleKeydown(e) {
    if (e.key === 'Escape' && options.onEsc) {
      options.onEsc(e)
    }
    if (e.key === 'Enter' && options.onEnterInForm) {
      const inForm = e.target?.closest?.('.el-form')
      if (inForm) {
        options.onEnterInForm(e)
      }
    }
  }

  // Register immediately for both component and non-component usage
  document.addEventListener('keydown', handleKeydown)

  // Auto-cleanup when used inside a component lifecycle
  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      document.removeEventListener('keydown', handleKeydown)
    })
  }

  return function cleanup() {
    document.removeEventListener('keydown', handleKeydown)
  }
}
