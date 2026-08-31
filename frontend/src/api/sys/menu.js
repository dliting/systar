import { systarApi } from '@/api/request'

export function getMenuTree() {
  return systarApi({ url: '/api/sys/menu/tree', method: 'get' })
}
export function getMenu(id) {
  return systarApi({ url: '/api/sys/menu/' + id, method: 'get' })
}
export function addMenu(data) {
  return systarApi({ url: '/api/sys/menu', method: 'post', data })
}
export function updateMenu(id, data) {
  return systarApi({ url: '/api/sys/menu/' + id, method: 'put', data })
}
export function deleteMenu(id) {
  return systarApi({ url: '/api/sys/menu/' + id, method: 'delete' })
}
