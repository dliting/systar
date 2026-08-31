<template>
  <div class="dept-page">
    <el-form inline class="search-bar">
      <el-form-item>
        <el-button type="primary" @click="openAdd()" v-hasPermi="['sys:dept:add']">新增部门</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="deptList" v-loading="loading" border stripe row-key="id"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }" default-expand-all>
      <el-table-column prop="deptName" label="部门名称" />
      <el-table-column prop="leader" label="负责人" />
      <el-table-column prop="phone" label="联系电话" />
      <el-table-column prop="orderNum" label="排序" width="60" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openAdd(row)" v-hasPermi="['sys:dept:add']">新增</el-button>
          <el-button text type="primary" @click="openEdit(row)" v-hasPermi="['sys:dept:edit']">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row)" v-hasPermi="['sys:dept:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="560px" :close-on-click-modal="false">
      <el-form ref="deptFormRef" :model="deptForm" :rules="deptRules" label-width="80px">
        <el-form-item label="上级部门">
          <el-tree-select v-model="deptForm.parentId" :data="deptTreeSelect"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="选择上级部门（留空为顶级）" clearable check-strictly
            style="width:100%" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="deptForm.deptName" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="deptForm.leader" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="deptForm.phone" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.orderNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="deptForm.status" :active-value="0" :inactive-value="1" />
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
import { getDeptTree, getDept, addDept, updateDept, deleteDept } from '@/api/sys/dept'

const deptList = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const currentDeptId = ref(null)
const deptFormRef = ref(null)
const deptForm = reactive({
  parentId: null, deptName: '', leader: '', phone: '', orderNum: 0, status: 0
})
const deptRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}
const dialogTitle = computed(() => editing.value ? '编辑部门' : '新增部门')
const deptTreeSelect = ref([])

async function fetchDepts() {
  loading.value = true
  try {
    const res = await getDeptTree()
    deptList.value = res.data || []
  } catch (e) {
    ElMessage.error('获取部门列表失败: ' + e.message)
  } finally { loading.value = false }
}

function openAdd(parentRow) {
  editing.value = false
  currentDeptId.value = null
  Object.assign(deptForm, {
    parentId: parentRow ? parentRow.id : null,
    deptName: '', leader: '', phone: '', orderNum: 0, status: 0
  })
  deptTreeSelect.value = JSON.parse(JSON.stringify(deptList.value))
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  currentDeptId.value = row.id
  getDept(row.id).then(res => {
    const d = res.data
    Object.assign(deptForm, {
      parentId: d.parentId || null,
      deptName: d.deptName, leader: d.leader || '',
      phone: d.phone || '', orderNum: d.orderNum || 0, status: d.status
    })
    deptTreeSelect.value = JSON.parse(JSON.stringify(deptList.value))
    dialogVisible.value = true
  }).catch(e => ElMessage.error('获取部门信息失败: ' + e.message))
}

async function submitForm() {
  if (!deptFormRef.value) return
  await deptFormRef.value.validate()
  submitting.value = true
  try {
    const data = { ...deptForm }
    if (editing.value) {
      await updateDept(currentDeptId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addDept(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchDepts()
  } catch (e) {
    ElMessage.error((editing.value ? '更新' : '新增') + '失败: ' + e.message)
  } finally { submitting.value = false }
}

function handleDelete(row) {
  const hasChild = deptList.value.some(d => d.parentId === row.id)
  const msg = hasChild ? '该部门下有子部门，确认全部删除？' : `确认删除部门「${row.deptName}」？`
  ElMessageBox.confirm(msg, '提示', { type: 'warning' })
    .then(async () => {
      await deleteDept(row.id)
      ElMessage.success('删除成功')
      fetchDepts()
    }).catch(() => {})
}

onMounted(() => { fetchDepts() })
</script>

<style scoped>
.dept-page { padding: 0; }
.search-bar { background: #fff; padding: 16px; border-radius: 4px; margin-bottom: 16px; }
</style>
