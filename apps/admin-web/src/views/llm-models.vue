<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { onOff, when } from '../support/format'
import { useRows } from '../support/useRows'

const list = useRows()
const dialog = ref(false)
const editingId = ref('')
const actionError = ref('')
const form = reactive({
  name: '',
  baseUrl: '',
  modelName: '',
  apiKey: '',
  weight: 0,
  enabled: false,
  timeoutMs: 60000,
})

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/llm-models')).data)
}

function openCreate(): void {
  editingId.value = ''
  form.name = ''
  form.baseUrl = ''
  form.modelName = ''
  form.apiKey = ''
  form.weight = 0
  form.enabled = false
  form.timeoutMs = 60000
  actionError.value = ''
  dialog.value = true
}

function openEdit(row: Record<string, unknown>): void {
  editingId.value = String(row.id ?? '')
  form.name = String(row.name ?? '')
  form.baseUrl = String(row.baseUrl ?? '')
  form.modelName = String(row.modelName ?? '')
  form.apiKey = ''
  form.weight = Number(row.weight ?? 0)
  form.enabled = row.enabled === 1 || row.enabled === true || row.enabled === '1'
  form.timeoutMs = Number(row.timeoutMs ?? 60000)
  actionError.value = ''
  dialog.value = true
}

function sharedBody(): Record<string, unknown> | null {
  if (!form.name.trim() || !form.baseUrl.trim() || !form.modelName.trim()) {
    actionError.value = '请填写名称、地址和模型名'
    return null
  }
  if (form.weight < 0) {
    actionError.value = '权重不能为负'
    return null
  }
  if (form.timeoutMs < 1000 || form.timeoutMs > 120000) {
    actionError.value = '超时须在 1000 到 120000 毫秒'
    return null
  }
  return {
    name: form.name.trim(),
    baseUrl: form.baseUrl.trim(),
    modelName: form.modelName.trim(),
    weight: form.weight,
    enabled: form.enabled ? 1 : 0,
    timeoutMs: form.timeoutMs,
  }
}

async function save(): Promise<void> {
  actionError.value = ''
  const body = sharedBody()
  if (!body) return
  const apiKey = form.apiKey.trim()
  try {
    if (editingId.value) {
      if (apiKey) body.apiKey = apiKey
      await apiPatch(`/v1/llm-models/${editingId.value}`, body)
    } else {
      if (!apiKey) {
        actionError.value = '请填写密钥'
        return
      }
      body.apiKey = apiKey
      await apiPost('/v1/llm-models', body)
    }
    dialog.value = false
    form.apiKey = ''
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
    <h1>模型分流</h1>
    <div><el-button type="primary" @click="openCreate">新建模型</el-button></div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="baseUrl" label="地址" min-width="200" />
      <el-table-column prop="modelName" label="模型名" min-width="140" />
      <el-table-column prop="weight" label="权重" width="80" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">{{ onOff(row.enabled) }}</template>
      </el-table-column>
      <el-table-column prop="timeoutMs" label="超时" width="100" />
      <el-table-column label="已设置密钥" width="110">
        <template #default="{ row }">{{ row.apiKeySet ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="apiKeyMask" label="密钥掩码" width="120" />
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ when(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editingId ? '编辑模型' : '新建模型'" width="520px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="88px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.baseUrl" /></el-form-item>
        <el-form-item label="模型名"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="权重"><el-input-number v-model="form.weight" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="超时"><el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" /></el-form-item>
        <el-form-item label="密钥">
          <el-input v-model="form.apiKey" type="password" autocomplete="off" :placeholder="editingId ? '留空则不修改' : '必填'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
