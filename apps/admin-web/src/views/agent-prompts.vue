<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, errorMessage } from '../api/http'
import { when } from '../support/format'

const error = ref('')
const actionError = ref('')
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const currentKey = ref('')
const content = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const body = await apiGet<unknown>('/v1/agent-prompts')
    rows.value = Array.isArray(body.data) ? (body.data as Record<string, unknown>[]) : []
    const current = rows.value.find((row) => String(row.key) === currentKey.value) ?? rows.value[0]
    if (current) select(current)
  } catch (reason) {
    rows.value = []
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function select(row: Record<string, unknown>): void {
  currentKey.value = String(row.key ?? '')
  content.value = String(row.content ?? '')
  actionError.value = ''
}

async function save(): Promise<void> {
  actionError.value = ''
  const next = content.value.trim()
  if (!currentKey.value || !next) {
    actionError.value = '正文不能为空'
    return
  }
  try {
    await apiPatch(`/v1/agent-prompts/${encodeURIComponent(currentKey.value)}`, { content: next })
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
    <h1>提示词</h1>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="split">
      <el-table v-loading="loading" :data="rows" highlight-current-row @row-click="select">
        <el-table-column prop="key" label="键" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="updatedBy" label="修改人" min-width="120" />
        <el-table-column label="更新时间" min-width="160">
          <template #default="{ row }">{{ when(row.updatedAt) }}</template>
        </el-table-column>
      </el-table>
      <section v-if="currentKey">
        <div class="key">{{ currentKey }}</div>
        <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
        <el-input v-model="content" type="textarea" :rows="16" />
        <el-button type="primary" class="save" @click="save">保存</el-button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
}
.key {
  margin-bottom: 8px;
  font-weight: 600;
}
.save {
  margin-top: 8px;
}
</style>
