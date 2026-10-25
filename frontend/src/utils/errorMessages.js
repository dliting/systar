/**
 * Operator-facing translation of transport-level failures.
 *
 * The primary users are operations staff, not developers: raw axios text
 * ("Network Error", "Request failed with status code 500") is meaningless
 * to them. Every network/HTTP failure is mapped here to plain Chinese with
 * actionable guidance; the request interceptor applies it centrally so all
 * e.message consumers (login page, dashboard banner, toasts) inherit it.
 */

export function humanizeNetworkError(message) {
  if (!message) return '请求失败'
  if (message === 'Network Error') return '无法连接服务器，请确认后端服务已启动'
  if (message.includes('timeout')) return '服务器响应超时，请稍后重试'
  return message
}

export function humanizeHttpStatus(status) {
  switch (status) {
    case 400: return '请求参数有误'
    case 401: return '登录已过期，请重新登录'
    case 403: return '没有权限执行此操作'
    case 404: return '请求的功能不存在，请联系管理员'
    case 500: case 502: case 503: case 504:
      return '服务器暂时无法处理请求，请稍后重试'
    default: return `请求失败（错误码 ${status}）`
  }
}

// The login endpoint returns body code 401 for a wrong password — that must
// not be mistaken for an expired session (which triggers the re-login dialog).
export function isLoginRequest(url) {
  return typeof url === 'string' && url.includes('/auth/login')
}
