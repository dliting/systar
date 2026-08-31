import { systarApi } from '@/api/request'

export function listUsers(params) {
  return systarApi({ url: '/api/sys/user', method: 'get', params })
}
export function getUser(id) {
  return systarApi({ url: '/api/sys/user/' + id, method: 'get' })
}
export function addUser(data) {
  return systarApi({ url: '/api/sys/user', method: 'post', data })
}
export function updateUser(id, data) {
  return systarApi({ url: '/api/sys/user/' + id, method: 'put', data })
}
export function deleteUser(id) {
  return systarApi({ url: '/api/sys/user/' + id, method: 'delete' })
}
export function resetPassword(id, password) {
  return systarApi({ url: '/api/sys/user/' + id + '/password', method: 'put', data: { password } })
}
export function assignRoles(id, roleIds) {
  return systarApi({ url: '/api/sys/user/' + id + '/roles', method: 'put', data: { roleIds } })
}
