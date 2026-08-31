import { systarApi } from '@/api/request'

export function login(username, password) {
  return systarApi({
    url: '/api/auth/login', method: 'post',
    data: { username, password }
  }).then(res => res.data)
}

export function getUserInfo() {
  return systarApi({ url: '/api/auth/getInfo', method: 'get' }).then(res => res.data)
}

export function logout() {
  return systarApi({ url: '/api/auth/logout', method: 'post' })
}
