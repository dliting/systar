<template>
  <div class="login-wrapper">
    <div class="login-card">
      <h2 class="login-title">Systar 智能监控运维系统</h2>
      <el-form ref="formRef" :model="loginForm" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password"
            placeholder="密码" prefix-icon="Lock" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

if (auth.token) { router.replace('/dashboard') }

const loginForm = reactive({ username: 'admin', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper { display: flex; justify-content: center; align-items: center;
  height: 100vh; background: linear-gradient(135deg, #1a365d 0%, #2d3748 100%); }
.login-card { width: 400px; padding: 40px; background: #fff;
  border-radius: 8px; box-shadow: 0 8px 32px rgba(0,0,0,0.2); }
.login-title { text-align: center; margin-bottom: 32px; font-size: 20px; color: #1a365d; }
.login-btn { width: 100%; }
</style>
