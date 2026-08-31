import { systarApi } from '@/api/request'

export function listNotices(params) {
  return systarApi({ url: '/api/sys/notice', method: 'get', params })
}
export function getActiveNotices() {
  return systarApi({ url: '/api/sys/notice/active', method: 'get' })
}
export function getNotice(id) {
  return systarApi({ url: '/api/sys/notice/' + id, method: 'get' })
}
export function addNotice(data) {
  return systarApi({ url: '/api/sys/notice', method: 'post', data })
}
export function updateNotice(id, data) {
  return systarApi({ url: '/api/sys/notice/' + id, method: 'put', data })
}
export function deleteNotice(id) {
  return systarApi({ url: '/api/sys/notice/' + id, method: 'delete' })
}
export function publishNotice(id) {
  return systarApi({ url: '/api/sys/notice/' + id + '/publish', method: 'put' })
}
