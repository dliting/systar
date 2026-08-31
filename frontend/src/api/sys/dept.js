import { systarApi } from '@/api/request'

export function getDeptTree() {
  return systarApi({ url: '/api/sys/dept/tree', method: 'get' })
}
export function getDept(id) {
  return systarApi({ url: '/api/sys/dept/' + id, method: 'get' })
}
export function addDept(data) {
  return systarApi({ url: '/api/sys/dept', method: 'post', data })
}
export function updateDept(id, data) {
  return systarApi({ url: '/api/sys/dept/' + id, method: 'put', data })
}
export function deleteDept(id) {
  return systarApi({ url: '/api/sys/dept/' + id, method: 'delete' })
}
