import { watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

export default {
  mounted(el, binding) {
    const { value } = binding
    if (!value || !(value instanceof Array) || value.length === 0) return

    el.style.display = 'none'

    const stopWatch = watch(
      () => useAuthStore().permissions,
      (perms) => {
        if (perms.includes('*') || value.some(p => perms.includes(p))) {
          el.style.display = ''
        } else {
          el.style.display = 'none'
        }
      },
      { immediate: true }
    )

    el._hasPermiStop = stopWatch
  },
  unmounted(el) {
    if (el._hasPermiStop) {
      el._hasPermiStop()
      el._hasPermiStop = null
    }
  }
}
