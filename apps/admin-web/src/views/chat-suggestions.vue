<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { label, when } from '../support/format'
import { useRows } from '../support/useRows'

const list = useRows()
const actionError = ref('')
const dialog = ref(false)
const editingId = ref('')
const form = reactive({ text: '', sort: 0, status: 'active' })

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/chat-suggestions')).data)
}

function openCreate(): void {
  editingId.value = ''
  form.text = ''
  form.sort = 0
  form.status = 'active'
  actionError.value = ''
  dialog.value = true
}

function openEdit(row: Record<string, unknown>): void {
  editingId.value = String(row.id ?? '')
  form.text = String(row.text ?? '')
  form.sort = Number(row.sort ?? 0)
  form.status = String(row.status ?? 'active')
  actionError.value = ''
  dialog.value = true
}

async function save(): Promise<void> {
  actionError.value = ''
  const text = form.text.trim()
  if (!text) {
    actionError.value = '请填写文案'
    return
  }
  const body = { text, sort: form.sort, status: form.status }
  try {
    if (editingId.value) await apiPatch(`/v1/chat-suggestions/${editingId.value}`, body)
    else await apiPost('/v1/chat-suggestions', body)
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
    <h1>推荐问题</h1>
    <div><el-button type="primary" @click="openCreate">新增</el-button></div>
    <el-alert v-if="actionError && !dialog" :title="actionError" type="error" show-icon :closable="false" />
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="text" label="文案" min-width="240" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ when(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editingId ? '编辑推荐' : '新增推荐'" width="480px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="72px">
        <el-form-item label="文案"><el-input v-model="form.text" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="生效" value="active" />
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
