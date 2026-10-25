import { describe, it, expect } from 'vitest'
import { humanizeNetworkError, humanizeHttpStatus, isLoginRequest, humanizeRuntimeDesc } from '../errorMessages'

// Operators must never see raw axios/HTTP text ("Network Error",
// "Request failed with status code 500") — every transport-level failure
// gets plain Chinese with actionable guidance.
describe('humanizeNetworkError', () => {
  it('turns a dead backend into an actionable hint', () => {
    expect(humanizeNetworkError('Network Error')).toBe('无法连接服务器，请确认后端服务已启动')
  })

  it('turns a timeout into a retry hint', () => {
    expect(humanizeNetworkError('timeout of 30000ms exceeded')).toBe('服务器响应超时，请稍后重试')
  })

  it('passes through an already-friendly message', () => {
    expect(humanizeNetworkError('服务器暂时无法处理请求')).toBe('服务器暂时无法处理请求')
  })

  it('falls back to a generic failure for empty input', () => {
    expect(humanizeNetworkError('')).toBe('请求失败')
    expect(humanizeNetworkError(undefined)).toBe('请求失败')
  })
})

describe('humanizeHttpStatus', () => {
  it('explains an expired session', () => {
    expect(humanizeHttpStatus(401)).toBe('登录已过期，请重新登录')
  })

  it('explains a permission denial', () => {
    expect(humanizeHttpStatus(403)).toBe('没有权限执行此操作')
  })

  it('explains a missing endpoint', () => {
    expect(humanizeHttpStatus(404)).toBe('请求的功能不存在，请联系管理员')
  })

  it('groups server-side failures', () => {
    for (const s of [500, 502, 503, 504]) {
      expect(humanizeHttpStatus(s)).toBe('服务器暂时无法处理请求，请稍后重试')
    }
  })

  it('names the code for unclassified statuses', () => {
    expect(humanizeHttpStatus(418)).toBe('请求失败（错误码 418）')
  })
})

// Probe error banners showed raw driver/exception text ("Modbus read failed:
// Connection refused") — operators need what to check, not the stack.
describe('humanizeRuntimeDesc', () => {
  it('explains a refused connection', () => {
    expect(humanizeRuntimeDesc('Modbus read failed: Connection refused'))
      .toBe('无法连接采集设备（连接被拒绝），请检查设备是否开机、通信线是否连接正常')
  })

  it('explains a device timeout', () => {
    expect(humanizeRuntimeDesc('OPC UA read failed: Read timed out'))
      .toBe('设备响应超时，请检查设备通信是否繁忙或网络是否拥堵')
  })

  it('explains an unreachable host', () => {
    expect(humanizeRuntimeDesc('Connection failed: no route to host'))
      .toBe('网络不可达，请检查设备 IP 配置与网络连通性')
  })

  it('explains a silent device', () => {
    expect(humanizeRuntimeDesc('No response for SNMP OID 1.3.6.1.2.1.1.1.0'))
      .toBe('设备未响应，请检查设备是否在线')
  })

  it('explains an incomplete configuration', () => {
    expect(humanizeRuntimeDesc('Modbus probe source is not configured'))
      .toBe('监测器尚未配置完成，请联系管理员完成配置')
  })

  it('explains the unknown-error placeholder', () => {
    expect(humanizeRuntimeDesc('unknown error')).toBe('采集出现未知错误，请稍后重试')
  })

  it('explains a bare Java class-name fallback', () => {
    expect(humanizeRuntimeDesc('java.net.ConnectException'))
      .toBe('设备通信异常，请检查设备与网络状态')
  })

  it('passes through text it cannot classify', () => {
    expect(humanizeRuntimeDesc('自定义规则表达式执行失败')).toBe('自定义规则表达式执行失败')
  })

  it('returns empty input unchanged', () => {
    expect(humanizeRuntimeDesc('')).toBe('')
    expect(humanizeRuntimeDesc(null)).toBe('')
  })
})

describe('isLoginRequest', () => {
  it('recognizes the login endpoint', () => {
    expect(isLoginRequest('/api/auth/login')).toBe(true)
  })

  it('rejects other endpoints', () => {
    expect(isLoginRequest('/api/monitor/assets/1')).toBe(false)
  })

  it('rejects empty input', () => {
    expect(isLoginRequest(null)).toBe(false)
    expect(isLoginRequest(undefined)).toBe(false)
  })
})
