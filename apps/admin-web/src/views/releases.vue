<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { label, onOff, text, versionCodeOf, visibleText, when } from '../support/format'
import RichBodyField from '../components/RichBodyField.vue'
import { useRows } from '../support/useRows'

const list = useRows()
const dialog = ref(false)
const editingId = ref('')
const actionError = ref('')
const form = reactive({
  clientType: 'android',
  channel: '',
  versionCode: '',
  versionName: '',
  forceUpdate: false,
  title: '',
  changelog: '',
  format: 'plain' as 'plain' | 'html',
  downloadUrl: '',
  status: 'draft',
})

function channelLocked(): string {
  if (form.clientType === 'ios') return 'appstore'
  if (form.clientType === 'h5') return 'web'
  return form.channel.trim()
}

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/releases')).data)
}

function openCreate(): void {
  editingId.value = ''
  form.clientType = 'android'
  form.channel = ''
  form.versionCode = ''
  form.versionName = ''
  form.forceUpdate = false
  form.title = ''
  form.changelog = ''
  form.format = 'plain'
  form.downloadUrl = ''
  form.status = 'draft'
  actionError.value = ''
  dialog.value = true
}

function openEdit(row: Record<string, unknown>): void {
  editingId.value = text(row.id)
  form.clientType = text(row.clientType) || 'android'
  form.channel = text(row.channel)
  form.versionCode = text(row.versionCode)
  form.versionName = text(row.versionName)
  form.forceUpdate = row.forceUpdate === 1 || row.forceUpdate === true
  form.title = text(row.title)
  form.changelog = text(row.changelog)
  form.format = text(row.format) === 'html' ? 'html' : 'plain'
  form.downloadUrl = text(row.downloadUrl)
  form.status = text(row.status) || 'draft'
  actionError.value = ''
  dialog.value = true
}

async function save(): Promise<void> {
  actionError.value = ''
  const versionCode = versionCodeOf(form.versionCode)
  if (versionCode === null) {
    actionError.value = '版本号必须是八位日期'
    return
  }
  const channel = channelLocked()
  if (!channel) {
    actionError.value = '请填写渠道'
    return
  }
  if (!form.versionName.trim() || !form.title.trim() || !visibleText(form.changelog, form.format)) {
    actionError.value = '请填写版本名、标题和说明'
    return
  }
  const body = {
    clientType: form.clientType,
    channel,
    versionCode,
    versionName: form.versionName.trim(),
    forceUpdate: form.forceUpdate ? 1 : 0,
    title: form.title.trim(),
    changelog: form.changelog,
    format: form.format,
    downloadUrl: form.downloadUrl.trim(),
    status: form.status,
  }
  try {
    if (editingId.value) await apiPatch(`/v1/releases/${editingId.value}`, body)
    else await apiPost('/v1/releases', body)
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
    <h1>版本发布</h1>
    <div><el-button type="primary" @click="openCreate">新建版本</el-button></div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column label="端" width="110">
        <template #default="{ row }">{{ label(row.clientType) }}</template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="120" />
      <el-table-column prop="versionCode" label="版本号" width="120" />
      <el-table-column prop="versionName" label="版本名" width="120" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="强更" width="80">
        <template #default="{ row }">{{ onOff(row.forceUpdate) }}</template>
      </el-table-column>
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
    <el-dialog v-model="dialog" :title="editingId ? '编辑版本' : '新建版本'" width="720px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="88px">
        <el-form-item label="端">
          <el-select v-model="form.clientType">
            <el-option label="Android" value="android" />
            <el-option label="iOS" value="ios" />
            <el-option label="H5" value="h5" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-input v-if="form.clientType === 'android'" v-model="form.channel" placeholder="渠道" />
          <el-input v-else :model-value="form.clientType === 'ios' ? 'appstore' : 'web'" disabled />
        </el-form-item>
        <el-form-item label="版本号"><el-input v-model="form.versionCode" placeholder="20261029" /></el-form-item>
        <el-form-item label="版本名"><el-input v-model="form.versionName" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="说明"><RichBodyField v-model:body="form.changelog" v-model:format="form.format" /></el-form-item>
        <el-form-item label="下载地址"><el-input v-model="form.downloadUrl" /></el-form-item>
        <el-form-item label="强制更新"><el-switch v-model="form.forceUpdate" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="草稿" value="draft" />
            <el-option label="已发布" value="published" />
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
