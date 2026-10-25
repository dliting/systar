import axios from 'axios'
import { getToken, removeToken } from '@/utils/auth'
import { ElMessageBox } from 'element-plus'
import { humanizeNetworkError, humanizeHttpStatus, isLoginRequest } from '@/utils/errorMessages'

let isRelogin = false

function promptRelogin() {
  if (isRelogin) return
  isRelogin = true
  ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
    confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning'
  }).then(() => {
    isRelogin = false; removeToken(); location.href = '/login'
  }).catch(() => { isRelogin = false })
}

const systarApi = axios.create({
  timeout: 30000
})

systarApi.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

systarApi.interceptors.response.use(res => {
  const code = res.data?.code
  if (code === 401) {
    // A wrong password also arrives as body code 401 — show the real reason
    // instead of the misleading "session expired" dialog.
    if (isLoginRequest(res.config?.url)) {
      return Promise.reject(new Error(res.data?.message || '登录失败'))
    }
    promptRelogin()
    return Promise.reject(new Error('登录已过期，请重新登录'))
  }
  if (code !== undefined && code !== 0) {
    const err = new Error(res.data?.message || '请求失败')
    err.code = code
    return Promise.reject(err)
  }
  return Promise.resolve(res.data)
}, error => {
  // Operators never see raw axios/HTTP text — translate centrally so every
  // e.message consumer (login page, banners, toasts) gets plain Chinese.
  const status = error.response?.status
  if (status === 401 && !isLoginRequest(error.config?.url)) promptRelogin()
  error.message = status ? humanizeHttpStatus(status) : humanizeNetworkError(error.message)
  return Promise.reject(error)
})

export { systarApi }
export default systarApi
