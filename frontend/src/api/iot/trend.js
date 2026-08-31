import { systarApi } from '@/api/request'

const BASE = '/api/ops/trend'

export function getTrendData(params, options = {}) {
  return systarApi({ url: `${BASE}/data`, method: 'get', params, signal: options.signal })
}

export function getTrendDefault(params, options = {}) {
  return systarApi({ url: `${BASE}/default`, method: 'get', params, signal: options.signal })
}

export function getTrendMetadata(params) {
  return systarApi({ url: `${BASE}/metadata`, method: 'get', params })
}
