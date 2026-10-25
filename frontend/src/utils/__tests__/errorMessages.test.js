import { describe, it, expect } from 'vitest'
import { humanizeNetworkError, humanizeHttpStatus, isLoginRequest } from '../errorMessages'

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
