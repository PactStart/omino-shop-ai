<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { label, when } from '../support/format'
import { useRows } from '../support/useRows'

const list = useRows()
const actionError = ref('')
const editing = ref<Record<string, unknown> | null>(null)
const creating = ref(false)
const form = reactive({ title: '', body: '', status: 'published' })

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/kb/articles')).data)
}

function openCreate(): void {
  form.title = ''
  form.body = ''
  form.status = 'published'
  editing.value = null
  creating.value = true
  actionError.value = ''
}

function openEdit(row: Record<string, unknown>): void {
  editing.value = row
  form.title = String(row.title ?? '')
  form.body = String(row.body ?? '')
  form.status = String(row.status ?? 'published')
  creating.value = false
  actionError.value = ''
}

async function save(): Promise<void> {
  actionError.value = ''
  const title = form.title.trim()
  const body = form.body.trim()
  if (!title || !body) {
    actionError.value = '请填写标题和正文'
    return
  }
  if (form.status !== 'published' && form.status !== 'retired') {
    actionError.value = '状态只能是已发布或已下架'
    return
  }
  try {
    if (editing.value?.id) {
      await apiPatch(`/v1/kb/articles/${String(editing.value.id)}`, { title, body, status: form.status })
    } else {
      await apiPost('/v1/kb/articles', { title, body })
    }
    creating.value = false
    editing.value = null
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

const dialog = ref(false)
function showCreate(): void {
  openCreate()
  dialog.value = true
}
function showEdit(row: Record<string, unknown>): void {
  openEdit(row)
  dialog.value = true
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>知识库</h1>
    <div><el-button type="primary" @click="showCreate">新建文章</el-button></div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ when(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editing ? '编辑文章' : '新建文章'" width="640px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="72px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.body" type="textarea" :rows="8" /></el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-select v-model="form.status">
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
