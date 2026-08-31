<template>
  <div class="role-page">
    <el-form inline class="search-bar">
      <el-form-item>
        <el-button type="primary" @click="openAdd" v-hasPermi="['sys:role:add']">新增角色</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="roleList" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="roleName" label="角色名称" />
      <el-table-column prop="roleKey" label="角色标识" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)" v-hasPermi="['sys:role:edit']">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row)" v-hasPermi="['sys:role:delete']">删除</el-button>
          <el-button text type="info" @click="openAssignMenus(row)" v-hasPermi="['sys:role:edit']">分配菜单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page="pageNum" v-model:limit="pageSize"
      :total="total" @pagination="fetchRoles" />

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="560px" :close-on-click-modal="false">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="roleForm.roleKey" :disabled="editing" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="roleForm.status" :active-value="0" :inactive-value="1" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="roleForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- Assign Menus Dialog -->
    <el-dialog title="分配菜单" v-model="menuDialogVisible" width="480px" :close-on-click-modal="false">
      <el-tree ref="menuTreeRef" :data="menuTree" show-checkbox node-key="id"
        :props="{ label: 'menuName', children: 'children' }"
        :default-checked-keys="checkedMenuIds" default-expand-all />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssignMenus">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoles, getRole, addRole, updateRole, deleteRole, assignMenus } from '@/api/sys/role'
import { getMenuTree } from '@/api/sys/menu'

const roleList = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const currentRoleId = ref(null)
const roleFormRef = ref(null)
const roleForm = reactive({ roleName: '', roleKey: '', status: 0, remark: '' })
const roleRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }]
}
const dialogTitle = computed(() => editing.value ? '编辑角色' : '新增角色')

const menuDialogVisible = ref(false)
const menuTargetId = ref(null)
const menuTree = ref([])
const checkedMenuIds = ref([])
const menuTreeRef = ref(null)

async function fetchRoles() {
  loading.value = true
  try {
    const res = await listRoles({ page: pageNum.value, size: pageSize.value })
    roleList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('获取角色列表失败: ' + e.message)
  } finally { loading.value = false }
}

function openAdd() {
  editing.value = false
  currentRoleId.value = null
  Object.assign(roleForm, { roleName: '', roleKey: '', status: 0, remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  currentRoleId.value = row.id
  getRole(row.id).then(res => {
    const d = res.data
    Object.assign(roleForm, {
      roleName: d.roleName, roleKey: d.roleKey,
      status: d.status, remark: d.remark || ''
    })
    dialogVisible.value = true
  }).catch(e => ElMessage.error('获取角色信息失败: ' + e.message))
}

async function submitForm() {
  if (!roleFormRef.value) return
  await roleFormRef.value.validate()
  submitting.value = true
  try {
    const data = { ...roleForm }
    if (editing.value) {
      await updateRole(currentRoleId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addRole(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchRoles()
  } catch (e) {
    ElMessage.error((editing.value ? '更新' : '新增') + '失败: ' + e.message)
  } finally { submitting.value = false }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteRole(row.id)
      ElMessage.success('删除成功')
      fetchRoles()
    }).catch(() => {})
}

async function openAssignMenus(row) {
  menuTargetId.value = row.id
  try {
    const [roleRes, menuRes] = await Promise.all([
      getRole(row.id),
      getMenuTree()
    ])
    menuTree.value = menuRes.data || []
    checkedMenuIds.value = roleRes.data?.menuIds || []
    menuDialogVisible.value = true
  } catch (e) {
    ElMessage.error('加载菜单信息失败: ' + e.message)
  }
}

async function submitAssignMenus() {
  try {
    const keys = menuTreeRef.value?.getCheckedKeys() || []
    const halfKeys = menuTreeRef.value?.getHalfCheckedKeys() || []
    await assignMenus(menuTargetId.value, [...keys, ...halfKeys])
    ElMessage.success('菜单分配成功')
    menuDialogVisible.value = false
  } catch (e) {
    ElMessage.error('菜单分配失败: ' + e.message)
  }
}

onMounted(() => { fetchRoles() })
</script>

<style scoped>
.role-page { padding: 0; }
.search-bar { background: #fff; padding: 16px; border-radius: 4px; margin-bottom: 16px; }
</style>
