import { systarApi } from '@/api/request'

export function listRoles(params) {
  return systarApi({ url: '/api/sys/role', method: 'get', params })
}
export function getRole(id) {
  return systarApi({ url: '/api/sys/role/' + id, method: 'get' })
}
export function addRole(data) {
  return systarApi({ url: '/api/sys/role', method: 'post', data })
}
export function updateRole(id, data) {
  return systarApi({ url: '/api/sys/role/' + id, method: 'put', data })
}
export function deleteRole(id) {
  return systarApi({ url: '/api/sys/role/' + id, method: 'delete' })
}
export function assignMenus(id, menuIds) {
  return systarApi({ url: '/api/sys/role/' + id + '/menus', method: 'put', data: { menuIds } })
}
