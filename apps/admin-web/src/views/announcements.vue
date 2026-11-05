<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { label, text, versionCodeOf, visibleText, when } from '../support/format'
import RichBodyField from '../components/RichBodyField.vue'
import { useRows } from '../support/useRows'

const list = useRows()
const dialog = ref(false)
const editingId = ref('')
const actionError = ref('')
const form = reactive({
  title: '',
  body: '',
  format: 'plain' as 'plain' | 'html',
  clientTypes: '',
  channels: '',
  versionCodeMin: '',
  versionCodeMax: '',
  sort: '0',
  status: 'draft',
})

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/announcements')).data)
}

function openCreate(): void {
  editingId.value = ''
  form.title = ''
  form.body = ''
  form.format = 'plain'
  form.clientTypes = ''
  form.channels = ''
  form.versionCodeMin = ''
  form.versionCodeMax = ''
  form.sort = '0'
  form.status = 'draft'
  actionError.value = ''
  dialog.value = true
}

function openEdit(row: Record<string, unknown>): void {
  editingId.value = text(row.id)
  form.title = text(row.title)
  form.body = text(row.body)
  form.format = text(row.format) === 'html' ? 'html' : 'plain'
  form.clientTypes = text(row.clientTypes)
  form.channels = text(row.channels)
  form.versionCodeMin = text(row.versionCodeMin)
  form.versionCodeMax = text(row.versionCodeMax)
  form.sort = text(row.sort) || '0'
  form.status = text(row.status) || 'draft'
  actionError.value = ''
  dialog.value = true
}

function optionalCode(raw: string): number | null | undefined {
  if (!raw.trim()) return undefined
  return versionCodeOf(raw)
}

async function save(): Promise<void> {
  actionError.value = ''
  if (!form.title.trim() || !visibleText(form.body, form.format)) {
    actionError.value = '请填写标题和正文'
    return
  }
  const versionCodeMin = optionalCode(form.versionCodeMin)
  const versionCodeMax = optionalCode(form.versionCodeMax)
  if (versionCodeMin === null || versionCodeMax === null) {
    actionError.value = '版本号必须是八位日期'
    return
  }
  const body: Record<string, unknown> = {
    title: form.title.trim(),
    body: form.body,
    format: form.format,
    clientTypes: form.clientTypes.trim(),
    channels: form.channels.trim(),
    sort: Number(form.sort) || 0,
    status: form.status,
  }
  if (versionCodeMin !== undefined) body.versionCodeMin = versionCodeMin
  if (versionCodeMax !== undefined) body.versionCodeMax = versionCodeMax
  try {
    if (editingId.value) await apiPatch(`/v1/announcements/${editingId.value}`, body)
    else await apiPost('/v1/announcements', body)
    dialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>公告</h1>
    <div><el-button type="primary" @click="openCreate">新建公告</el-button></div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="clientTypes" label="端" min-width="120" />
      <el-table-column prop="channels" label="渠道" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ when(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editingId ? '编辑公告' : '新建公告'" width="640px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="96px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="正文"><RichBodyField v-model:body="form.body" v-model:format="form.format" /></el-form-item>
        <el-form-item label="端"><el-input v-model="form.clientTypes" placeholder="android,ios,h5" /></el-form-item>
        <el-form-item label="渠道"><el-input v-model="form.channels" /></el-form-item>
        <el-form-item label="最低版本"><el-input v-model="form.versionCodeMin" placeholder="20261029" /></el-form-item>
        <el-form-item label="最高版本"><el-input v-model="form.versionCodeMax" placeholder="20261029" /></el-form-item>
        <el-form-item label="排序"><el-input v-model="form.sort" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="草稿" value="draft" />
            <el-option label="已发布" value="published" />
            <el-option label="已下架" value="retired" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
