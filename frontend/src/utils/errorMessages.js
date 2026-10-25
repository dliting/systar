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

// Probe/control error banners carry raw driver and exception text
// ("Modbus read failed: Connection refused"). Operators need to know what to
// check, not the stack — map the common failure signatures to plain Chinese
// with actionable guidance; unclassifiable text passes through and the raw
// string stays available as technical detail at the display site.
const RUNTIME_PATTERNS = [
  { re: /connection refused/i, msg: '无法连接采集设备（连接被拒绝），请检查设备是否开机、通信线是否连接正常' },
  { re: /timed?\s*out|timeout/i, msg: '设备响应超时，请检查设备通信是否繁忙或网络是否拥堵' },
  { re: /no route to host|unknown host|unreachable|connect failed/i, msg: '网络不可达，请检查设备 IP 配置与网络连通性' },
  { re: /no response/i, msg: '设备未响应，请检查设备是否在线' },
  { re: /not configured/i, msg: '监测器尚未配置完成，请联系管理员完成配置' },
  { re: /^unknown error$/i, msg: '采集出现未知错误，请稍后重试' },
  // DetectTask falls back to the exception class name when the message is
  // empty (java.net.ConnectException) — meaningless to operators.
  { re: /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*\.[A-Z]\w*$/, msg: '设备通信异常，请检查设备与网络状态' }
]

export function humanizeRuntimeDesc(desc) {
  if (!desc) return ''
  const text = String(desc)
  for (const { re, msg } of RUNTIME_PATTERNS) {
    if (re.test(text)) return msg
  }
  return text
}
