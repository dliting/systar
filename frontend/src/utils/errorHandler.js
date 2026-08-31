/**
 * Unified error handler for IoT API calls.
 *
 * Provides clear, actionable error messages instead of generic failures.
 * Key improvement: distinguishes between Systar backend unreachable,
 * reverse-proxy errors, and business logic errors.
 */
import { ElMessage } from 'element-plus'

const ERROR_SYSTAR_UNREACHABLE = 'Systar IoT 后端（端口 8081）无法连接，请确认服务已启动'
const ERROR_PROXY_TIMEOUT = '请求超时，请检查网络连接与后端服务状态'
const ERROR_UNKNOWN = '请求失败'

/**
 * Detect error type from axios error and show an actionable message.
 * @param {Error} err - The axios error object
 * @param {string} context - What operation was being performed (e.g. '加载资产树失败')
 */
export function showSystarError(err, context = '') {
  const prefix = context ? context + '：' : ''

  if (!err) {
    ElMessage.error(prefix + ERROR_UNKNOWN)
    return
  }

  const msg = err.message || String(err)

  if (msg.includes('Network Error') || msg.includes('Connection refused') || msg.includes('ECONNREFUSED')) {
    console.error(`[IoT] ${context} — Systar backend unreachable:`, msg)
    ElMessage.error({ message: prefix + ERROR_SYSTAR_UNREACHABLE, duration: 8000 })
  } else if (msg.includes('timeout') || msg.includes('Timed out')) {
    console.error(`[IoT] ${context} — Request timeout:`, msg)
    ElMessage.error({ message: prefix + ERROR_PROXY_TIMEOUT, duration: 6000 })
  } else {
    console.error(`[IoT] ${context}:`, msg)
    ElMessage.error(prefix + msg)
  }
}

export function showSystarSuccess(msg) {
  ElMessage.success(msg)
}
