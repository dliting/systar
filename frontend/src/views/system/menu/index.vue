<template>
  <div class="menu-page">
    <el-form inline class="search-bar">
      <el-form-item>
        <el-button type="primary" @click="openAdd()" v-hasPermi="['sys:menu:add']">新增菜单</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="menuList" v-loading="loading" border stripe row-key="id"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }" default-expand-all>
      <el-table-column prop="menuName" label="菜单名称" />
      <el-table-column prop="menuType" label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.menuType === 'M' ? '' : row.menuType === 'C' ? 'success' : 'warning'">
            {{ row.menuType === 'M' ? '目录' : row.menuType === 'C' ? '菜单' : '按钮' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由/路径" />
      <el-table-column prop="component" label="组件" />
      <el-table-column prop="perms" label="权限标识" />
      <el-table-column prop="orderNum" label="排序" width="60" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openAdd(row)" v-hasPermi="['sys:menu:add']">新增</el-button>
          <el-button text type="primary" @click="openEdit(row)" v-hasPermi="['sys:menu:edit']">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row)" v-hasPermi="['sys:menu:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" :close-on-click-modal="false">
      <el-form ref="menuFormRef" :model="menuForm" :rules="menuRules" label-width="80px">
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select v-model="menuForm.parentId" :data="menuTreeSelect"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            placeholder="选择上级菜单（留空为顶级）" clearable check-strictly
            style="width:100%" />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="menuForm.menuName" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="menuForm.menuType">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">菜单</el-radio>
            <el-radio value="F">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="menuForm.menuType !== 'F'" label="路由">
          <el-input v-model="menuForm.path" />
        </el-form-item>
        <el-form-item v-if="menuForm.menuType === 'C'" label="组件">
          <el-input v-model="menuForm.component" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="menuForm.icon" placeholder="例: system" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="menuForm.perms" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="menuForm.orderNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="menuForm.status" :active-value="0" :inactive-value="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMenuTree, getMenu, addMenu, updateMenu, deleteMenu } from '@/api/sys/menu'

const menuList = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const currentMenuId = ref(null)
const menuFormRef = ref(null)
const menuForm = reactive({
  parentId: null, menuName: '', menuType: 'M',
  path: '', component: '', icon: '', perms: '', orderNum: 0, status: 0
})
const menuRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}
const dialogTitle = computed(() => editing.value ? '编辑菜单' : '新增菜单')
const menuTreeSelect = ref([])

async function fetchMenus() {
  loading.value = true
  try {
    const res = await getMenuTree()
    menuList.value = res.data || []
  } catch (e) {
    ElMessage.error('获取菜单列表失败: ' + e.message)
  } finally { loading.value = false }
}

function openAdd(parentRow) {
  editing.value = false
  currentMenuId.value = null
  Object.assign(menuForm, {
    parentId: parentRow ? parentRow.id : null,
    menuName: '', menuType: 'M',
    path: '', component: '', icon: '', perms: '', orderNum: 0, status: 0
  })
  menuTreeSelect.value = JSON.parse(JSON.stringify(menuList.value))
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  currentMenuId.value = row.id
  getMenu(row.id).then(res => {
    const d = res.data
    Object.assign(menuForm, {
      parentId: d.parentId || null,
      menuName: d.menuName, menuType: d.menuType || 'M',
      path: d.path || '', component: d.component || '',
      icon: d.icon || '', perms: d.perms || '',
      orderNum: d.orderNum || 0, status: d.status
    })
    menuTreeSelect.value = JSON.parse(JSON.stringify(menuList.value))
    dialogVisible.value = true
  }).catch(e => ElMessage.error('获取菜单信息失败: ' + e.message))
}

async function submitForm() {
  if (!menuFormRef.value) return
  await menuFormRef.value.validate()
  submitting.value = true
  try {
    const data = { ...menuForm }
    if (editing.value) {
      await updateMenu(currentMenuId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addMenu(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchMenus()
  } catch (e) {
    ElMessage.error((editing.value ? '更新' : '新增') + '失败: ' + e.message)
  } finally { submitting.value = false }
}

function hasChildren(id) {
  return menuList.value.some(m => m.parentId === id)
}

function handleDelete(row) {
  if (hasChildren(row.id)) {
    ElMessageBox.confirm('该菜单下有子菜单，确认全部删除？', '提示', { type: 'warning' })
      .then(doDelete).catch(() => {})
  } else {
    doDelete()
  }
  async function doDelete() {
    try {
      await deleteMenu(row.id)
      ElMessage.success('删除成功')
      fetchMenus()
    } catch (e) {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

onMounted(() => { fetchMenus() })
</script>

<style scoped>
.menu-page { padding: 0; }
.search-bar { background: #fff; padding: 16px; border-radius: 4px; margin-bottom: 16px; }
</style>
