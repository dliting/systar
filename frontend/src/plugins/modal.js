import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  install(app) {
    const modal = {
      msgSuccess(message) { ElMessage.success(message) },
      msgWarning(message) { ElMessage.warning(message) },
      msgError(message) { ElMessage.error(message) },
      confirm(message) { return ElMessageBox.confirm(message, '系统提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }) }
    }
    app.config.globalProperties.$modal = modal
  }
}
