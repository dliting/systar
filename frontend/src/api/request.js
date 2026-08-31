import axios from 'axios'
import { getToken, removeToken } from '@/utils/auth'
import { ElMessageBox } from 'element-plus'

let isRelogin = false

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
    if (!isRelogin) {
      isRelogin = true
      ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
        confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning'
      }).then(() => {
        isRelogin = false; removeToken(); location.href = '/login'
      }).catch(() => { isRelogin = false })
    }
    return Promise.reject(new Error('会话已过期'))
  }
  if (code !== undefined && code !== 0) {
    const err = new Error(res.data?.message || '请求失败')
    err.code = code
    return Promise.reject(err)
  }
  return Promise.resolve(res.data)
}, error => {
  let message = error.message
  if (message === 'Network Error') message = '后端服务连接异常'
  else if (message.includes('timeout')) message = '请求超时'
  error.message = message
  return Promise.reject(error)
})

export { systarApi }
export default systarApi
