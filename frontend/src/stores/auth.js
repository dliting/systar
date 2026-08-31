import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const username = ref('')
  const permissions = ref([])
  const userId = ref(null)
  let renewInterval = null

  if (token.value) {
    loadUserInfo().then(() => startRenewer()).catch(() => { removeToken(); token.value = '' })
  }

  async function login(user, pwd) {
    const res = await loginApi(user, pwd)
    setToken(res.token)
    token.value = res.token
    await loadUserInfo()
    startRenewer()
  }

  async function loadUserInfo() {
    const info = await getUserInfo()
    username.value = info.user?.userName || info.user?.nickName || '用户'
    permissions.value = info.permissions || []
    userId.value = info.user?.userId
  }

  async function logout() {
    stopRenewer()
    try { await logoutApi() } catch (e) { /* ignore */ }
    removeToken()
    token.value = ''; username.value = ''; permissions.value = []; userId.value = null
  }

  function startRenewer() {
    if (renewInterval) clearInterval(renewInterval)
    renewInterval = setInterval(async () => {
      try { await getUserInfo() } catch (e) { /* ignore */ }
    }, 10 * 60 * 1000)
  }

  function stopRenewer() {
    if (renewInterval) { clearInterval(renewInterval); renewInterval = null }
  }

  function hasPermission(perm) {
    return permissions.value.includes('*') || permissions.value.includes(perm)
  }

  return { token, username, permissions, userId, login, loadUserInfo, logout, hasPermission, startRenewer, stopRenewer }
})
