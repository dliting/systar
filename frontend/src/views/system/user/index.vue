<template>
  <div class="user-page">
    <el-form :model="searchForm" inline class="search-bar">
      <el-form-item label="用户名">
        <el-input v-model="searchForm.username" placeholder="输入用户名" clearable @keyup.enter="handleSearch" style="width:200px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </el-form-item>
      <el-form-item style="float:right">
        <el-button type="primary" @click="openAdd" v-hasPermi="['sys:user:add']">新增用户</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="userList" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)" v-hasPermi="['sys:user:edit']">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row)" v-hasPermi="['sys:user:delete']">删除</el-button>
          <el-button text type="warning" @click="openResetPwd(row)" v-hasPermi="['sys:user:edit']">重置密码</el-button>
          <el-button text type="info" @click="openAssignRoles(row)" v-hasPermi="['sys:user:edit']">分配角色</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page="pageNum" v-model:limit="pageSize"
      :total="total" @pagination="fetchUsers" />

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="560px" :close-on-click-modal="false">
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="editing" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="userForm.status" :active-value="0" :inactive-value="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog title="重置密码" v-model="pwdDialogVisible" width="420px" :close-on-click-modal="false">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>

    <!-- Assign Roles Dialog -->
    <el-dialog title="分配角色" v-model="roleDialogVisible" width="480px" :close-on-click-modal="false">
      <el-checkbox-group v-model="selectedRoleIds">
        <div v-for="r in allRoles" :key="r.id" style="margin-bottom:8px">
          <el-checkbox :label="r.id" :value="r.id">{{ r.roleName }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssignRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, getUser, addUser, updateUser, deleteUser, resetPassword, assignRoles } from '@/api/sys/user'
import { listRoles } from '@/api/sys/role'

const searchForm = reactive({ username: '' })
const userList = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const currentUserId = ref(null)
const userFormRef = ref(null)
const userForm = reactive({
  username: '', password: '', nickname: '', email: '', phone: '', status: 0
})
const userRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const dialogTitle = computed(() => editing.value ? '编辑用户' : '新增用户')

const pwdDialogVisible = ref(false)
const pwdTargetId = ref(null)
const pwdFormRef = ref(null)
const pwdForm = reactive({ password: '' })
const pwdRules = {
  password: [{ required: true, message: '请输入新密码', trigger: 'blur' }]
}

const roleDialogVisible = ref(false)
const roleTargetId = ref(null)
const selectedRoleIds = ref([])
const allRoles = ref([])

async function fetchUsers() {
  loading.value = true
  try {
    const params = { page: pageNum.value, size: pageSize.value }
    if (searchForm.username) params.username = searchForm.username
    const res = await listUsers(params)
    userList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('获取用户列表失败: ' + e.message)
  } finally { loading.value = false }
}

function handleSearch() {
  pageNum.value = 1
  fetchUsers()
}
function resetSearch() {
  searchForm.username = ''
  pageNum.value = 1
  fetchUsers()
}

function openAdd() {
  editing.value = false
  currentUserId.value = null
  Object.assign(userForm, { username: '', password: '', nickname: '', email: '', phone: '', status: 0 })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  currentUserId.value = row.id
  getUser(row.id).then(res => {
    const d = res.data
    Object.assign(userForm, {
      username: d.username, password: '', nickname: d.nickname || '',
      email: d.email || '', phone: d.phone || '', status: d.status
    })
    dialogVisible.value = true
  }).catch(e => ElMessage.error('获取用户信息失败: ' + e.message))
}

async function submitForm() {
  if (!userFormRef.value) return
  await userFormRef.value.validate()
  submitting.value = true
  try {
    const data = { ...userForm }
    if (editing.value) {
      delete data.password
      await updateUser(currentUserId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addUser(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchUsers()
  } catch (e) {
    ElMessage.error((editing.value ? '更新' : '新增') + '失败: ' + e.message)
  } finally { submitting.value = false }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteUser(row.id)
      ElMessage.success('删除成功')
      fetchUsers()
    }).catch(() => {})
}

function openResetPwd(row) {
  pwdTargetId.value = row.id
  pwdForm.password = ''
  pwdDialogVisible.value = true
}
async function submitResetPwd() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate()
  try {
    await resetPassword(pwdTargetId.value, pwdForm.password)
    ElMessage.success('密码重置成功')
    pwdDialogVisible.value = false
  } catch (e) {
    ElMessage.error('重置失败: ' + e.message)
  }
}

async function openAssignRoles(row) {
  roleTargetId.value = row.id
  try {
    const [userRes, rolesRes] = await Promise.all([
      getUser(row.id),
      listRoles({ size: 999 })
    ])
    allRoles.value = rolesRes.data?.records || []
    selectedRoleIds.value = userRes.data?.roleIds || []
    roleDialogVisible.value = true
  } catch (e) {
    ElMessage.error('加载角色信息失败: ' + e.message)
  }
}
async function submitAssignRoles() {
  try {
    await assignRoles(roleTargetId.value, selectedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
  } catch (e) {
    ElMessage.error('角色分配失败: ' + e.message)
  }
}

onMounted(() => { fetchUsers() })
</script>

<style scoped>
.user-page { padding: 0; }
.search-bar { background: #fff; padding: 16px; border-radius: 4px; margin-bottom: 16px; }
</style>
