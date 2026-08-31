<template>
  <div class="notice-page">
    <el-form inline class="search-bar">
      <el-form-item>
        <el-button type="primary" @click="openAdd" v-hasPermi="['sys:notice:add']">新增公告</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="noticeList" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.type === 2 ? 'warning' : 'info'">
            {{ row.type === 2 ? '公告' : '通知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 0 ? '' : 'success'">
            {{ row.status === 0 ? '草稿' : '已发布' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="100" />
      <el-table-column prop="publishTime" label="发布时间" width="160" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)" v-hasPermi="['sys:notice:edit']">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row)" v-hasPermi="['sys:notice:delete']">删除</el-button>
          <el-button v-if="row.status === 0" text type="warning" @click="handlePublish(row)" v-hasPermi="['sys:notice:edit']">发布</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page="pageNum" v-model:limit="pageSize"
      :total="total" @pagination="fetchNotices" />

    <!-- Add/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" :close-on-click-modal="false">
      <el-form ref="noticeFormRef" :model="noticeForm" :rules="noticeRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="noticeForm.title" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="noticeForm.type">
            <el-radio :label="1">通知</el-radio>
            <el-radio :label="2">公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="noticeForm.content" type="textarea" :rows="6" />
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
import { listNotices, getNotice, addNotice, updateNotice, deleteNotice, publishNotice } from '@/api/sys/notice'

const noticeList = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const currentNoticeId = ref(null)
const noticeFormRef = ref(null)
const noticeForm = reactive({ title: '', type: 1, content: '' })
const noticeRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}
const dialogTitle = computed(() => editing.value ? '编辑公告' : '新增公告')

async function fetchNotices() {
  loading.value = true
  try {
    const res = await listNotices({ page: pageNum.value, size: pageSize.value })
    noticeList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('获取公告列表失败: ' + e.message)
  } finally { loading.value = false }
}

function openAdd() {
  editing.value = false
  currentNoticeId.value = null
  Object.assign(noticeForm, { title: '', type: 1, content: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  currentNoticeId.value = row.id
  getNotice(row.id).then(res => {
    const d = res.data
    Object.assign(noticeForm, {
      title: d.title, type: d.type, content: d.content || ''
    })
    dialogVisible.value = true
  }).catch(e => ElMessage.error('获取公告信息失败: ' + e.message))
}

async function submitForm() {
  if (!noticeFormRef.value) return
  await noticeFormRef.value.validate()
  submitting.value = true
  try {
    const data = { ...noticeForm }
    if (editing.value) {
      await updateNotice(currentNoticeId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addNotice(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchNotices()
  } catch (e) {
    ElMessage.error((editing.value ? '更新' : '新增') + '失败: ' + e.message)
  } finally { submitting.value = false }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteNotice(row.id)
      ElMessage.success('删除成功')
      fetchNotices()
    }).catch(() => {})
}

function handlePublish(row) {
  ElMessageBox.confirm(`确认发布公告「${row.title}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await publishNotice(row.id)
      ElMessage.success('发布成功')
      fetchNotices()
    }).catch(() => {})
}

onMounted(() => { fetchNotices() })
</script>

<style scoped>
.notice-page { padding: 0; }
.search-bar { background: #fff; padding: 16px; border-radius: 4px; margin-bottom: 16px; }
</style>
